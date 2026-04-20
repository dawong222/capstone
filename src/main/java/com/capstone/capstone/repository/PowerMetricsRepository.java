package com.capstone.capstone.repository;

import com.capstone.capstone.entity.PowerMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PowerMetricsRepository extends JpaRepository<PowerMetrics, Long> {
}