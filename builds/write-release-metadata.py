#!/usr/bin/env python3
"""Create verifiable metadata from the actual offline package, without Docker access."""

import argparse
import hashlib
import json
import tarfile
from datetime import datetime, timezone
from pathlib import Path


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def image_records(path):
    with tarfile.open(path, "r:*") as archive:
        manifest = json.load(archive.extractfile("manifest.json"))
        records = []
        for item in manifest:
            raw_config = archive.extractfile(item["Config"]).read()
            config = json.loads(raw_config)
            records.append({
                "imageId": "sha256:" + hashlib.sha256(raw_config).hexdigest(),
                "tags": item.get("RepoTags") or [],
                "architecture": config.get("architecture"),
                "os": config.get("os"),
                "sourceRevision": (config.get("config", {}).get("Labels") or {}).get("org.opencontainers.image.revision"),
            })
        if not records:
            raise ValueError(f"Image archive is empty: {path.name}")
        return records


def write_metadata(root, metadata_dir, revision):
    metadata = root / metadata_dir
    metadata.mkdir(parents=True, exist_ok=True)
    images = []
    checksums = []
    for path in sorted((root / "images").glob("*.tar")):
        digest = sha256(path)
        images.append({"archive": path.name, "sha256": digest, "images": image_records(path)})
        checksums.append(f"{digest}  {path.name}\n")
    manifest = {
        "formatVersion": 1,
        "version": revision[:12],
        "sourceCommit": revision,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "images": images,
        "imagesIncluded": bool(images),
    }
    (metadata / "release-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    checksum_file = metadata / "checksums.txt"
    if checksums:
        checksum_file.write_text("".join(checksums))
    else:
        # --no-images is explicit; the lite upgrader treats an absent file as preloaded.
        checksum_file.unlink(missing_ok=True)
    file_checksums = []
    for directory in (root / "dts-stack", metadata):
        for path in sorted(directory.rglob("*")):
            if path.is_file() and path.name != "files-checksums.txt":
                file_checksums.append(f"{sha256(path)}  {path.relative_to(root).as_posix()}\n")
    (metadata / "files-checksums.txt").write_text("".join(file_checksums))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--metadata-dir", choices=("extra", "misc"), required=True)
    parser.add_argument("--revision", required=True)
    args = parser.parse_args()
    write_metadata(args.root, args.metadata_dir, args.revision)
