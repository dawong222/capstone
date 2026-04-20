package com.capstone.capstone.repository;

import com.capstone.capstone.entity.ChargerState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChargerStateRepository extends JpaRepository<ChargerState, Long> {
}