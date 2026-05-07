package com.capstone.capstone.controller;

import com.capstone.capstone.dto.AiResponseDto;
import com.capstone.capstone.service.AiService;
import com.capstone.capstone.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
        Map<String, Object> payload = schedulingService.buildRawAiRequest();
        AiResponseDto response = aiService.sendRawAndParse(payload);
        schedulingService.saveAiResult(response);
        return ResponseEntity.ok(response);
    }

    // ─── v2: raw 데이터 그대로 AI 서버 전송 ──────────────────────

    /** 전송할 JSON 미리보기 (AI 서버 미전송) */
    @GetMapping("/v2/preview")
    public ResponseEntity<Map<String, Object>> previewRawRequest() {
        return ResponseEntity.ok(schedulingService.buildRawAiRequest());
    }

    /** Raw 데이터를 AI 서버에 전송하고 결과 저장 */
    @PostMapping("/v2/send")
    public ResponseEntity<String> sendRawToAi() {
        Map<String, Object> payload = schedulingService.buildRawAiRequest();
        AiResponseDto response = aiService.sendRawAndParse(payload);
        schedulingService.saveAiResult(response);
        return ResponseEntity.ok("스케줄 저장 완료: requestId=" + response.getRequestId());
    }
}