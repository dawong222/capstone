package com.capstone.capstone.repository;

import com.capstone.capstone.entity.ChargerStateLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChargerStateLogRepository extends JpaRepository<ChargerStateLog, Long> {
}