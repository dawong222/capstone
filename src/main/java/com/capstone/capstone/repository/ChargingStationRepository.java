package com.capstone.capstone.repository;

import com.capstone.capstone.entity.ChargingStation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingStationRepository
        extends JpaRepository<ChargingStation, Long> {
}