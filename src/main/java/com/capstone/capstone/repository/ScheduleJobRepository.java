package com.capstone.capstone.repository;

import com.capstone.capstone.entity.ScheduleJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface ScheduleJobRepository extends JpaRepository<ScheduleJob, Long> {

    Optional<ScheduleJob> findByScheduleTargetDate(LocalDate date);

}