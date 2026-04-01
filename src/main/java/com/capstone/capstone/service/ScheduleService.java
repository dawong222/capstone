package com.capstone.capstone.service;

import com.capstone.capstone.dto.AiResponseDto;
import com.capstone.capstone.dto.HourlyPlanDto;
import com.capstone.capstone.dto.StationScheduleDto;
import com.capstone.capstone.dto.TransferDto;
import com.capstone.capstone.entity.*;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.ScheduleJobRepository;
import com.capstone.capstone.repository.ScheduleResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

// AI가 만든 스케줄(JSON)을 DB 구조로 변환해서 저장하는 역할
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleJobRepository scheduleJobRepository;
    private final ChargingStationRepository stationRepository;
    private final ScheduleResultRepository scheduleResultRepository;

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
                plan.setEssPowerW(planDto.getEssPowerW());
                plan.setGridUsageW(planDto.getGridUsageW());
                plan.setPvPriority(planDto.getPvPriority());

                result.getHourlyPlans().add(plan);

                // 4. Transfer 처리
                if (planDto.getTransfer() != null) {
                    for (TransferDto t : planDto.getTransfer()) {

                        Transfer transfer = new Transfer();
                        transfer.setHourlyPlan(plan);
                        transfer.setTargetStationId(t.getTargetStationId());
                        transfer.setPowerW(t.getPowerW());

                        plan.getTransfers().add(transfer);
                    }
                }
            }
        }
    }
}