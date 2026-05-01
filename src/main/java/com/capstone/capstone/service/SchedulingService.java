package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import com.capstone.capstone.dto.mqtt.MqttChargerStatusDto;
import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.dto.mqtt.MqttStationDto;
import com.capstone.capstone.entity.*;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.ScheduleJobRepository;
import com.capstone.capstone.repository.ScheduleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
<<<<<<< HEAD
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
=======
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
>>>>>>> 18bc6ccc6f33932ea608a3d64fd811712713c80e

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SchedulingService {

    private final ScheduleJobRepository scheduleJobRepository;
    private final ChargingStationRepository stationRepository;
    private final ScheduleResultRepository scheduleResultRepository;
    private final DataProcessingService dataProcessingService;
    private final AiService aiService;

    public AiRequestDto buildAiRequest() {
        MqttPayloadDto latest = dataProcessingService.getLatestData();
        if (latest == null || latest.getStations() == null || latest.getStations().isEmpty()) {
            throw new IllegalStateException("No telemetry data available to build AiRequest");
        }
        return buildAiRequest(latest);
    }

    public AiResponseDto callAiServer(AiRequestDto requestDto) {
        return aiService.requestSchedule(requestDto);
    }

    public AiRequestDto buildMockAiRequest() {
        AiRequestDto request = new AiRequestDto();
        request.setRequestId("mock-" + Instant.now().toEpochMilli());
        request.setRequestTimestamp(Instant.now().toString());
        request.setScheduleTargetDate(LocalDate.now().plusDays(1).toString());
        request.setScheduleHorizonHours("24");
        request.setClusterState(buildClusterState());
        request.setStations(buildMockStations());
        return request;
    }

    public AiResponseDto buildMockAiResponse(String requestId) {
        AiResponseDto response = new AiResponseDto();
        response.setRequestId(requestId == null || requestId.isBlank() ? "mock-response" : requestId);
        response.setTimestamp(Instant.now().toString());

        AiResponseDto.Status status = new AiResponseDto.Status();
        status.setSuccess(true);
        status.setErrorCode(0);
        status.setMessage("Mock AI response generated");
        response.setStatus(status);

        List<StationScheduleDto> schedules = new ArrayList<>();
        schedules.add(mockStationSchedule(1L));
        schedules.add(mockStationSchedule(2L));
        response.setStationDayAheadSchedule(schedules);
        return response;
    }

    private AiRequestDto buildAiRequest(MqttPayloadDto latest) {
        AiRequestDto request = new AiRequestDto();
        request.setRequestId("rl-" + Instant.now().toEpochMilli());
        request.setRequestTimestamp(Instant.now().toString());
        request.setScheduleTargetDate(LocalDate.now().plusDays(1).toString());
        request.setScheduleHorizonHours("24");
        request.setClusterState(buildClusterState());
        request.setStations(buildStations(latest));
        return request;
    }

    private ClusterStateDto buildClusterState() {
        ClusterStateDto dto = new ClusterStateDto();
        dto.setTimeIndex(LocalDateTime.now().getHour());
        dto.setDayOfWeek(LocalDate.now().getDayOfWeek().getValue());
        dto.setTouPrice(174.0);
        dto.setGridLimit(200.0);
        dto.setTransferEnabled(true);
        return dto;
    }

    private List<StationDto> buildStations(MqttPayloadDto latest) {
        return latest.getStations().stream()
                .map(this::toStationDto)
                .toList();
    }

    private StationDto toStationDto(MqttStationDto s) {
        StationDto dto = new StationDto();
        dto.setStationId((long) s.getHeader().getStationId());

        CurrentStateDto state = new CurrentStateDto();
        state.setSoc(s.getPayload().getStateOfCharge().getSoc());
        state.setDemandCount((int) s.getPayload().getChargerStatus().stream()
                .filter(MqttChargerStatusDto::isHasDemand).count());
        state.setChargers(s.getPayload().getChargerStatus().stream()
                .map(c -> {
                    ChargerDto cd = new ChargerDto();
                    cd.setChargerId((long) c.getChargerId());
                    cd.setActive(c.isHasDemand());
                    cd.setPowerDemand(c.isHasDemand() ? 7.0 : 0.0);
                    return cd;
                }).toList());

        PowerDto power = new PowerDto();
        power.setPPv(s.getPayload().getPowerMetricsW().getPPv());
        power.setPLoad(s.getPayload().getPowerMetricsW().getPLoad());
        power.setPEss(s.getPayload().getPowerMetricsW().getPEss());
        power.setPGrid(s.getPayload().getPowerMetricsW().getPGrid());
        power.setPTr(s.getPayload().getPowerMetricsW().getPTr());
        state.setPower(power);
        dto.setCurrentState(state);

        ConstraintsDto constraints = new ConstraintsDto();
        constraints.setSocMin(0.2);
        constraints.setSocMax(0.9);
        constraints.setEssMaxCharge(15.0);
        constraints.setEssMaxDischarge(15.0);
        dto.setConstraints(constraints);

        return dto;
    }

    private List<StationDto> buildMockStations() {
        List<StationDto> stations = new ArrayList<>();
        stations.add(mockStation(1L, 0.42, 2, 15.0, 23.0, 0.0, 8.0, 0.0));
        stations.add(mockStation(2L, 0.68, 1, 7.0, 18.0, -4.0, 3.0, 0.0));
        return stations;
    }

    private StationDto mockStation(
            Long stationId,
            double soc,
            int demandCount,
            double pPv,
            double pLoad,
            double pEss,
            double pGrid,
            double pTr
    ) {
        StationDto dto = new StationDto();
        dto.setStationId(stationId);

        CurrentStateDto state = new CurrentStateDto();
        state.setSoc(soc);
        state.setDemandCount(demandCount);

        List<ChargerDto> chargers = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            ChargerDto charger = new ChargerDto();
            charger.setChargerId(stationId * 100 + i);
            charger.setType("AC");
            charger.setActive(i <= demandCount);
            charger.setPowerDemand(i <= demandCount ? 7.0 : 0.0);
            chargers.add(charger);
        }
        state.setChargers(chargers);

        PowerDto power = new PowerDto();
        power.setPPv(pPv);
        power.setPLoad(pLoad);
        power.setPEss(pEss);
        power.setPGrid(pGrid);
        power.setPTr(pTr);
        state.setPower(power);
        dto.setCurrentState(state);

        ConstraintsDto constraints = new ConstraintsDto();
        constraints.setSocMin(0.2);
        constraints.setSocMax(0.9);
        constraints.setEssMaxCharge(15.0);
        constraints.setEssMaxDischarge(15.0);
        dto.setConstraints(constraints);

        return dto;
    }

    private StationScheduleDto mockStationSchedule(Long stationId) {
        StationScheduleDto stationSchedule = new StationScheduleDto();
        stationSchedule.setStationId(stationId);

        List<HourlyPlanDto> plans = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            HourlyPlanDto plan = new HourlyPlanDto();
            plan.setHour(hour);
            plan.setEssMode(hour < 8 ? "charge" : "discharge");
            plan.setEssPower(hour < 8 ? 6.0 : 4.0);
            plan.setGridUsage(hour < 8 ? 12.0 : 8.0);
            plan.setPvPriority(hour >= 10 && hour <= 16 ? 1.0 : 0.5);
            plan.setTransfer(Collections.emptyList());
            plans.add(plan);
        }

        stationSchedule.setHourlyPlan(plans);
        return stationSchedule;
    }

    public void saveAiResult(AiResponseDto dto) {

        ScheduleJob job = new ScheduleJob();
        job.setRequestId(dto.getRequestId());
        job.setCreatedAt(LocalDateTime.now());
        job.setStatus(dto.getStatus().getMessage());
        job.setScheduleTargetDate(LocalDate.now().plusDays(1));
        scheduleJobRepository.save(job);

        for (StationScheduleDto stationDto : dto.getStationDayAheadSchedule()) {

            ChargingStation station = stationRepository.findById(stationDto.getStationId())
                    .orElseThrow(() -> new RuntimeException("Station not found: " + stationDto.getStationId()));

            ScheduleResult result = new ScheduleResult();
            result.setScheduleJob(job);
            result.setStation(station);
            result.setHourlyPlans(new ArrayList<>());
            scheduleResultRepository.save(result);

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
                result.getHourlyPlans().add(plan);

                if (planDto.getTransfer() != null) {
                    for (TransferDto t : planDto.getTransfer()) {
                        Transfer transfer = new Transfer();
                        transfer.setHourlyPlan(plan);
                        transfer.setTargetStationId(t.getTargetStationId());
                        transfer.setPower(t.getPower());
                        plan.getTransfers().add(transfer);
                    }
                }
            }
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
