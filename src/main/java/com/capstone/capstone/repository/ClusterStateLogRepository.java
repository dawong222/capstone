package com.capstone.capstone.repository;

import com.capstone.capstone.entity.ClusterStateLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClusterStateLogRepository extends JpaRepository<ClusterStateLog, Long> {
}