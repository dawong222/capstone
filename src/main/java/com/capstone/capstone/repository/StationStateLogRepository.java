package com.capstone.capstone.repository;

import com.capstone.capstone.entity.StationStateLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StationStateLogRepository extends JpaRepository<StationStateLog, Long> {
}