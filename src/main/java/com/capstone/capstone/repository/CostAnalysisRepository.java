package com.capstone.capstone.repository;

import com.capstone.capstone.entity.CostAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CostAnalysisRepository
        extends JpaRepository<CostAnalysis, Long> {

    List<CostAnalysis> findByStationId(Long stationId);

}