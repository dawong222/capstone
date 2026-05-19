package com.capstone.capstone.controller;

import com.capstone.capstone.dto.DailyStatsDto;
import com.capstone.capstone.dto.HourlySolarDto;
import com.capstone.capstone.entity.HourlySnapshot;
import com.capstone.capstone.repository.HourlySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final HourlySnapshotRepository hourlySnapshotRepository;

    @GetMapping("/daily")
    public ResponseEntity<List<DailyStatsDto>> getDailyStats(
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) Integer stationId) {

        LocalDate today = LocalDate.now();
        LocalDateTime from = today.minusDays(days - 1).atStartOfDay();
        LocalDateTime to = today.plusDays(1).atStartOfDay();

        List<HourlySnapshot> snapshots = (stationId != null)
                ? hourlySnapshotRepository.findByRecordedAtBetweenAndStationStationIndexOrderByRecordedAt(from, to, stationId)
                : hourlySnapshotRepository.findByRecordedAtBetweenOrderByRecordedAt(from, to);

        Map<LocalDate, List<HourlySnapshot>> byDay = snapshots.stream()
                .collect(Collectors.groupingBy(s -> s.getRecordedAt().toLocalDate()));

        List<DailyStatsDto> result = new ArrayList<>();
        for (LocalDate date = today.minusDays(days - 1); !date.isAfter(today); date = date.plusDays(1)) {
            List<HourlySnapshot> daySnaps = byDay.getOrDefault(date, List.of());

            DailyStatsDto dto = new DailyStatsDto();
            dto.setDate(date.toString());
            dto.setSolar(daySnaps.stream().mapToDouble(s -> s.getPPv() / 1000.0).sum());
            dto.setConsumption(daySnaps.stream().mapToDouble(s -> s.getPLoad() / 1000.0).sum());
            dto.setGrid(daySnaps.stream().mapToDouble(s -> s.getPGrid() / 1000.0).sum());
            dto.setAvgSoc(daySnaps.stream().mapToDouble(s -> s.getSoc() * 100).average().orElse(0));
            dto.setPeakDemand(daySnaps.stream().mapToInt(HourlySnapshot::getDemandCount).max().orElse(0));
            result.add(dto);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/solar/today")
    public ResponseEntity<List<HourlySolarDto>> getTodaySolarActual() {
        LocalDateTime from = LocalDate.now().atStartOfDay();
        LocalDateTime to   = LocalDateTime.now();

        List<HourlySnapshot> snapshots =
                hourlySnapshotRepository.findByRecordedAtBetweenOrderByRecordedAt(from, to);

        Map<Integer, Double> byHour = snapshots.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getRecordedAt().getHour(),
                        Collectors.summingDouble(s -> s.getPPv() / 1000.0)
                ));

        if (byHour.isEmpty()) return ResponseEntity.noContent().build();

        List<HourlySolarDto> result = byHour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new HourlySolarDto(e.getKey(), Math.round(e.getValue() * 10.0) / 10.0))
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
