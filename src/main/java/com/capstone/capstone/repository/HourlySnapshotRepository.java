package com.capstone.capstone.repository;

import com.capstone.capstone.entity.HourlySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface HourlySnapshotRepository extends JpaRepository<HourlySnapshot, Long> {

    long countByRecordedAtBetween(LocalDateTime start, LocalDateTime end);

    // 오늘 저장된 고유 시간(hour) 수 - 스테이션 수로 나눠서 사용
    @Query("SELECT COUNT(h) FROM HourlySnapshot h WHERE h.recordedAt >= :start AND h.recordedAt < :end AND h.stationId = 0")
    long countHoursByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
