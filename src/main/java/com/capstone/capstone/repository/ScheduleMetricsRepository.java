package com.capstone.capstone.repository;

import com.capstone.capstone.entity.ScheduleMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScheduleMetricsRepository extends JpaRepository<ScheduleMetrics, Long> {
}
