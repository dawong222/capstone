package com.capstone.capstone.controller;

import com.capstone.capstone.dto.AiRequestDto;
import com.capstone.capstone.dto.AiResponseDto;
import com.capstone.capstone.dto.ai.ScheduleForecastRequestDto;
import com.capstone.capstone.service.AiService;
import com.capstone.capstone.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final SchedulingService schedulingService;
    private final AiService aiService;

    @PostMapping("/result")
    public ResponseEntity<Void> receiveAiResult(@RequestBody AiResponseDto dto) {

        schedulingService.saveAiResult(dto);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/request")
    public ResponseEntity<?> requestAiScheduling() {

        AiRequestDto request = schedulingService.buildAiRequest();

        AiResponseDto response = schedulingService.callAiServer(request);

        schedulingService.saveAiResult(response);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/request/mock")
    public ResponseEntity<?> requestAiSchedulingWithMockData() {
        AiRequestDto request = schedulingService.buildMockAiRequest();
        AiResponseDto response = schedulingService.callAiServer(request);
        schedulingService.saveAiResult(response);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mock-data/request")
    public ResponseEntity<AiRequestDto> getMockAiRequest() {
        return ResponseEntity.ok(schedulingService.buildMockAiRequest());
    }

    @PostMapping("/mock-data/response")
    public ResponseEntity<AiResponseDto> getMockAiResponse(@RequestBody(required = false) AiRequestDto dto) {
        String requestId = dto == null ? null : dto.getRequestId();
        return ResponseEntity.ok(schedulingService.buildMockAiResponse(requestId));
    }

    // ─── PDF 스펙 기반 새 포맷 ───────────────────────────────────

    /** 새 포맷 요청 JSON 미리보기 (AI 서버 미전송) */
    @GetMapping("/v2/preview")
    public ResponseEntity<ScheduleForecastRequestDto> previewScheduleForecastRequest() {
        return ResponseEntity.ok(schedulingService.buildScheduleForecastRequest());
    }

    /** 새 포맷으로 AI 서버에 실제 전송 */
    @PostMapping("/v2/request")
    public ResponseEntity<?> requestAiV2() {
        ScheduleForecastRequestDto request = schedulingService.buildScheduleForecastRequest();
        AiResponseDto response = aiService.requestScheduleForecast(request);
        if (response != null) {
            schedulingService.saveAiResult(response);
        }
        return ResponseEntity.ok(response);
    }
}