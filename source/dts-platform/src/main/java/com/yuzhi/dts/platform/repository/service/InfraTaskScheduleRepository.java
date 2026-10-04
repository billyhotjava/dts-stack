package com.yuzhi.dts.platform.repository.service;

import com.yuzhi.dts.platform.domain.service.InfraTaskSchedule;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InfraTaskScheduleRepository extends JpaRepository<InfraTaskSchedule, UUID> {}

