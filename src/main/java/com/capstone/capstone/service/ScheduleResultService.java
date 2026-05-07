package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import com.capstone.capstone.entity.*;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.HourlySnapshotRepository;
import com.capstone.capstone.repository.ScheduleJobRepository;
import com.capstone.capstone.repository.ScheduleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleResultService {

    private final ScheduleJobRepository scheduleJobRepository;
    private final ChargingStationRepository stationRepository;
    private final ScheduleResultRepository scheduleResultRepository;

    @Transactional
    public void saveAiResult(AiResponseDto dto) {
        ScheduleJob job = new ScheduleJob();
        job.setRequestId(dto.getRequestId());
        job.setCreatedAt(LocalDateTime.now());
        job.setStatus(dto.getStatus() != null ? dto.getStatus().getMessage() : "SUCCESS");
        job.setScheduleTargetDate(LocalDate.now().plusDays(1));
        scheduleJobRepository.save(job);

        // AI 응답의 station_id는 0-based 인덱스 → DB 실제 ID와 매핑
        List<ChargingStation> allStations = stationRepository.findAll();
        allStations.sort(Comparator.comparing(ChargingStation::getId));

        for (StationScheduleDto stationDto : dto.getStationDayAheadSchedule()) {
            int idx = stationDto.getStationId() != null ? stationDto.getStationId().intValue() : 0;
            ChargingStation station = (idx >= 0 && idx < allStations.size())
                    ? allStations.get(idx)
                    : stationRepository.findById(stationDto.getStationId())
                        .orElseThrow(() -> new RuntimeException("Station not found: " + stationDto.getStationId()));

            // 전체 그래프를 먼저 구성한 뒤 한 번에 save → cascade 정상 동작
            ScheduleResult result = new ScheduleResult();
            result.setScheduleJob(job);
            result.setStation(station);
            result.setHourlyPlans(new ArrayList<>());

            List<HourlyPlanDto> plans = stationDto.getHourlyPlan() == null
                    ? Collections.emptyList()
                    : stationDto.getHourlyPlan();

            for (HourlyPlanDto planDto : plans) {
                HourlyPlan plan = new HourlyPlan();
                plan.setScheduleResult(result);
                plan.setHour(planDto.getHour());
                plan.setEssMode(planDto.getEssMode());
                plan.setEssPower(planDto.getEssPower());
                plan.setGridUsage(planDto.getGridUsage());
                plan.setPvPriority(planDto.getPvPriority());
                plan.setTransfers(new ArrayList<>());

                if (planDto.getTransfer() != null) {
                    for (TransferDto t : planDto.getTransfer()) {
                        Transfer transfer = new Transfer();
                        transfer.setHourlyPlan(plan);
                        transfer.setTargetStationId(t.getTargetStationId());
                        transfer.setPower(t.getPower());
                        plan.getTransfers().add(transfer);
                    }
                }

                result.getHourlyPlans().add(plan);
            }

            scheduleResultRepository.save(result);
        }

        log.info("[스케줄 저장 완료] requestId={}, 스테이션 수={}",
                dto.getRequestId(), dto.getStationDayAheadSchedule().size());
    }

    @Transactional(readOnly = true)
    public Optional<ScheduleResponseDto> getScheduleByDate(LocalDate date) {
        return scheduleJobRepository.findByScheduleTargetDate(date)
                .map(this::toScheduleResponseDto);
    }

    @Transactional(readOnly = true)
    public List<ScheduleHistoryItemDto> getScheduleHistory() {
        return scheduleJobRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(this::toHistoryItemDto)
                .collect(Collectors.toList());
    }

    private ScheduleResponseDto toScheduleResponseDto(ScheduleJob job) {
        ScheduleResponseDto dto = new ScheduleResponseDto();
        dto.setRequestId(job.getRequestId());
        dto.setTargetDate(job.getScheduleTargetDate().toString());
        dto.setCreatedAt(job.getCreatedAt().toString());
        dto.setStatus(job.getStatus());

        List<StationScheduleResponseDto> stations = scheduleResultRepository
                .findByScheduleJobId(job.getId()).stream()
                .map(this::toStationScheduleResponseDto)
                .collect(Collectors.toList());
        dto.setStations(stations);
        return dto;
    }

    private StationScheduleResponseDto toStationScheduleResponseDto(ScheduleResult result) {
        StationScheduleResponseDto dto = new StationScheduleResponseDto();
        dto.setStationId(result.getStation().getId());
        dto.setStationName(result.getStation().getName());

        List<HourlyPlanDto> plans = result.getHourlyPlans().stream()
                .map(this::toHourlyPlanDto)
                .sorted(Comparator.comparingInt(HourlyPlanDto::getHour))
                .collect(Collectors.toList());
        dto.setHourlyPlan(plans);
        return dto;
    }

    private HourlyPlanDto toHourlyPlanDto(HourlyPlan plan) {
        HourlyPlanDto dto = new HourlyPlanDto();
        dto.setHour(plan.getHour());
        dto.setEssMode(plan.getEssMode());
        dto.setEssPower(plan.getEssPower());
        dto.setGridUsage(plan.getGridUsage());
        dto.setPvPriority(plan.getPvPriority());

        List<TransferDto> transfers = plan.getTransfers().stream()
                .map(t -> {
                    TransferDto td = new TransferDto();
                    td.setTargetStationId(t.getTargetStationId());
                    td.setPower(t.getPower());
                    return td;
                })
                .collect(Collectors.toList());
        dto.setTransfer(transfers);
        return dto;
    }

    private ScheduleHistoryItemDto toHistoryItemDto(ScheduleJob job) {
        ScheduleHistoryItemDto dto = new ScheduleHistoryItemDto();
        dto.setRequestId(job.getRequestId());
        dto.setTargetDate(job.getScheduleTargetDate().toString());
        dto.setCreatedAt(job.getCreatedAt().toString());
        dto.setStatus(job.getStatus());
        return dto;
    }
}
