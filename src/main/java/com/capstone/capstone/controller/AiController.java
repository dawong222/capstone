package com.capstone.capstone.controller;

import com.capstone.capstone.dto.AiRequestDto;
import com.capstone.capstone.dto.AiResponseDto;
import com.capstone.capstone.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final SchedulingService schedulingService;

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
}