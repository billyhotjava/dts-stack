package com.yuzhi.dts.metrics.domain.repository;

import com.yuzhi.dts.metrics.domain.GraphDraft;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link GraphDraft}. Narrow surface (findById/save inherited) so test fakes are trivial.
 * Consumed by {@code MetricGraphDraftService} in T03.
 */
public interface GraphDraftRepository extends JpaRepository<GraphDraft, String> {}
