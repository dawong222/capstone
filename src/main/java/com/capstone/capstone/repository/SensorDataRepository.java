package com.capstone.capstone.repository;

import com.capstone.capstone.entity.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SensorDataRepository
        extends JpaRepository<SensorData, Long> {
    List<SensorData> findByStationId(Long stationId);
}
