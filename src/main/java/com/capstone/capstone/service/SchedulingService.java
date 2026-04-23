package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import com.capstone.capstone.entity.*;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.ScheduleJobRepository;
import com.capstone.capstone.repository.ScheduleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SchedulingService {

    private final ScheduleJobRepository scheduleJobRepository;
    private final ChargingStationRepository stationRepository;
    private final ScheduleResultRepository scheduleResultRepository;

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
            scheduleResultRepository.save(result);

            for (HourlyPlanDto planDto : stationDto.getHourlyPlan()) {

                HourlyPlan plan = new HourlyPlan();
                plan.setScheduleResult(result);
                plan.setHour(planDto.getHour());
                plan.setEssMode(planDto.getEssMode());
                plan.setEssPower(planDto.getEssPower());
                plan.setGridUsage(planDto.getGridUsage());
                plan.setPvPriority(planDto.getPvPriority());
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
}
