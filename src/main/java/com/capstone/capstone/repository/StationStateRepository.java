package com.capstone.capstone.repository;

import com.capstone.capstone.entity.StationState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StationStateRepository extends JpaRepository<StationState, Long> {
}