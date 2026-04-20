package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import com.capstone.capstone.entity.*;
import com.capstone.capstone.mqtt.MqttPublisher;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.ScheduleJobRepository;
import com.capstone.capstone.repository.ScheduleResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// AI가 만든 스케줄(JSON)을 DB 구조로 변환해서 저장하는 역할
@Service
@RequiredArgsConstructor
@Transactional
public class SchedulingService {

    private final ScheduleJobRepository scheduleJobRepository;
    private final ChargingStationRepository stationRepository;
    private final ScheduleResultRepository scheduleResultRepository;
    private final AiService aiService;
    private final MqttPublisher mqttPublisher;

    public void runScheduling(){
        AiRequestDto request = buildAiRequest();
        AiResponseDto response = aiService.requestSchedule(requestDto);
        saveAiResult(response);
        mqttPublisher.publish(
                "${mqtt.publish-topic}",
                convertToJson(response)
        );
    }
    //AI에서 온 Response 저장
    public void saveAiResult(AiResponseDto dto) {


        // 1. ScheduleJob 생성
        ScheduleJob job = new ScheduleJob();
        job.setRequestId(dto.getRequestId());
        job.setCreatedAt(LocalDateTime.now());
        job.setStatus(dto.getStatus().getMessage());
        job.setScheduleTargetDate(LocalDate.now().plusDays(1));

        scheduleJobRepository.save(job);

        // 2. Station별 처리
        for (StationScheduleDto stationDto : dto.getStationDayAheadSchedule()) {

            ChargingStation station = stationRepository.findById(stationDto.getStationId())
                    .orElseThrow(() -> new RuntimeException("Station not found"));

            ScheduleResult result = new ScheduleResult();
            result.setScheduleJob(job);
            result.setStation(station);

            scheduleResultRepository.save(result);

            // 3. HourlyPlan 처리
            for (HourlyPlanDto planDto : stationDto.getHourlyPlan()) {

                HourlyPlan plan = new HourlyPlan();
                plan.setScheduleResult(result);
                plan.setHour(planDto.getHour());
                plan.setEssMode(planDto.getEssMode());
                plan.setEssPower(planDto.getEssPower());
                plan.setGridUsage(planDto.getGridUsage());
                plan.setPvPriority(planDto.getPvPriority());

                result.getHourlyPlans().add(plan);

                // 4. Transfer 처리
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
    }
    private AiRequestDto buildAiRequest() {

        AiRequestDto request = new AiRequestDto();

        request.setRequestId("rl-" + LocalDateTime.now());
        request.setTimestamp(Instant.now().toString());


    }
    //clusterState 생성
    private ClusterStateDto buildClusterState() {

        ClusterStateDto dto = new ClusterStateDto();

        dto.setTimeIndex(LocalDateTime.now().getHour());
        dto.setDayOfWeek(LocalDate.now().getDayOfWeek().getValue());
        dto.setTouPrice(174.0);        // 나중에 계산 or DB
        dto.setGridLimit(200.0);
        dto.setTransferEnabled(true);

        return dto;
    }
    private List<StationDto> buildStations() {

        List<ChargingStation> stations = stationRepository.findAll();

        return stations.stream()
                .map(this::toStationDto)
                .toList();
    }

    private StationDto toStationDto(ChargingStation station) {

        StationDto dto = new StationDto();

        dto.setStationId(station.getId());

        // current_state
        CurrentStateDto state = new CurrentStateDto();

        state.setSoc(station.getSoc());
        //이거 어떻게 station에서 시작해서 get을 해야하는지

        state.setDemandCount(calculateDemandCount(station));

        state.setChargers(buildChargers(station));

        state.setPower(buildPower(station));

        dto.setCurrentState(state);

        // constraints
        ConstraintsDto constraints = new ConstraintsDto();

        constraints.setSocMin(0.2);
        constraints.setSocMax(0.9);
        constraints.setEssMaxCharge(15.0);
        constraints.setEssMaxDischarge(15.0);

        dto.setConstraints(constraints);

        return dto;
    }

    private PowerDto buildPower(ChargingStation station) {
        return null;
    }

    private int calculateDemandCount(ChargingStation station) {
        int i = 0;
        return i;
    }

    private List<ChargerDto> buildChargers(ChargingStation station) {
        return null;
    }

}