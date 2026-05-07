package com.capstone.capstone.repository;

import com.capstone.capstone.entity.ChargingStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, Long> {

    Optional<ChargingStation> findByStationIndex(Integer stationIndex);
}
