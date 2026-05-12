package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import com.capstone.capstone.entity.ChargingStation;
import com.capstone.capstone.repository.ChargingStationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final AiRequestBuilderService aiRequestBuilderService;
    private final ScheduleResultService scheduleResultService;
    private final ScheduleMqttPublisherService scheduleMqttPublisherService;
    private final ChargingStationRepository chargingStationRepository;

    public Map<String, Object> buildRawAiRequest() {
        return aiRequestBuilderService.buildRawAiRequest();
    }

    public void saveAiResult(AiResponseDto dto) {
        scheduleMqttPublisherService.publishSchedule(dto);
    }

    public ScheduleResponseDto convertToScheduleResponse(AiResponseDto dto) {
        List<ChargingStation> allStations = chargingStationRepository.findAll();
        Map<Integer, String> indexToName = allStations.stream()
            .filter(s -> s.getStationIndex() != null)
            .collect(Collectors.toMap(
                ChargingStation::getStationIndex,
                s -> s.getName() != null ? s.getName() : "충전소 " + s.getStationIndex()
            ));

        ScheduleResponseDto response = new ScheduleResponseDto();
        response.setRequestId(dto.getRequestId());
        response.setTargetDate(LocalDate.now().plusDays(1).toString());
        response.setCreatedAt(LocalDateTime.now().toString());
        response.setStatus("LIVE");

        List<StationScheduleResponseDto> stations = dto.getStationDayAheadSchedule() == null
            ? List.of()
            : dto.getStationDayAheadSchedule().stream().map(s -> {
                int idx = s.getStationId() != null ? s.getStationId().intValue() : 0;
                StationScheduleResponseDto r = new StationScheduleResponseDto();
                r.setStationId(s.getStationId());
                r.setStationName(indexToName.getOrDefault(idx, "충전소 " + idx));
                r.setHourlyPlan(s.getHourlyPlan());
                return r;
            }).collect(Collectors.toList());

        response.setStations(stations);
        return response;
    }

    public Optional<ScheduleResponseDto> getScheduleByDate(LocalDate date) {
        return scheduleResultService.getScheduleByDate(date);
    }

    public List<ScheduleHistoryItemDto> getScheduleHistory() {
        return scheduleResultService.getScheduleHistory();
    }
}
