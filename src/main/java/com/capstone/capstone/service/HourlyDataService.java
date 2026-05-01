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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final HourlySnapshotRepository snapshotRepository;
    private final DataProcessingService dataProcessingService;
    private final AiService aiService;
    private final SchedulingService2 schedulingService;

    @Scheduled(cron = "0 0 * * * *") // 매 정각
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

        // 오늘 저장된 시간 수 = 전체 row / 스테이션 수
        LocalDateTime startOfDay = now.toLocalDate().atStartOfDay();
        long totalRows = snapshotRepository.countByRecordedAtBetween(startOfDay, now.plusSeconds(1));
        long hoursCollected = stationCount > 0 ? totalRows / stationCount : 0;

        log.info("[정각 저장 완료] {}시, 오늘 누적 {}시간분", now.getHour(), hoursCollected);

        if (hoursCollected >= 24) {
            log.info("[AI 전송] 24시간 데이터 수집 완료 → AI 서버 전송");
            triggerAi(latest);
        }
    }

    private void triggerAi(MqttPayloadDto latest) {
        try {
            AiRequestDto request = buildAiRequest(latest);
            AiResponseDto response = aiService.requestSchedule(request);
            if (response != null) {
                schedulingService.saveAiResult(response);
                log.info("[AI 결과 저장 완료] requestId={}", response.getRequestId());
            }
        } catch (Exception e) {
            log.error("[AI 전송 실패] {}", e.getMessage());
        }
    }

    private AiRequestDto buildAiRequest(MqttPayloadDto latest) {
        AiRequestDto request = new AiRequestDto();
        request.setRequestId("rl-" + Instant.now().toEpochMilli());
        request.setRequestTimestamp(Instant.now().toString());
        request.setScheduleTargetDate(LocalDate.now().plusDays(1).toString());
        request.setScheduleHorizonHours("24");
        request.setClusterState(buildClusterState());
        request.setStations(buildStations(latest));
        return request;
    }

    private ClusterStateDto buildClusterState() {
        ClusterStateDto dto = new ClusterStateDto();
        dto.setTimeIndex(LocalDateTime.now().getHour());
        dto.setDayOfWeek(LocalDate.now().getDayOfWeek().getValue());
        dto.setTouPrice(174.0);
        dto.setGridLimit(200.0);
        dto.setTransferEnabled(true);
        return dto;
    }

    private List<StationDto> buildStations(MqttPayloadDto latest) {
        return latest.getStations().stream()
                .map(this::toStationDto)
                .toList();
    }

    private StationDto toStationDto(MqttStationDto s) {
        StationDto dto = new StationDto();
        dto.setStationId((long) s.getHeader().getStationId());

        CurrentStateDto state = new CurrentStateDto();
        state.setSoc(s.getPayload().getStateOfCharge().getSoc());
        state.setDemandCount((int) s.getPayload().getChargerStatus().stream()
                .filter(MqttChargerStatusDto::isHasDemand).count());
        state.setChargers(s.getPayload().getChargerStatus().stream()
                .map(c -> {
                    ChargerDto cd = new ChargerDto();
                    cd.setChargerId((long) c.getChargerId());
                    cd.setActive(c.isHasDemand());
                    cd.setPowerDemand(c.isHasDemand() ? 7.0 : 0.0);
                    return cd;
                }).toList());

        PowerDto power = new PowerDto();
        power.setPPv(s.getPayload().getPowerMetricsW().getPPv());
        power.setPLoad(s.getPayload().getPowerMetricsW().getPLoad());
        power.setPEss(s.getPayload().getPowerMetricsW().getPEss());
        power.setPGrid(s.getPayload().getPowerMetricsW().getPGrid());
        power.setPTr(s.getPayload().getPowerMetricsW().getPTr());
        state.setPower(power);
        dto.setCurrentState(state);

        ConstraintsDto constraints = new ConstraintsDto();
        constraints.setSocMin(0.2);
        constraints.setSocMax(0.9);
        constraints.setEssMaxCharge(15.0);
        constraints.setEssMaxDischarge(15.0);
        dto.setConstraints(constraints);

        return dto;
    }
}
