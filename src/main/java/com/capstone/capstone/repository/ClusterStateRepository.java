package com.capstone.capstone.repository;

import com.capstone.capstone.entity.ClusterState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClusterStateRepository extends JpaRepository<ClusterState, Long> {
}