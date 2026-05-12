package com.capstone.capstone.service;

import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.dto.mqtt.MqttStationDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataProcessingService {

    private final ObjectMapper objectMapper;
    private final TelemetryPersistenceService persistenceService;

    // 최신 MQTT 데이터 (스레드 안전)
    private final AtomicReference<MqttPayloadDto> latestData = new AtomicReference<>();

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

            return data;

        } catch (JsonProcessingException e) {
            log.error("[MQTT 파싱 실패] error={}", e.getMessage());
            throw new RuntimeException("JSON 파싱 실패", e);
        }
    }

    public MqttPayloadDto getLatestData() {
        return latestData.get();
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

    public void broadcastScheduleUpdate(String requestId, String targetDate) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                "requestId", requestId != null ? requestId : "",
                "targetDate", targetDate != null ? targetDate : "",
                "timestamp", LocalDateTime.now().toString()
            ));
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
