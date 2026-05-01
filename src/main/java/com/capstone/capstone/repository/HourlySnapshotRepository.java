package com.capstone.capstone.repository;

import com.capstone.capstone.entity.HourlySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface HourlySnapshotRepository extends JpaRepository<HourlySnapshot, Long> {

    long countByRecordedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(h) FROM HourlySnapshot h WHERE h.recordedAt >= :start AND h.recordedAt < :end AND h.stationId = 0")
    long countHoursByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<HourlySnapshot> findByRecordedAtBetweenOrderByRecordedAt(LocalDateTime start, LocalDateTime end);
}
