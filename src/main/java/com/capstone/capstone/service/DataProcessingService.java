package com.capstone.capstone.service;

import com.capstone.capstone.dto.ScheduleResponseDto;
import com.capstone.capstone.dto.mqtt.MqttChargerStatusDto;
import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.dto.mqtt.MqttStationDto;
import com.capstone.capstone.entity.ChargingStation;
import com.capstone.capstone.entity.HourlySnapshot;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.HourlySnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataProcessingService {

    private final ObjectMapper objectMapper;
    private final TelemetryPersistenceService persistenceService;
    private final AiRequestBuilderService aiRequestBuilderService;
    private final AiService aiService;
    private final HourlySnapshotRepository snapshotRepository;
    private final ChargingStationRepository stationRepository;

    // 최신 MQTT 데이터 (스레드 안전)
    private final AtomicReference<MqttPayloadDto> latestData = new AtomicReference<>();
    private final AtomicReference<LocalDate> lastAiTriggerDate = new AtomicReference<>();

    // SSE 구독자 목록 (스레드 안전)
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public MqttPayloadDto process(String payload) {
        try {
            MqttPayloadDto data = objectMapper.readValue(payload, MqttPayloadDto.class);

            log.info("[MQTT 수신] 스테이션 수={}", data.getStations() != null ? data.getStations().size() : 0);

            if (data.getStations() != null) {
                for (MqttStationDto s : data.getStations()) {
                    log.info("  └ stationId={}, soc={}, pLoad={}, pPv={}, active={}",
                            s.getHeader().getStationId(),
                            s.getPayload().getStateOfCharge().getSoc(),
                            s.getPayload().getPowerMetricsW().getPLoad(),
                            s.getPayload().getPowerMetricsW().getPPv(),
                            s.getStatus().isActive());
                }
            }

            latestData.set(data);
            broadcast(data);
            persistenceService.persist(data);
            checkAndSaveSimulationSnapshot(data);
            checkAndTriggerAiRequest(data);

            return data;

        } catch (JsonProcessingException e) {
            log.error("[MQTT 파싱 실패] error={}", e.getMessage());
            throw new RuntimeException("JSON 파싱 실패", e);
        }
    }

    public MqttPayloadDto getLatestData() {
        return latestData.get();
    }

    // DB 저장 없이 최신 데이터만 갱신하고 SSE 브로드캐스트 (데모 전용)
    public void updateLiveData(MqttPayloadDto data) {
        latestData.set(data);
        broadcast(data);
    }

    public void addEmitter(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        // 연결 즉시 최신 데이터 전송
        MqttPayloadDto current = latestData.get();
        if (current != null) {
            sendToEmitter(emitter, current);
        }
    }

    private void broadcast(MqttPayloadDto data) {
        for (SseEmitter emitter : emitters) {
            sendToEmitter(emitter, data);
        }
    }

    private void sendToEmitter(SseEmitter emitter, MqttPayloadDto data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name("telemetry").data(json));
        } catch (IOException e) {
            emitters.remove(emitter);
        }
    }

    @Transactional
    public void checkAndSaveSimulationSnapshot(MqttPayloadDto data) {
        if (data.getStations() == null || data.getStations().isEmpty()) return;
        String timestamp = data.getStations().get(0).getHeader().getTimestamp();
        if (timestamp == null) return;
        try {
            ZonedDateTime kst = ZonedDateTime.parse(timestamp).withZoneSameInstant(ZoneId.of("Asia/Seoul"));
            if (kst.getMinute() != 0) return;

            LocalDateTime simHour = kst.toLocalDateTime().truncatedTo(ChronoUnit.HOURS);

            long existing = snapshotRepository.countByRecordedAtBetween(simHour, simHour.plusMinutes(59));
            if (existing > 0) {
                log.debug("[시뮬레이션 스냅샷] {} 이미 저장됨 - 스킵", simHour);
                return;
            }

            Map<Integer, ChargingStation> indexToStation = stationRepository.findAll().stream()
                    .filter(s -> s.getStationIndex() != null)
                    .collect(Collectors.toMap(ChargingStation::getStationIndex, s -> s));

            for (MqttStationDto mqttStation : data.getStations()) {
                ChargingStation station = indexToStation.get(mqttStation.getHeader().getStationId());
                if (station == null) continue;

                HourlySnapshot snap = new HourlySnapshot();
                snap.setStation(station);
                snap.setRecordedAt(simHour);
                snap.setSoc(mqttStation.getPayload().getStateOfCharge().getSoc());
                snap.setCapacityWh(mqttStation.getPayload().getStateOfCharge().getCapacityWh());
                snap.setDemandCount((int) mqttStation.getPayload().getChargerStatus().stream()
                        .filter(MqttChargerStatusDto::isHasDemand).count());
                snap.setPPv(mqttStation.getPayload().getPowerMetricsW().getPPv());
                snap.setPLoad(mqttStation.getPayload().getPowerMetricsW().getPLoad());
                snap.setPEss(mqttStation.getPayload().getPowerMetricsW().getPEss());
                snap.setPGrid(mqttStation.getPayload().getPowerMetricsW().getPGrid());
                snap.setPTr(mqttStation.getPayload().getPowerMetricsW().getPTr());
                snapshotRepository.save(snap);
            }
            log.info("[시뮬레이션 스냅샷 저장] KST {}", simHour);
        } catch (Exception e) {
            log.warn("[시뮬레이션 스냅샷] 타임스탬프 파싱 실패: {}", timestamp);
        }
    }

    private boolean hasSevenDaysOfSnapshots() {
        long stationCount = stationRepository.count();
        if (stationCount == 0) {
            log.warn("[22:10 AI 요청 스킵] 등록된 스테이션 없음");
            return false;
        }
        long minRowsPerDay = stationCount * 24;
        long completeDays = snapshotRepository.countCompleteDays(minRowsPerDay);
        if (completeDays < 7) {
            log.warn("[22:10 AI 요청 스킵] 완전한 7일치 스냅샷 부족 - 현재 {}일치 완료 ({}개 스테이션 × 24시간 기준)",
                    completeDays, stationCount);
            return false;
        }
        return true;
    }

    private void checkAndTriggerAiRequest(MqttPayloadDto data) {
        if (data.getStations() == null || data.getStations().isEmpty()) return;
        String timestamp = data.getStations().get(0).getHeader().getTimestamp();
        if (timestamp == null) return;
        try {
            ZonedDateTime kst = ZonedDateTime.parse(timestamp)
                    .withZoneSameInstant(ZoneId.of("Asia/Seoul"));
            if (kst.getHour() != 22 || kst.getMinute() != 10) return;

            LocalDate triggerDate = kst.toLocalDate();
            LocalDate prev = lastAiTriggerDate.get();
            if (prev != null && prev.equals(triggerDate)) return;
            if (!lastAiTriggerDate.compareAndSet(prev, triggerDate)) return;

            if (!hasSevenDaysOfSnapshots()) return;

            LocalDateTime simKstTime = kst.toLocalDateTime();
            log.info("[22:10 AI 요청] IoT 타임스탬프 기준 트리거 ({})", timestamp);
            new Thread(() -> {
                try {
                    Map<String, Object> payload = aiRequestBuilderService.buildRawAiRequest(simKstTime);
                    aiService.sendRaw(payload);
                    log.info("[22:10 AI 요청 완료]");
                } catch (Exception e) {
                    log.error("[22:10 AI 요청 실패] {}", e.getMessage());
                }
            }, "ai-request-trigger").start();
        } catch (Exception e) {
            log.warn("[22:10 AI 요청] 타임스탬프 파싱 실패: {}", timestamp);
        }
    }

    public void broadcastScheduleUpdate(String requestId, String targetDate, ScheduleResponseDto schedule,
            Double costReductionKrw, Double costReductionPct, Double gridOnlyCostKrw) {
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("requestId", requestId != null ? requestId : "");
            payload.put("targetDate", targetDate != null ? targetDate : "");
            payload.put("timestamp", LocalDateTime.now().toString());
            if (schedule != null) {
                payload.put("schedule", schedule);
            }
            if (costReductionKrw != null) payload.put("costReductionKrw", costReductionKrw);
            if (costReductionPct != null) payload.put("costReductionPct", costReductionPct);
            if (gridOnlyCostKrw != null)  payload.put("gridOnlyCostKrw",  gridOnlyCostKrw);
            String json = objectMapper.writeValueAsString(payload);
            for (SseEmitter emitter : new ArrayList<>(emitters)) {
                try {
                    emitter.send(SseEmitter.event().name("schedule").data(json));
                } catch (IOException e) {
                    emitters.remove(emitter);
                }
            }
            log.info("[SSE 스케줄 이벤트] requestId={}, 구독자={}", requestId, emitters.size());
        } catch (JsonProcessingException e) {
            log.error("[SSE 스케줄 브로드캐스트 실패] error={}", e.getMessage());
        }
    }
}
