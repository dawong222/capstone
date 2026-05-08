package com.capstone.capstone.controller;

import com.capstone.capstone.entity.ChargingStation;
import com.capstone.capstone.entity.HourlySnapshot;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.HourlySnapshotRepository;
import com.capstone.capstone.service.HourlyDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Slf4j
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class DemoController {

    private final HourlySnapshotRepository snapshotRepository;
    private final ChargingStationRepository stationRepository;
    private final HourlyDataService hourlyDataService;

    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> run() {
        try {
            LocalDateTime todayStart = LocalDate.now().atStartOfDay();
            long existing = snapshotRepository.countByRecordedAtBetween(todayStart, todayStart.plusDays(1));
            if (existing == 0) {
                seedSnapshots();
                log.info("[Demo] HourlySnapshot 시드 완료");
            } else {
                log.info("[Demo] 오늘 스냅샷 이미 존재 ({}건) - 시드 스킵", existing);
            }
            hourlyDataService.sendDailyAiRequest();
            log.info("[Demo] AI 요청 전송 완료");
            return ResponseEntity.ok(Map.of("status", "ok", "message", "데모 데이터 시드 완료 및 AI 요청 전송됨"));
        } catch (Exception e) {
            log.error("[Demo] 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    private void seedSnapshots() {
        List<ChargingStation> stations = stationRepository.findAll();
        Random rng = new Random(42);
        LocalDateTime base = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        List<HourlySnapshot> batch = new ArrayList<>();

        for (ChargingStation station : stations) {
            double soc = 0.50 + (rng.nextDouble() - 0.5) * 0.10;

            for (int hoursAgo = 7 * 24 - 1; hoursAgo >= 0; hoursAgo--) {
                LocalDateTime ts = base.minusHours(hoursAgo);
                int hour = ts.getHour();

                double pv   = computePv(hour, rng);
                double load = computeLoad(hour, rng);
                double ess  = computeEss(hour, pv, load, rng);
                double grid = Math.max(-30_000, Math.min(30_000, load - pv - ess));

                // SOC 연속 변동 (충전 시 증가, 방전 시 감소)
                soc += ess > 0 ? 0.005 : -0.004;
                soc = Math.max(0.20, Math.min(0.90, soc));

                HourlySnapshot snap = new HourlySnapshot();
                snap.setStation(station);
                snap.setRecordedAt(ts);
                snap.setSoc(soc);
                snap.setCapacityWh(100_000.0);
                snap.setDemandCount(rng.nextInt(6));
                snap.setPPv(pv);
                snap.setPLoad(load);
                snap.setPEss(ess);
                snap.setPGrid(grid);
                snap.setPTr(0.0);
                batch.add(snap);
            }
        }
        snapshotRepository.saveAll(batch);
    }

    /** W 단위 PV 발전량: 0~6시/18~23시 = 0W, 6~18시 = 사인 커브 10~80kW */
    private double computePv(int hour, Random rng) {
        if (hour < 6 || hour >= 18) return 0.0;
        double angle = Math.PI * (hour - 6) / 12.0;
        double base = 10_000 + 70_000 * Math.sin(angle);
        return base * (0.8 + rng.nextDouble() * 0.4);
    }

    /** W 단위 부하: 낮(9~20시) 높고 나머지 낮게, 20~150kW 범위 */
    private double computeLoad(int hour, Random rng) {
        double factor = (hour >= 9 && hour <= 20) ? 1.3 : 0.7;
        double val = 85_000 * factor * (0.7 + rng.nextDouble() * 0.6);
        return Math.max(20_000, Math.min(150_000, val));
    }

    /** W 단위 ESS: 양수=충전, 음수=방전, ±50kW 범위. 낮엔 PV 초과분 충전, 밤엔 방전 */
    private double computeEss(int hour, double pv, double load, Random rng) {
        if (hour >= 6 && hour < 18 && pv > load) {
            return Math.min(50_000, (pv - load) * (0.5 + rng.nextDouble() * 0.5));
        } else {
            return -Math.min(50_000, load * 0.25 * rng.nextDouble());
        }
    }
}
