package com.capstone.capstone.repository;

import com.capstone.capstone.entity.HourlySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HourlySnapshotRepository extends JpaRepository<HourlySnapshot, Long> {

    Optional<HourlySnapshot> findFirstByOrderByRecordedAtAsc();

    // 최근 7일 내에서 하루치 데이터가 minRowsPerDay 이상인 날의 수 (완전한 날 카운트)
    @Query(value = "SELECT COUNT(*) FROM (SELECT DATE(recorded_at) FROM hourly_snapshot WHERE recorded_at >= :sevenDaysAgo GROUP BY DATE(recorded_at) HAVING COUNT(*) >= :minRowsPerDay) t", nativeQuery = true)
    long countCompleteDays(@Param("minRowsPerDay") long minRowsPerDay, @Param("sevenDaysAgo") LocalDateTime sevenDaysAgo);

    long countByRecordedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(h) FROM HourlySnapshot h WHERE h.recordedAt >= :start AND h.recordedAt < :end AND h.station.stationIndex = 0")
    long countHoursByDate(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<HourlySnapshot> findByRecordedAtBetweenOrderByRecordedAt(LocalDateTime start, LocalDateTime end);

    List<HourlySnapshot> findByRecordedAtBetweenAndStationStationIndexOrderByRecordedAt(
            LocalDateTime start, LocalDateTime end, Integer stationIndex);

    // 7일 초과분 삭제용
    void deleteByRecordedAtBefore(LocalDateTime cutoff);
}
