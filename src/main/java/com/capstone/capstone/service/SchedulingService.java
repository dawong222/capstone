package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final ScheduleResultService scheduleResultService;
    private final ScheduleMqttPublisherService scheduleMqttPublisherService;

    public void saveAiResult(AiResponseDto dto) {
        scheduleResultService.saveAiResult(dto);
        scheduleMqttPublisherService.publishSchedule(dto);
    }

    public Optional<ScheduleResponseDto> getScheduleByDate(LocalDate date) {
        return scheduleResultService.getScheduleByDate(date);
    }

    public List<ScheduleHistoryItemDto> getScheduleHistory() {
        return scheduleResultService.getScheduleHistory();
    }
}
