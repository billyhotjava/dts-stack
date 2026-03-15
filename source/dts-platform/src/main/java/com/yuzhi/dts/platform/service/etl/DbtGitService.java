package com.yuzhi.dts.platform.service.etl;

import com.yuzhi.dts.platform.config.DbtProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DbtGitService {

    private static final Logger LOG = LoggerFactory.getLogger(DbtGitService.class);

    private static final String DEFAULT_GITIGNORE =
        """
        target/
        logs/
        dbt_packages/
        __pycache__/
        .venv/
        """;

    private final DbtProperties properties;
    private final DbtConfigService configService;

    public DbtGitService(DbtProperties properties, DbtConfigService configService) {
        this.properties = properties;
        this.configService = configService;
    }

    // ── Public API ───────────────────────────────────────────────

    /**
     * Ensure the dbt project directory is a Git repository.
     * If .git does not exist, initializes one and creates a .gitignore.
     */
    public void ensureGitInit() {
        Path projectDir = resolveProjectDir();
        Path gitDir = projectDir.resolve(".git");
        if (Files.isDirectory(gitDir)) {
            return;
        }
        try {
            Files.createDirectories(projectDir);
            Git.init().setDirectory(projectDir.toFile()).call().close();
            LOG.info("[dbt-git] Initialized new Git repository at {}", projectDir);

            // Create .gitignore if it doesn't exist
            Path gitignore = projectDir.resolve(".gitignore");
            if (!Files.exists(gitignore)) {
                Files.writeString(gitignore, DEFAULT_GITIGNORE, StandardCharsets.UTF_8);
                LOG.info("[dbt-git] Created .gitignore");
            }
        } catch (IOException | GitAPIException ex) {
            throw new IllegalStateException("Failed to initialize Git repository: " + ex.getMessage(), ex);
        }
    }

    /**
     * Return the current status of the working tree.
     */
    public GitStatusResult getStatus() {
        ensureGitInit();
        try (Git git = openGit()) {
            org.eclipse.jgit.api.Status status = git.status().call();

            List<String> modified = sorted(status.getModified());
            List<String> added = sorted(status.getAdded());
            List<String> deleted = sorted(status.getMissing());
            // Combine removed (staged deletes) into deleted as well
            List<String> allDeleted = new ArrayList<>(deleted);
            allDeleted.addAll(sorted(status.getRemoved()));
            List<String> untracked = sorted(status.getUntracked());
            boolean clean = status.isClean();

            return new GitStatusResult(modified, added, allDeleted, untracked, clean);
        } catch (GitAPIException | IOException ex) {
            throw new IllegalStateException("Failed to get Git status: " + ex.getMessage(), ex);
        }
    }

    /**
     * Stage all changes and commit with the given message.
     */
    public GitCommitResult commit(String message, String authorName, String authorEmail) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("Commit message must not be empty");
        }
        ensureGitInit();
        try (Git git = openGit()) {
            // Stage all changes (new, modified, deleted)
            git.add().addFilepattern(".").call();
            // Stage deletions explicitly
            git.add().addFilepattern(".").setUpdate(true).call();

            PersonIdent author = new PersonIdent(
                StringUtils.hasText(authorName) ? authorName : "DTS Platform",
                StringUtils.hasText(authorEmail) ? authorEmail : "dts@localhost"
            );

            RevCommit commit = git.commit()
                .setMessage(message)
                .setAuthor(author)
                .setCommitter(author)
                .call();

            LOG.info("[dbt-git] Committed: {} ({})", commit.getId().abbreviate(7).name(), message);

            return new GitCommitResult(
                commit.getId().name(),
                message,
                Instant.ofEpochSecond(commit.getCommitTime())
            );
        } catch (GitAPIException | IOException ex) {
            throw new IllegalStateException("Failed to commit: " + ex.getMessage(), ex);
        }
    }

    /**
     * Return the most recent N commits.
     */
    public List<GitLogEntry> log(int limit) {
        if (limit <= 0) {
            limit = 20;
        }
        ensureGitInit();
        try (Git git = openGit()) {
            Iterable<RevCommit> commits;
            try {
                commits = git.log().setMaxCount(limit).call();
            } catch (org.eclipse.jgit.api.errors.NoHeadException ex) {
                // No commits yet
                return List.of();
            }

            List<GitLogEntry> entries = new ArrayList<>();
            for (RevCommit commit : commits) {
                PersonIdent authorIdent = commit.getAuthorIdent();
                entries.add(new GitLogEntry(
                    commit.getId().name(),
                    commit.getId().abbreviate(7).name(),
                    commit.getFullMessage(),
                    authorIdent.getName(),
                    authorIdent.getWhenAsInstant()
                ));
            }
            return entries;
        } catch (GitAPIException | IOException ex) {
            throw new IllegalStateException("Failed to read Git log: " + ex.getMessage(), ex);
        }
    }

    /**
     * Get the diff for a specific file (working tree vs HEAD).
     */
    public String diff(String relativePath) {
        validateRelativePath(relativePath);
        ensureGitInit();
        try (Git git = openGit()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setRepository(git.getRepository());
                formatter.setPathFilter(PathFilter.create(normalizePathSeparators(relativePath)));

                AbstractTreeIterator headTree = resolveHeadTree(git.getRepository());
                List<DiffEntry> diffs = formatter.scan(headTree, new org.eclipse.jgit.treewalk.FileTreeIterator(git.getRepository()));
                formatter.format(diffs);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException | GitAPIException ex) {
            throw new IllegalStateException("Failed to compute diff: " + ex.getMessage(), ex);
        }
    }

    /**
     * Get diff of all uncommitted changes.
     */
    public String diffAll() {
        ensureGitInit();
        try (Git git = openGit()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter formatter = new DiffFormatter(out)) {
                formatter.setRepository(git.getRepository());

                AbstractTreeIterator headTree = resolveHeadTree(git.getRepository());
                List<DiffEntry> diffs = formatter.scan(headTree, new org.eclipse.jgit.treewalk.FileTreeIterator(git.getRepository()));
                formatter.format(diffs);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException | GitAPIException ex) {
            throw new IllegalStateException("Failed to compute diff: " + ex.getMessage(), ex);
        }
    }

    /**
     * Revert a specific file to its state at HEAD.
     */
    public void revertFile(String relativePath) {
        validateRelativePath(relativePath);
        ensureGitInit();
        try (Git git = openGit()) {
            git.checkout()
                .addPath(normalizePathSeparators(relativePath))
                .call();
            LOG.info("[dbt-git] Reverted file to HEAD: {}", relativePath);
        } catch (GitAPIException | IOException ex) {
            throw new IllegalStateException("Failed to revert file: " + ex.getMessage(), ex);
        }
    }

    /**
     * Get the content of a file at a specific commit.
     */
    public String getFileAtCommit(String relativePath, String commitHash) {
        validateRelativePath(relativePath);
        if (!StringUtils.hasText(commitHash)) {
            throw new IllegalArgumentException("Commit hash must not be empty");
        }
        ensureGitInit();
        try (Git git = openGit()) {
            Repository repository = git.getRepository();
            ObjectId commitId = repository.resolve(commitHash);
            if (commitId == null) {
                throw new IllegalArgumentException("Commit not found: " + commitHash);
            }

            try (org.eclipse.jgit.revwalk.RevWalk revWalk = new org.eclipse.jgit.revwalk.RevWalk(repository)) {
                RevCommit commit = revWalk.parseCommit(commitId);
                try (TreeWalk treeWalk = TreeWalk.forPath(
                        repository, normalizePathSeparators(relativePath), commit.getTree())) {
                    if (treeWalk == null) {
                        throw new IllegalArgumentException(
                            "File not found at commit " + commitHash + ": " + relativePath);
                    }
                    ObjectId blobId = treeWalk.getObjectId(0);
                    byte[] bytes = repository.open(blobId).getBytes();
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
        } catch (IOException | GitAPIException ex) {
            throw new IllegalStateException("Failed to read file at commit: " + ex.getMessage(), ex);
        }
    }

    // ── Internal Helpers ─────────────────────────────────────────

    private Path resolveProjectDir() {
        String dir = properties.getProjectDir();
        DbtConfigService.DbtConfigView view = configService.loadConfig();
        if (view != null && view.config() != null && StringUtils.hasText(view.config().projectDir())) {
            dir = view.config().projectDir();
        }
        if (!StringUtils.hasText(dir)) {
            dir = "/opt/dts/dbt";
        }
        return Path.of(dir);
    }

    private Git openGit() throws IOException, GitAPIException {
        Path projectDir = resolveProjectDir();
        return Git.open(projectDir.toFile());
    }

    private void validateRelativePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("Path must not be empty");
        }
        if (relativePath.contains("..")) {
            throw new SecurityException("Invalid path: path traversal not allowed");
        }
        Path projectDir = resolveProjectDir();
        Path resolved = projectDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(projectDir)) {
            throw new SecurityException("Invalid path: outside project directory");
        }
    }

    private String normalizePathSeparators(String path) {
        return path.replace('\\', '/');
    }

    private AbstractTreeIterator resolveHeadTree(Repository repository) throws IOException {
        ObjectId head = repository.resolve("HEAD^{tree}");
        if (head == null) {
            // No commits yet – compare against empty tree
            return new EmptyTreeIterator();
        }
        CanonicalTreeParser treeParser = new CanonicalTreeParser();
        try (ObjectReader reader = repository.newObjectReader()) {
            treeParser.reset(reader, head);
        }
        return treeParser;
    }

    private List<String> sorted(Set<String> set) {
        return new ArrayList<>(new TreeSet<>(set));
    }

    // ── DTOs ─────────────────────────────────────────────────────

    public record GitStatusResult(
        List<String> modified,
        List<String> added,
        List<String> deleted,
        List<String> untracked,
        boolean clean
    ) {}

    public record GitCommitResult(String commitHash, String message, Instant timestamp) {}

    public record GitLogEntry(String hash, String shortHash, String message, String author, Instant timestamp) {}

    // ── Request DTOs ─────────────────────────────────────────────

    public record GitCommitRequest(String message, String authorName, String authorEmail) {}

    public record GitRevertRequest(String path) {}
}
