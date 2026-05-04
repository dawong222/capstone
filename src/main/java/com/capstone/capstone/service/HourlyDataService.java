package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import com.capstone.capstone.dto.mqtt.MqttChargerStatusDto;
import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.dto.mqtt.MqttStationDto;
import com.capstone.capstone.entity.HourlySnapshot;
import com.capstone.capstone.repository.HourlySnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class HourlyDataService {

    private final HourlySnapshotRepository snapshotRepository;
    private final DataProcessingService dataProcessingService;
    private final AiService aiService;
    private final AiRequestBuilderService aiRequestBuilderService;
    private final ScheduleResultService scheduleResultService;

    @Scheduled(cron = "0 0 * * * *")
    public void saveHourlySnapshot() {
        MqttPayloadDto latest = dataProcessingService.getLatestData();

        if (latest == null || latest.getStations() == null) {
            log.warn("[정각 저장] MQTT 데이터 없음 - 스킵");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int stationCount = latest.getStations().size();

        for (MqttStationDto station : latest.getStations()) {
            HourlySnapshot snapshot = new HourlySnapshot();
            snapshot.setRecordedAt(now);
            snapshot.setStationId(station.getHeader().getStationId());
            snapshot.setSoc(station.getPayload().getStateOfCharge().getSoc());
            snapshot.setCapacityWh(station.getPayload().getStateOfCharge().getCapacityWh());
            snapshot.setDemandCount((int) station.getPayload().getChargerStatus().stream()
                    .filter(MqttChargerStatusDto::isHasDemand).count());
            snapshot.setPPv(station.getPayload().getPowerMetricsW().getPPv());
            snapshot.setPLoad(station.getPayload().getPowerMetricsW().getPLoad());
            snapshot.setPEss(station.getPayload().getPowerMetricsW().getPEss());
            snapshot.setPGrid(station.getPayload().getPowerMetricsW().getPGrid());
            snapshot.setPTr(station.getPayload().getPowerMetricsW().getPTr());
            snapshotRepository.save(snapshot);
        }

        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        long totalRows = snapshotRepository.countByRecordedAtBetween(startOfDay, now.plusSeconds(1));
        long hoursCollected = stationCount > 0 ? totalRows / stationCount : 0;

        log.info("[정각 저장 완료] {}시, 오늘 누적 {}시간분", now.getHour(), hoursCollected);

        if (hoursCollected >= 24) {
            log.info("[AI 전송] 24시간 데이터 수집 완료 → AI 서버 전송");
            triggerAi();
        }
    }

    @Scheduled(cron = "0 10 22 * * *")
    public void sendDailyAiRequest() {
        log.info("[22:10 AI 전송] 스케줄 시작");
        try {
            Map<String, Object> payload = aiRequestBuilderService.buildRawAiRequest();
            String response = aiService.sendRaw(payload);
            log.info("[22:10 AI 전송 완료] response={}", response);
        } catch (Exception e) {
            log.error("[22:10 AI 전송 실패] {}", e.getMessage());
        }
    }

    private void triggerAi() {
        try {
            AiRequestDto request = aiRequestBuilderService.buildAiRequest();
            AiResponseDto response = aiService.requestSchedule(request);
            if (response != null) {
                scheduleResultService.saveAiResult(response);
                log.info("[AI 결과 저장 완료] requestId={}", response.getRequestId());
            }
        } catch (Exception e) {
            log.error("[AI 전송 실패] {}", e.getMessage());
        }
    }
}
