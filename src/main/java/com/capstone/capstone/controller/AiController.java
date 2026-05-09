package com.capstone.capstone.controller;

import com.capstone.capstone.dto.AiResponseDto;
import com.capstone.capstone.service.AiService;
import com.capstone.capstone.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


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

    /** 샘플/커스텀 payload를 AI 서버에 직접 전송, 결과는 콜백(/ai/result)으로 수신 */
    @PostMapping("/v2/send-sample")
    public ResponseEntity<Void> sendSampleToAi(@RequestBody Map<String, Object> payload) {
        aiService.sendRaw(payload);
        return ResponseEntity.accepted().build();
    }

    /** IoT가 만든 payload를 그대로 AI 서버(/ai/control)로 전달 - 시연용 */
    @PostMapping("/iot/send")
    public ResponseEntity<Void> sendFromIot(@RequestBody Map<String, Object> payload) {
        aiService.sendRaw(payload);
        return ResponseEntity.accepted().build();
    }
}