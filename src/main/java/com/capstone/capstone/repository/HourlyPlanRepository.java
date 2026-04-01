package com.capstone.capstone.repository;

import com.capstone.capstone.entity.HourlyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HourlyPlanRepository extends JpaRepository<HourlyPlan, Long> {

    List<HourlyPlan> findByScheduleResultId(Long scheduleResultId);

}