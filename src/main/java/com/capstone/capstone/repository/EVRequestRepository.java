package com.capstone.capstone.repository;

import com.capstone.capstone.entity.EVRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EVRequestRepository
        extends JpaRepository<EVRequest, Long> {

    List<EVRequest> findByStationId(Long stationId);

}