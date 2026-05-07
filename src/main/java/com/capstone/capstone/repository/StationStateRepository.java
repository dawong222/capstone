package com.capstone.capstone.repository;

import com.capstone.capstone.entity.StationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StationStateRepository extends JpaRepository<StationState, Long> {

    Optional<StationState> findByStationId(Long stationId);
}
