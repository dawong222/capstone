package com.capstone.capstone.controller;

import com.capstone.capstone.dto.AiRequestDto;
import com.capstone.capstone.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final AiService aiService;

    @PostMapping("/run")
    public ResponseEntity<Void> runSchedule(@RequestBody AiRequestDto dto) {
        aiService.requestSchedule(dto);
        return ResponseEntity.ok().build();
    }
}