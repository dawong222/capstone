package com.capstone.capstone.repository;

import com.capstone.capstone.entity.PowerMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PowerMetricsRepository extends JpaRepository<PowerMetrics, Long> {

    Optional<PowerMetrics> findByStationStateId(Long stationStateId);
}
