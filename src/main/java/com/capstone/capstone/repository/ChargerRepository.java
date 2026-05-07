package com.capstone.capstone.repository;

import com.capstone.capstone.entity.Charger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargerRepository extends JpaRepository<Charger, Long> {

    Optional<Charger> findByStationIdAndChargerIndex(Long stationId, Integer chargerIndex);

    List<Charger> findByStationIdOrderByChargerIndex(Long stationId);
}
