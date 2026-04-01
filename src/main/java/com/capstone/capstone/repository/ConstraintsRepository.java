package com.capstone.capstone.repository;

import com.capstone.capstone.entity.Constraints;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConstraintsRepository extends JpaRepository<Constraints, Long> {
}