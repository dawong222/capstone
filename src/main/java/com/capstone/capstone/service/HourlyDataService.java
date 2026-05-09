package com.capstone.capstone.service;

import com.capstone.capstone.dto.mqtt.MqttChargerStatusDto;
import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.dto.mqtt.MqttStationDto;
import com.capstone.capstone.entity.ChargingStation;
import com.capstone.capstone.entity.HourlySnapshot;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.HourlySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class HourlyDataService {

    private final HourlySnapshotRepository snapshotRepository;
    private final ChargingStationRepository stationRepository;
    private final DataProcessingService dataProcessingService;

    @Scheduled(cron = "0 0 * * * *")
    public void saveHourlySnapshot() {
        MqttPayloadDto latest = dataProcessingService.getLatestData();

        if (latest == null || latest.getStations() == null) {
            log.warn("[정각 저장] MQTT 데이터 없음 - 스킵");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int stationCount = latest.getStations().size();

        for (MqttStationDto mqttStation : latest.getStations()) {
            int stationIndex = mqttStation.getHeader().getStationId();
            ChargingStation dbStation = stationRepository.findByStationIndex(stationIndex)
                    .orElse(null);
            if (dbStation == null) {
                log.warn("[정각 저장] stationIndex={} 에 해당하는 스테이션 없음 - 스킵", stationIndex);
                continue;
            }

            HourlySnapshot snapshot = new HourlySnapshot();
            snapshot.setStation(dbStation);
            snapshot.setRecordedAt(now);
            snapshot.setSoc(mqttStation.getPayload().getStateOfCharge().getSoc());
            snapshot.setCapacityWh(mqttStation.getPayload().getStateOfCharge().getCapacityWh());
            snapshot.setDemandCount((int) mqttStation.getPayload().getChargerStatus().stream()
                    .filter(MqttChargerStatusDto::isHasDemand).count());
            snapshot.setPPv(mqttStation.getPayload().getPowerMetricsW().getPPv());
            snapshot.setPLoad(mqttStation.getPayload().getPowerMetricsW().getPLoad());
            snapshot.setPEss(mqttStation.getPayload().getPowerMetricsW().getPEss());
            snapshot.setPGrid(mqttStation.getPayload().getPowerMetricsW().getPGrid());
            snapshot.setPTr(mqttStation.getPayload().getPowerMetricsW().getPTr());
            snapshotRepository.save(snapshot);
        }

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        long totalRows = snapshotRepository.countByRecordedAtBetween(startOfDay, now.plusSeconds(1));
        long hoursCollected = stationCount > 0 ? totalRows / stationCount : 0;
        log.info("[정각 저장 완료] {}시, 오늘 누적 {}시간분", now.getHour(), hoursCollected);
    }

    @Transactional
    @Scheduled(cron = "0 10 22 * * *")
    public void cleanupOldSnapshots() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
        snapshotRepository.deleteByRecordedAtBefore(cutoff);
        log.info("[스냅샷 정리] {} 이전 데이터 삭제 완료", cutoff.toLocalDate());
    }
}
