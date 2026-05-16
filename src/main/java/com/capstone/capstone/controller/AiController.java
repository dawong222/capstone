package com.capstone.capstone.controller;

import com.capstone.capstone.dto.AiResponseDto;
import com.capstone.capstone.dto.ScheduleResponseDto;
import com.capstone.capstone.service.AiService;
import com.capstone.capstone.service.DataProcessingService;
import com.capstone.capstone.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

import java.time.LocalDate;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final SchedulingService schedulingService;
    private final AiService aiService;
    private final DataProcessingService dataProcessingService;

    @PostMapping("/result")
    public ResponseEntity<Void> receiveAiResult(@RequestBody AiResponseDto dto) {
        // MQTT(IoT) 발행 — DB 실패해도 반드시 실행 (saveAiResult 내부에서 try-catch)
        schedulingService.saveAiResult(dto);

        // SSE(프론트) 브로드캐스트 — MQTT와 독립적으로 항상 시도
        try {
            ScheduleResponseDto schedule = schedulingService.convertToScheduleResponse(dto);
            dataProcessingService.broadcastScheduleUpdate(
                dto.getRequestId(),
                LocalDate.now().plusDays(1).toString(),
                schedule
            );
        } catch (Exception e) {
            log.error("[SSE 브로드캐스트 실패] {}", e.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    /** 프론트엔드 트리거: 요청 빌드 후 AI 서버 전송, 결과는 콜백(/ai/result)으로 수신 */
    @PostMapping("/request")
    public ResponseEntity<Void> requestAiScheduling() {
        Map<String, Object> payload = schedulingService.buildRawAiRequest();
        aiService.sendRaw(payload);
        return ResponseEntity.accepted().build();
    }

    // ─── v2: raw 데이터 그대로 AI 서버 전송 ──────────────────────

    /** 전송할 JSON 미리보기 (AI 서버 미전송) */
    @GetMapping("/v2/preview")
    public ResponseEntity<Map<String, Object>> previewRawRequest() {
        return ResponseEntity.ok(schedulingService.buildRawAiRequest());
    }

    /** Raw 데이터를 AI 서버에 전송, 결과는 콜백(/ai/result)으로 수신 */
    @PostMapping("/v2/send")
    public ResponseEntity<Void> sendRawToAi() {
        Map<String, Object> payload = schedulingService.buildRawAiRequest();
        aiService.sendRaw(payload);
        return ResponseEntity.accepted().build();
    }

    /** 샘플/커스텀 payload를 AI 서버에 직접 전송, 결과는 콜백(/ai/result)으로 수신 */
    @PostMapping("/v2/send-sample")
    public ResponseEntity<String> sendSampleToAi(@RequestBody Map<String, Object> payload) {
        try {
            aiService.sendRaw(payload);
            return ResponseEntity.accepted().build();
        } catch (HttpClientErrorException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        }
    }
}