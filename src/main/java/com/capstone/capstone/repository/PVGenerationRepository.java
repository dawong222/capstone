package com.capstone.capstone.repository;

import com.capstone.capstone.entity.PVGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PVGenerationRepository
        extends JpaRepository<PVGeneration, Long> {
    List<PVGeneration> findByStationId(Long stationId);
}