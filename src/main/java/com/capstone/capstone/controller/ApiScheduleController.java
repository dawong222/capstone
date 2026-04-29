package com.capstone.capstone.controller;

import com.capstone.capstone.dto.ScheduleHistoryItemDto;
import com.capstone.capstone.dto.ScheduleResponseDto;
import com.capstone.capstone.service.SchedulingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedule")
@RequiredArgsConstructor
public class ApiScheduleController {

    private final SchedulingService schedulingService;

    @GetMapping("/today")
    public ResponseEntity<ScheduleResponseDto> getToday() {
        return schedulingService.getScheduleByDate(LocalDate.now())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/tomorrow")
    public ResponseEntity<ScheduleResponseDto> getTomorrow() {
        return schedulingService.getScheduleByDate(LocalDate.now().plusDays(1))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<ScheduleResponseDto> getByDate(@PathVariable String date) {
        return schedulingService.getScheduleByDate(LocalDate.parse(date))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/history")
    public ResponseEntity<List<ScheduleHistoryItemDto>> getHistory() {
        return ResponseEntity.ok(schedulingService.getScheduleHistory());
    }
}
