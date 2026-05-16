package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import com.capstone.capstone.entity.*;
import com.capstone.capstone.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleResultService {

    private final ScheduleJobRepository scheduleJobRepository;
    private final ScheduleMetricsRepository scheduleMetricsRepository;
    private final ClusterForecastRepository clusterForecastRepository;
    private final StationDemandForecastRepository stationDemandForecastRepository;
    private final ChargingStationRepository stationRepository;
    private final ScheduleResultRepository scheduleResultRepository;

    @Transactional
    public void saveAiResult(AiResponseDto dto) {
        ScheduleJob job = buildScheduleJob(dto);
        scheduleJobRepository.save(job);

        if (dto.getMetrics() != null) {
            scheduleMetricsRepository.save(buildScheduleMetrics(job, dto.getMetrics()));
        }

        if (dto.getForecastResults() != null) {
            saveForecastResults(job, dto.getForecastResults());
        }

        List<ChargingStation> allStations = stationRepository.findAll();
        allStations.sort(Comparator.comparing(ChargingStation::getId));
        Map<Integer, ChargingStation> indexToStation = allStations.stream()
                .filter(s -> s.getStationIndex() != null)
                .collect(Collectors.toMap(ChargingStation::getStationIndex, s -> s));

        if (dto.getStationDayAheadSchedule() == null) {
            log.warn("[스케줄 저장] station_day_ahead_schedule 없음");
        } else {
            for (StationScheduleDto stationDto : dto.getStationDayAheadSchedule()) {
                int idx = stationDto.getStationId() != null ? stationDto.getStationId().intValue() : 0;
                ChargingStation station = indexToStation.getOrDefault(idx,
                        (idx >= 0 && idx < allStations.size()) ? allStations.get(idx) : null);
                if (station == null) {
                    log.warn("[스케줄 저장] stationIndex={} 매핑 실패 - 스킵", idx);
                    continue;
                }
                scheduleResultRepository.save(buildScheduleResult(job, station, stationDto, indexToStation));
            }
        }

        job.setCompletedAt(LocalDateTime.now());
        job.setStatus("SUCCESS");
        scheduleJobRepository.save(job);

        int stationCount = dto.getStationDayAheadSchedule() != null ? dto.getStationDayAheadSchedule().size() : 0;
        log.info("[스케줄 저장 완료] requestId={}, 스테이션 수={}", dto.getRequestId(), stationCount);
    }

    private ScheduleJob buildScheduleJob(AiResponseDto dto) {
        ScheduleJob job = new ScheduleJob();
        job.setRequestId(dto.getRequestId());
        job.setScheduleMode(dto.getScheduleMode());
        job.setScheduleTargetDate(dto.getScheduleTargetDate() != null
                ? LocalDate.parse(dto.getScheduleTargetDate())
                : LocalDate.now().plusDays(1));
        job.setCreatedAt(LocalDateTime.now());
        job.setStatus(dto.getStatus() != null ? dto.getStatus().getMessage() : "RECEIVED");

        if (dto.getTimestamp() != null) {
            try {
                job.setResponseAt(OffsetDateTime.parse(dto.getTimestamp()));
            } catch (Exception e) {
                log.warn("[스케줄 저장] timestamp 파싱 실패: {}", dto.getTimestamp());
            }
        }

        if (dto.getModel() != null) {
            job.setModelAlgorithm(dto.getModel().getAlgorithm());
            job.setModelVersion(dto.getModel().getVersion());
        }

        return job;
    }

    private ScheduleMetrics buildScheduleMetrics(ScheduleJob job, AiResponseDto.MetricsDto m) {
        ScheduleMetrics metrics = new ScheduleMetrics();
        metrics.setScheduleJob(job);
        metrics.setGridOnlyCostKrw(m.getGridOnlyCostKrw());
        metrics.setSacCostWithTransferKrw(m.getSacCostWithTransferKrw());
        metrics.setCostReductionKrw(m.getCostReductionKrw());
        metrics.setCostReductionPct(m.getCostReductionPct());
        metrics.setTotalDemandKwh(m.getTotalDemandKwh());
        metrics.setTotalPvKwh(m.getTotalPvKwh());
        metrics.setTotalGridUsageKwh(m.getTotalGridUsageKwh());
        metrics.setTotalSelfSupplyKwh(m.getTotalSelfSupplyKwh());
        metrics.setTotalPvLostKwh(m.getTotalPvLostKwh());
        metrics.setTotalTransferOutKwh(m.getTotalTransferOutKwh());
        metrics.setTotalTransferInKwh(m.getTotalTransferInKwh());
        metrics.setTotalTransferLossKwh(m.getTotalTransferLossKwh());
        metrics.setMaxClusterGridUsageKwhPerHour(m.getMaxClusterGridUsageKwhPerHour());
        metrics.setPeakViolationSlots(m.getPeakViolationSlots());
        metrics.setSocMin(m.getSocMin());
        metrics.setSocMax(m.getSocMax());
        metrics.setTotalRewardBeforeTransfer(m.getTotalRewardBeforeTransfer());
        metrics.setDevice(m.getDevice());
        metrics.setTorchCudaAvailable(m.getTorchCudaAvailable());
        metrics.setCudaDeviceName(m.getCudaDeviceName());
        metrics.setTransferPostprocessEnabled(m.getTransferPostprocessEnabled());
        return metrics;
    }

    private void saveForecastResults(ScheduleJob job, AiResponseDto.ForecastResultsDto forecastResults) {
        if (forecastResults.getPvDayAheadForecast() != null) {
            for (AiResponseDto.PvForecastSlotDto pv : forecastResults.getPvDayAheadForecast()) {
                ClusterForecast cf = new ClusterForecast();
                cf.setScheduleJob(job);
                cf.setHour(pv.getHour());
                cf.setPredictedPvKwhPerStation(pv.getPredictedPvKwhPerStation());
                cf.setPredictedClusterPvKwh(pv.getPredictedClusterPvKwh());
                clusterForecastRepository.save(cf);
            }
        }

        if (forecastResults.getDemandDayAheadForecast() != null) {
            for (AiResponseDto.DemandForecastSlotDto d : forecastResults.getDemandDayAheadForecast()) {
                StationDemandForecast sdf = new StationDemandForecast();
                sdf.setScheduleJob(job);
                sdf.setStationIndex(d.getStationId());
                sdf.setHour(d.getHour());
                sdf.setPredictedDemandKwh(d.getPredictedDemandKwh());
                stationDemandForecastRepository.save(sdf);
            }
        }
    }

    private ScheduleResult buildScheduleResult(ScheduleJob job, ChargingStation station,
            StationScheduleDto stationDto, Map<Integer, ChargingStation> indexToStation) {
        ScheduleResult result = new ScheduleResult();
        result.setScheduleJob(job);
        result.setStation(station);
        result.setStationName(stationDto.getStationName());
        result.setHourlyPlans(new ArrayList<>());

        List<HourlyPlanDto> plans = stationDto.getHourlyPlan() != null
                ? stationDto.getHourlyPlan()
                : Collections.emptyList();

        for (HourlyPlanDto planDto : plans) {
            HourlyPlan plan = new HourlyPlan();
            plan.setScheduleResult(result);
            plan.setHour(planDto.getHour());
            plan.setEssMode(planDto.getEssMode());
            plan.setEssPowerKw(planDto.getEssPowerKw());
            plan.setEssPowerSignedKw(planDto.getEssPowerSignedKw());
            plan.setEssEnergyKwh(planDto.getEssEnergyKwh());
            plan.setGridUsageKw(planDto.getGridUsageKw());
            plan.setGridUsageKwh(planDto.getGridUsageKwh());
            plan.setPvGenerationPredKwh(planDto.getPvGenerationPredKwh());
            plan.setLoadPredKwh(planDto.getLoadPredKwh());
            plan.setPvPriority(planDto.getPvPriority());
            plan.setExpectedSoc(planDto.getExpectedSoc());
            plan.setTransfers(new ArrayList<>());

            if (planDto.getTransfer() != null) {
                for (TransferDto t : planDto.getTransfer()) {
                    int targetIdx = t.getTargetStationId() != null ? t.getTargetStationId().intValue() : -1;
                    ChargingStation targetStation = indexToStation.get(targetIdx);
                    if (targetStation == null) {
                        log.warn("[스케줄 저장] Transfer 대상 stationIndex={} 없음 - 스킵", targetIdx);
                        continue;
                    }
                    Transfer transfer = new Transfer();
                    transfer.setHourlyPlan(plan);
                    transfer.setTargetStation(targetStation);
                    transfer.setTransferPowerKw(t.getTransferPowerKw());
                    transfer.setReceivedPowerKw(t.getReceivedPowerKw());
                    transfer.setLossPowerKw(t.getLossPowerKw());
                    transfer.setTransferEnergyKwh(t.getTransferEnergyKwh());
                    transfer.setReceivedEnergyKwh(t.getReceivedEnergyKwh());
                    transfer.setLossEnergyKwh(t.getLossEnergyKwh());
                    plan.getTransfers().add(transfer);
                }
            }

            result.getHourlyPlans().add(plan);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public Optional<ScheduleResponseDto> getScheduleByDate(LocalDate date) {
        return scheduleJobRepository.findFirstByScheduleTargetDateOrderByCreatedAtDesc(date)
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
        dto.setEssPowerKw(plan.getEssPowerKw());
        dto.setEssPowerSignedKw(plan.getEssPowerSignedKw());
        dto.setEssEnergyKwh(plan.getEssEnergyKwh());
        dto.setGridUsageKw(plan.getGridUsageKw());
        dto.setGridUsageKwh(plan.getGridUsageKwh());
        dto.setPvGenerationPredKwh(plan.getPvGenerationPredKwh());
        dto.setLoadPredKwh(plan.getLoadPredKwh());
        dto.setPvPriority(plan.getPvPriority());
        dto.setExpectedSoc(plan.getExpectedSoc());

        List<TransferDto> transfers = plan.getTransfers().stream()
                .map(t -> {
                    TransferDto td = new TransferDto();
                    td.setTargetStationId(t.getTargetStation() != null
                            ? t.getTargetStation().getStationIndex().longValue()
                            : null);
                    td.setTransferPowerKw(t.getTransferPowerKw());
                    td.setReceivedPowerKw(t.getReceivedPowerKw());
                    td.setLossPowerKw(t.getLossPowerKw());
                    td.setTransferEnergyKwh(t.getTransferEnergyKwh());
                    td.setReceivedEnergyKwh(t.getReceivedEnergyKwh());
                    td.setLossEnergyKwh(t.getLossEnergyKwh());
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
        dto.setCostReductionKrw(job.getCostReductionKrw());
        dto.setCostReductionPct(job.getCostReductionPct());
        dto.setGridOnlyCostKrw(job.getGridOnlyCostKrw());
        return dto;
    }
}
