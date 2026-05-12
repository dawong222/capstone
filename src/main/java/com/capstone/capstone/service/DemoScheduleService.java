package com.capstone.capstone.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 데모 전용 스케줄링 서비스.
 *
 * simulate/telemetry 토픽으로 수신된 완성된 AI 요청(sample_ai_server_request.json 형식)을
 * DB 저장 없이 AI 서버로 그대로 전달한다.
 *
 * 일반 모드(HourlyDataService.sendDailyAiRequest)와 완전히 분리된 경로이며,
 * AI 서버 응답은 공통 콜백(/ai/result → simulate/action)으로 처리된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoScheduleService {

    private final AiService aiService;
    private final ObjectMapper objectMapper;

    /**
     * simulate/telemetry 수신 시 호출.
     * payload는 이미 완성된 AI 스케줄 요청(sample_ai_server_request.json 형식)이므로
     * 그대로 AI 서버로 전달한다. DB 저장 없음.
     */
    public void handleSimulateTelemetry(String rawPayload) {
        try {
            Map<String, Object> request = objectMapper.readValue(rawPayload, new TypeReference<>() {});
            String requestId = String.valueOf(request.getOrDefault("request_id", "unknown"));
            log.info("[DEMO] simulate/telemetry 수신 → AI 서버 전송 request_id={}", requestId);
            aiService.sendRaw(request);
            log.info("[DEMO] AI 서버 전송 완료 request_id={}", requestId);
        } catch (Exception e) {
            log.error("[DEMO] AI 요청 전송 실패: {}", e.getMessage());
        }
    }
}
