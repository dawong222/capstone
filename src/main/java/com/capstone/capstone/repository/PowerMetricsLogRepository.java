package com.capstone.capstone.repository;

import com.capstone.capstone.entity.PowerMetricsLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PowerMetricsLogRepository extends JpaRepository<PowerMetricsLog, Long> {
}