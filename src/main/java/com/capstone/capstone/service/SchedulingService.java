package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final AiRequestBuilderService aiRequestBuilderService;
    private final ScheduleResultService scheduleResultService;

    public Map<String, Object> buildRawAiRequest() {
        return aiRequestBuilderService.buildRawAiRequest();
    }

    public void saveAiResult(AiResponseDto dto) {
        scheduleResultService.saveAiResult(dto);
    }

    public Optional<ScheduleResponseDto> getScheduleByDate(LocalDate date) {
        return scheduleResultService.getScheduleByDate(date);
    }

    public List<ScheduleHistoryItemDto> getScheduleHistory() {
        return scheduleResultService.getScheduleHistory();
    }
}
