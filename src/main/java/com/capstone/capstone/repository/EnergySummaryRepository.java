package com.capstone.capstone.repository;

import com.capstone.capstone.entity.EnergySummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnergySummaryRepository
        extends JpaRepository<EnergySummary, Long> {

    List<EnergySummary> findByStationId(Long stationId);

}