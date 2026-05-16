package com.capstone.capstone.repository;

import com.capstone.capstone.entity.StationDemandForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StationDemandForecastRepository extends JpaRepository<StationDemandForecast, Long> {
}
