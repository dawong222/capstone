package com.capstone.capstone.repository;

import com.capstone.capstone.entity.ClusterForecast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClusterForecastRepository extends JpaRepository<ClusterForecast, Long> {
}
