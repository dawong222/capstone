package com.capstone.capstone.controller;

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
}