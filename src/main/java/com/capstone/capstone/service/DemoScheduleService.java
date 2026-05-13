package com.capstone.capstone.service;

import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 데모 전용 스케줄링 서비스.
 *
 * simulate/telemetry 토픽으로 수신된 텔레메트리 데이터(day_idx 포함)를 파싱하여
 * day_idx 변화가 감지될 때마다 AI 서버에 스케줄 요청을 전송한다.
 * DB 저장 없음 — 일반 모드(MqttSubscriber + DataProcessingService)와 완전히 분리된 경로.
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

    /**
     * simulate/telemetry 수신 시 호출.
     * 텔레메트리 데이터를 파싱해 live 뷰를 갱신하고,
     * day_idx가 바뀌면 새 스케줄을 AI 서버에 요청한다.
     */
    public void handleSimulateTelemetry(String rawPayload) {
        try {
            MqttPayloadDto data = objectMapper.readValue(rawPayload, MqttPayloadDto.class);

            if (data.getStations() == null || data.getStations().isEmpty()) {
                log.warn("[DEMO] stations 없음 - 스킵");
                return;
            }

            // live 텔레메트리 갱신 (SSE broadcast, DB 저장 없음)
            dataProcessingService.updateLiveData(data);

            int currentDayIdx = data.getStations().get(0).getHeader().getDayIdx();
            int prevDayIdx = lastDayIdx.getAndSet(currentDayIdx);

            if (prevDayIdx == -1) {
                log.info("[DEMO] 첫 수신 day_idx={}", currentDayIdx);
                return;
            }

            if (currentDayIdx != prevDayIdx) {
                log.info("[DEMO] 시뮬레이션 날짜 변경 감지: day_idx {} → {} → AI 요청 전송", prevDayIdx, currentDayIdx);
                triggerAiSchedule();
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
