package com.yuzhi.dts.metrics.domain.repository;

import com.yuzhi.dts.metrics.domain.MetricModelState;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@link MetricModelState} keyed by the natural {@code model_id}.
 * Consumed by {@code MetricModelLifecycleService} in T04.
 */
public interface MetricModelStateRepository extends JpaRepository<MetricModelState, String> {}
