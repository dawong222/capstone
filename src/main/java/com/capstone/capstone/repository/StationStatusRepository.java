package com.capstone.capstone.repository;

import com.capstone.capstone.entity.StationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StationStatusRepository
        extends JpaRepository<StationStatus, Long> {

    Optional<StationStatus> findTopByStationIdOrderByUpdatedAtDesc(Long stationId);

}
