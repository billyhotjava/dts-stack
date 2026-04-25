package com.yuzhi.dts.platform.service.workbench;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sprint-17 hotfix — fallback query isolation.
 *
 * <p>The leader-overview parent service runs in a single
 * {@code @Transactional(readOnly = true)} boundary that issues five+
 * queries. If any one of them fails (e.g. missing
 * {@code bi_report_link.source} column when the Liquibase changeset has
 * not been applied), Postgres marks the whole transaction as aborted and
 * every subsequent query in that boundary returns
 * <em>"current transaction is aborted, commands ignored until end of
 * transaction block"</em> — including the unrelated catalog_dataset
 * lookups, which then 500s the entire "我的概览" page.
 *
 * <p>Wrapping the fallback in {@link Propagation#REQUIRES_NEW} runs it
 * in its own physical transaction. A failure here only rolls back this
 * sub-tx; the parent stays clean and the rest of the overview loads.
 */
@Service
public class TopReportsFallbackService {

    private final BiReportLinkRepository repo;

    public TopReportsFallbackService(BiReportLinkRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<BiReportLink> tryFetchFallback(String source, Pageable pageable) {
        return repo.findRecentBySourceForFallback(source, pageable);
    }
}
