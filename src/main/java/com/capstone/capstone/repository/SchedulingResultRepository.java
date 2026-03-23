package com.capstone.capstone.repository;

import com.capstone.capstone.entity.SchedulingResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchedulingResultRepository
        extends JpaRepository<SchedulingResult, Long> {

    List<SchedulingResult> findByStationId(Long stationId);

}