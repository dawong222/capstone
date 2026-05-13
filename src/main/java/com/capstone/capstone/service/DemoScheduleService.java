package com.capstone.capstone.service;

import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 데모 전용 스케줄링 서비스.
 *
 * simulate/telemetry 토픽은 두 가지 페이로드 형식을 수신한다:
 *   1) 완성된 AI 요청 형식 (request_id 포함) → AI 서버에 그대로 전달
 *   2) 텔레메트리 형식 (stations + day_idx 포함) → day_idx 변화 감지 후 AI 트리거
 * DB 저장 없음 — 일반 모드와 완전히 분리된 경로.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoScheduleService {

    private final AiService aiService;
    private final AiRequestBuilderService aiRequestBuilderService;
    private final DataProcessingService dataProcessingService;
    private final ObjectMapper objectMapper;

    private final AtomicInteger lastDayIdx = new AtomicInteger(-1);

    public void handleSimulateTelemetry(String rawPayload) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(rawPayload, new TypeReference<>() {});

            if (parsed.containsKey("request_id")) {
                // ── 형식 1: 완성된 AI 요청 → 그대로 AI 서버에 전달 (기존 동작 유지)
                String requestId = String.valueOf(parsed.get("request_id"));
                log.info("[DEMO] AI 요청 형식 수신 → AI 서버 전송 request_id={}", requestId);
                aiService.sendRaw(parsed);
                log.info("[DEMO] AI 서버 전송 완료 request_id={}", requestId);

            } else if (parsed.containsKey("stations")) {
                // ── 형식 2: 텔레메트리 형식 → day_idx 변화 감지 후 AI 트리거
                MqttPayloadDto data = objectMapper.convertValue(parsed, MqttPayloadDto.class);

                if (data.getStations() == null || data.getStations().isEmpty()) {
                    log.warn("[DEMO] stations 비어 있음 - 스킵");
                    return;
                }

                dataProcessingService.updateLiveData(data);

                int currentDayIdx = data.getStations().get(0).getHeader().getDayIdx();
                int prevDayIdx = lastDayIdx.getAndSet(currentDayIdx);

                if (prevDayIdx == -1) {
                    log.info("[DEMO] 첫 수신 day_idx={}", currentDayIdx);
                    return;
                }

                if (currentDayIdx != prevDayIdx) {
                    log.info("[DEMO] 날짜 변경 감지: day_idx {} → {} → AI 요청 전송", prevDayIdx, currentDayIdx);
                    triggerAiSchedule();
                }

            } else {
                log.warn("[DEMO] 알 수 없는 페이로드 형식 - 스킵 (keys={})", parsed.keySet());
            }

        } catch (Exception e) {
            log.error("[DEMO] 처리 실패: {}", e.getMessage());
        }
    }

    private void triggerAiSchedule() {
        try {
            Map<String, Object> request = aiRequestBuilderService.buildRawAiRequest();
            aiService.sendRaw(request);
            log.info("[DEMO] AI 서버 요청 전송 완료 - 콜백(/ai/result) 대기 중");
        } catch (Exception e) {
            log.error("[DEMO] AI 서버 요청 실패: {}", e.getMessage());
        }
    }
}
