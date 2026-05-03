package com.capstone.capstone.service;

import com.capstone.capstone.dto.*;
import com.capstone.capstone.dto.ai.*;
import com.capstone.capstone.dto.mqtt.MqttChargerStatusDto;
import com.capstone.capstone.dto.mqtt.MqttPayloadDto;
import com.capstone.capstone.dto.mqtt.MqttStationDto;
import com.capstone.capstone.entity.*;
import com.capstone.capstone.repository.ChargingStationRepository;
import com.capstone.capstone.repository.HourlySnapshotRepository;
import com.capstone.capstone.repository.ScheduleJobRepository;
import com.capstone.capstone.repository.ScheduleResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class SchedulingService {

    private final ScheduleJobRepository scheduleJobRepository;
    private final ChargingStationRepository stationRepository;
    private final ScheduleResultRepository scheduleResultRepository;
    private final HourlySnapshotRepository snapshotRepository;
    private final DataProcessingService dataProcessingService;
    private final AiService aiService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${weather.asos.key}")
    private String asosKey;

    @Value("${weather.forecast.key}")
    private String forecastKey;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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
        Map<String, Object> weather = new HashMap<>();

        weather.put("historyRaw", getHistoryRaw());
// 나중에
// weather.put("forecastRaw", getForecastRaw());

        request.setWeather(weather);
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

    // ─────────────────────────────────────────────────────────────
    // PDF 스펙 기반 AI 요청 빌더 (기상청 미연동 → weather 필드 빈 배열)
    // ─────────────────────────────────────────────────────────────

    public ScheduleForecastRequestDto buildScheduleForecastRequest() {
        LocalDate d = LocalDate.now();
        LocalDate targetDate = d.plusDays(1);
        ZonedDateTime callTime = ZonedDateTime.now(KST);
        DateTimeFormatter iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HHmmss");

        ScheduleForecastRequestDto req = new ScheduleForecastRequestDto();
        req.setRequestId(String.format("schedule-forecast-%s-%s-0001",
                d.format(dateFmt), callTime.format(timeFmt)));
        req.setRequestTimestamp(callTime.format(iso));
        req.setScheduleTargetDate(targetDate.toString());
        req.setScheduleHorizonHours(24);

        TargetWindowDto window = new TargetWindowDto();
        window.setStart(targetDate.atStartOfDay(KST).format(iso));
        window.setEnd(targetDate.plusDays(1).atStartOfDay(KST).format(iso));
        window.setSlotUnit("hour");
        window.setSlotCount(24);
        window.setSlotDefinition("slot 0 = 00:00~01:00, slot 23 = 23:00~24:00");
        req.setTargetWindow(window);

        // 충전소 ID → 이름 맵 (MQTT stationId 순서 기준)
        List<ChargingStation> allStations = stationRepository.findAll();
        allStations.sort(Comparator.comparing(ChargingStation::getId));
        Map<Integer, String> stationNameMap = new HashMap<>();
        for (int i = 0; i < allStations.size(); i++) {
            stationNameMap.put(i, allStations.get(i).getName());
        }

        // 과거 스냅샷 조회 (D-7 00:00 ~ D 21:00)
        LocalDateTime rangeStart = d.minusDays(7).atStartOfDay();
        LocalDateTime rangeEnd = d.atTime(21, 0, 0);
        List<HourlySnapshot> snapshots =
                snapshotRepository.findByRecordedAtBetweenOrderByRecordedAt(rangeStart, rangeEnd);

        String startDt = d.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE);
        String endDt   = d.format(DateTimeFormatter.BASIC_ISO_DATE);
        String baseDateFmt = d.format(DateTimeFormatter.BASIC_ISO_DATE);

        req.setDemandPastDemandHourly(buildDemandPastDemand(snapshots, stationNameMap, iso));
        req.setDemandPastWeatherHourly(fetchAsosWeather("108", startDt, endDt, iso));
        req.setDemandForecastShortTermHourly(fetchVilageFcst(61, 125, baseDateFmt, targetDate, iso));
        req.setPvPastGenerationHourly(buildPvPastGeneration(snapshots, iso));
        req.setPvPastWeatherHourly(fetchAsosWeather("129", startDt, endDt, iso));
        req.setPvForecastShortTermHourly(fetchVilageFcst(68, 100, baseDateFmt, targetDate, iso));
        req.setStationCurrentStates(buildStationCurrentStates(allStations, callTime, iso));
        req.setTouPriceHourly(buildTouPriceHourly(targetDate, iso));
        req.setGridConstraints(buildGridConstraints());
        req.setEssConstraints(buildEssConstraints());
        req.setTransferTopology(buildTransferTopology(allStations));

        return req;
    }

    private List<DemandPastDemandItemDto> buildDemandPastDemand(
            List<HourlySnapshot> snapshots, Map<Integer, String> nameMap,
            DateTimeFormatter iso) {

        List<DemandPastDemandItemDto> items = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            String name = nameMap.getOrDefault(s.getStationId(), "Station-" + s.getStationId());
            ZonedDateTime slotStart = s.getRecordedAt().atZone(KST);
            String tm = slotStart.format(iso);
            String slotEnd = slotStart.plusHours(1).format(iso);
            double demandKwh = s.getPLoad() != null ? s.getPLoad() / 1000.0 : 0.0;
            items.add(new DemandPastDemandItemDto(tm, name, tm, slotEnd, demandKwh));
        }
        return items;
    }

    private List<PvPastGenerationItemDto> buildPvPastGeneration(
            List<HourlySnapshot> snapshots, DateTimeFormatter iso) {

        // 충전소 1개(stationId=0) 50kW PV 기준
        List<PvPastGenerationItemDto> items = new ArrayList<>();
        snapshots.stream()
                .filter(s -> s.getStationId() != null && s.getStationId() == 0)
                .forEach(s -> {
                    ZonedDateTime slotStart = s.getRecordedAt().atZone(KST);
                    String tm = slotStart.format(iso);
                    String slotEnd = slotStart.plusHours(1).format(iso);
                    double genKwh = s.getPPv() != null ? s.getPPv() / 1000.0 : 0.0;
                    items.add(new PvPastGenerationItemDto(tm, tm, slotEnd, genKwh));
                });
        return items;
    }

    private List<StationCurrentStateItemDto> buildStationCurrentStates(
            List<ChargingStation> allStations, ZonedDateTime callTime, DateTimeFormatter iso) {

        MqttPayloadDto latest = dataProcessingService.getLatestData();
        String timestamp = callTime.format(iso);
        List<StationCurrentStateItemDto> states = new ArrayList<>();

        for (int i = 0; i < allStations.size(); i++) {
            ChargingStation station = allStations.get(i);
            StationCurrentStateItemDto state = new StationCurrentStateItemDto();
            state.setStationId(i);
            state.setStationName(station.getName());
            state.setPhysical(true);
            state.setTimestamp(timestamp);
            state.setErrorCode(0);

            boolean hasMqtt = latest != null && latest.getStations() != null
                    && i < latest.getStations().size();

            if (hasMqtt) {
                MqttStationDto mqttStation = latest.getStations().get(i);
                state.setPhysical(mqttStation.getHeader().isPhysical());
                state.setEssSoc(mqttStation.getPayload().getStateOfCharge().getSoc());

                List<MqttChargerStatusDto> csList = mqttStation.getPayload().getChargerStatus();
                state.setChargerCount(csList.size());

                List<ChargerStateItemDto> chargers = new ArrayList<>();
                for (MqttChargerStatusDto cs : csList) {
                    ChargerStateItemDto cd = new ChargerStateItemDto();
                    cd.setChargerId(String.format("ch-%03d", cs.getChargerId()));
                    cd.setChargerType("fast");
                    cd.setRatedPowerKw(100.0);
                    cd.setActive(cs.isHasDemand());
                    cd.setCurrentPowerKw(cs.isHasDemand() ? 7.0 : 0.0);
                    chargers.add(cd);
                }
                state.setChargers(chargers);
            } else {
                state.setEssSoc(0.5);
                state.setChargerCount(0);
                state.setChargers(new ArrayList<>());
            }

            states.add(state);
        }
        return states;
    }

    private List<TouPriceItemDto> buildTouPriceHourly(LocalDate targetDate, DateTimeFormatter iso) {
        List<TouPriceItemDto> prices = new ArrayList<>();
        for (int slot = 0; slot < 24; slot++) {
            ZonedDateTime start = targetDate.atTime(slot, 0).atZone(KST);
            ZonedDateTime end = start.plusHours(1);

            // 한국전력 고압A 갑 I 봄/가을 기준 TOU
            String level;
            double price;
            if (slot >= 23 || slot < 9) {
                level = "off_peak";
                price = 93.3;
            } else if ((slot >= 10 && slot < 12) || (slot >= 13 && slot < 17)) {
                level = "on_peak";
                price = 229.5;
            } else {
                level = "mid_peak";
                price = 146.9;
            }

            prices.add(new TouPriceItemDto(slot, start.format(iso), end.format(iso), level, price));
        }
        return prices;
    }

    private GridConstraintsDto buildGridConstraints() {
        GridConstraintsDto grid = new GridConstraintsDto();
        grid.setClusterGridLimitKw(500.0);
        grid.setStationGridLimitKw(120.0);
        grid.setPeakLimitKw(500.0);
        return grid;
    }

    private EssConstraintsDto buildEssConstraints() {
        EssConstraintsDto ess = new EssConstraintsDto();
        ess.setEssCapacityKwhPerStation(100.0);
        ess.setEssMinSoc(0.1);
        ess.setEssMaxSoc(0.9);
        ess.setEssMaxChargeKw(50.0);
        ess.setEssMaxDischargeKw(50.0);
        ess.setRoundTripEfficiency(0.85);
        return ess;
    }

    private TransferTopologyDto buildTransferTopology(List<ChargingStation> allStations) {
        TransferTopologyDto topology = new TransferTopologyDto();
        topology.setTransferEnabled(true);
        topology.setStationOrder(allStations.stream().map(ChargingStation::getName).toList());
        topology.setAdjacencyMatrix5x5(List.of(
                List.of(0, 1, 1, 0, 0),
                List.of(1, 0, 1, 1, 0),
                List.of(1, 1, 0, 1, 1),
                List.of(0, 1, 1, 0, 1),
                List.of(0, 0, 1, 1, 0)
        ));
        topology.setTransferCapacityKwMatrix5x5(List.of(
                List.of(0, 50, 50, 0, 0),
                List.of(50, 0, 50, 50, 0),
                List.of(50, 50, 0, 50, 50),
                List.of(0, 50, 50, 0, 50),
                List.of(0, 0, 50, 50, 0)
        ));
        topology.setTransferLossRateMatrix5x5(List.of(
                List.of(0.0, 0.02, 0.02, 0.0, 0.0),
                List.of(0.02, 0.0, 0.02, 0.02, 0.0),
                List.of(0.02, 0.02, 0.0, 0.02, 0.02),
                List.of(0.0, 0.02, 0.02, 0.0, 0.02),
                List.of(0.0, 0.0, 0.02, 0.02, 0.0)
        ));
        return topology;
    }

    // ─────────────────────────────────────────────────────────────
    // Raw Map 방식 AI 요청 (전처리 없이 날것 데이터 그대로 전송)
    // ─────────────────────────────────────────────────────────────

    public Map<String, Object> buildRawAiRequest() {
        LocalDate d = LocalDate.now();
        LocalDate targetDate = d.plusDays(1);
        ZonedDateTime callTime = ZonedDateTime.now(KST);
        DateTimeFormatter iso = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
        String startDt   = d.minusDays(7).format(DateTimeFormatter.BASIC_ISO_DATE);
        String endDt     = d.format(DateTimeFormatter.BASIC_ISO_DATE);
        String baseDate  = endDt;

        // 충전소 목록 (MQTT stationId 순서 기준)
        List<ChargingStation> allStations = stationRepository.findAll();
        allStations.sort(Comparator.comparing(ChargingStation::getId));
        Map<Integer, String> nameMap = new HashMap<>();
        for (int i = 0; i < allStations.size(); i++) nameMap.put(i, allStations.get(i).getName());

        // 과거 스냅샷 (D-7 00:00 ~ D 21:00)
        List<HourlySnapshot> snapshots = snapshotRepository.findByRecordedAtBetweenOrderByRecordedAt(
                d.minusDays(7).atStartOfDay(), d.atTime(21, 0, 0));

        Map<String, Object> req = new LinkedHashMap<>();

        // ── 메타 ──────────────────────────────────────────────────
        req.put("request_id", String.format("schedule-forecast-%s-%s-0001",
                d.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                callTime.format(DateTimeFormatter.ofPattern("HHmmss"))));
        req.put("request_timestamp", callTime.format(iso));
        req.put("schedule_target_date", targetDate.toString());
        req.put("schedule_horizon_hours", 24);

        Map<String, Object> window = new LinkedHashMap<>();
        window.put("start", targetDate.atStartOfDay(KST).format(iso));
        window.put("end",   targetDate.plusDays(1).atStartOfDay(KST).format(iso));
        window.put("slot_unit",  "hour");
        window.put("slot_count", 24);
        window.put("slot_definition", "slot 0 = 00:00~01:00, slot 23 = 23:00~24:00");
        req.put("target_window", window);

        // ── 수요 과거 (DB 스냅샷) ──────────────────────────────────
        List<Map<String, Object>> demandPast = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            ZonedDateTime st = s.getRecordedAt().atZone(KST);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tm",           st.format(iso));
            item.put("station_name", nameMap.getOrDefault(s.getStationId(), "station-" + s.getStationId()));
            item.put("slot_start",   st.format(iso));
            item.put("slot_end",     st.plusHours(1).format(iso));
            item.put("demand_kwh",   s.getPLoad() != null ? s.getPLoad() / 1000.0 : 0.0);
            demandPast.add(item);
        }
        req.put("demand_past_demand_hourly", demandPast);

        // ── 수요 과거 날씨 (ASOS 108 서울) ───────────────────────
        req.put("demand_past_weather_hourly", fetchRawAsosItems("108", startDt, endDt));

        // ── 수요 단기예보 (강남구 nx=61, ny=125) ──────────────────
        req.put("demand_forecast_short_term_hourly", fetchRawForecastItems(61, 125, baseDate, targetDate));

        // ── PV 과거 발전량 (stationId=0 기준 50kW PV) ────────────
        List<Map<String, Object>> pvPast = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            if (s.getStationId() == null || s.getStationId() != 0) continue;
            ZonedDateTime st = s.getRecordedAt().atZone(KST);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("tm",         st.format(iso));
            item.put("slot_start", st.format(iso));
            item.put("slot_end",   st.plusHours(1).format(iso));
            item.put("gen_kwh",    s.getPPv() != null ? s.getPPv() / 1000.0 : 0.0);
            pvPast.add(item);
        }
        req.put("pv_past_generation_hourly", pvPast);

        // ── PV 과거 날씨 (ASOS 129 서산) ─────────────────────────
        req.put("pv_past_weather_hourly", fetchRawAsosItems("129", startDt, endDt));

        // ── PV 단기예보 (서산 nx=68, ny=100) ─────────────────────
        req.put("pv_forecast_short_term_hourly", fetchRawForecastItems(68, 100, baseDate, targetDate));

        // ── 충전소 현재 상태 (MQTT) ───────────────────────────────
        req.put("station_current_states", buildRawStationStates(allStations, callTime, iso));

        // ── TOU 전기요금 (D+1 24슬롯) ─────────────────────────────
        List<Map<String, Object>> tou = new ArrayList<>();
        for (int slot = 0; slot < 24; slot++) {
            ZonedDateTime st = targetDate.atTime(slot, 0).atZone(KST);
            String level; double price;
            if (slot >= 23 || slot < 9)                              { level = "off_peak";  price = 93.3;  }
            else if ((slot >= 10 && slot < 12) || (slot >= 13 && slot < 17)) { level = "on_peak";   price = 229.5; }
            else                                                      { level = "mid_peak";  price = 146.9; }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot", slot);
            row.put("time_start", st.format(iso));
            row.put("time_end",   st.plusHours(1).format(iso));
            row.put("tou_level",  level);
            row.put("price_krw_per_kwh", price);
            tou.add(row);
        }
        req.put("tou_price_hourly", tou);

        // ── 계통/ESS/토폴로지 (고정값) ───────────────────────────
        req.put("grid_constraints", Map.of(
                "cluster_grid_limit_kw", 500.0,
                "station_grid_limit_kw", 120.0,
                "peak_limit_kw",         500.0));

        req.put("ess_constraints", Map.of(
                "ess_capacity_kwh_per_station", 100.0,
                "ess_min_soc",           0.1,
                "ess_max_soc",           0.9,
                "ess_max_charge_kw",     50.0,
                "ess_max_discharge_kw",  50.0,
                "round_trip_efficiency", 0.85));

        List<String> stationOrder = allStations.stream().map(ChargingStation::getName).toList();
        req.put("transfer_topology", Map.of(
                "transfer_enabled", true,
                "station_order", stationOrder,
                "adjacency_matrix_5x5", List.of(
                        List.of(0,1,1,0,0), List.of(1,0,1,1,0), List.of(1,1,0,1,1),
                        List.of(0,1,1,0,1), List.of(0,0,1,1,0)),
                "transfer_capacity_kw_matrix_5x5", List.of(
                        List.of(0,50,50,0,0), List.of(50,0,50,50,0), List.of(50,50,0,50,50),
                        List.of(0,50,50,0,50), List.of(0,0,50,50,0)),
                "transfer_loss_rate_matrix_5x5", List.of(
                        List.of(0.0,0.02,0.02,0.0,0.0), List.of(0.02,0.0,0.02,0.02,0.0),
                        List.of(0.02,0.02,0.0,0.02,0.02), List.of(0.0,0.02,0.02,0.0,0.02),
                        List.of(0.0,0.0,0.02,0.02,0.0))));

        return req;
    }

    private List<Map<String, Object>> fetchRawAsosItems(String stnIds, String startDt, String endDt) {
        String url = UriComponentsBuilder
                .fromUriString("https://apis.data.go.kr/1360000/AsosHourlyInfoService/getWthrDataList")
                .queryParam("serviceKey", asosKey)
                .queryParam("numOfRows", 300)
                .queryParam("pageNo", 1)
                .queryParam("dataType", "JSON")
                .queryParam("dataCd", "ASOS")
                .queryParam("dateCd", "HR")
                .queryParam("startDt", startDt)
                .queryParam("startHh", "00")
                .queryParam("endDt", endDt)
                .queryParam("endHh", "22")
                .queryParam("stnIds", stnIds)
                .build(true)
                .toUriString();

        try {
            String json = restTemplate.getForObject(url, String.class);
            JsonNode items = objectMapper.readTree(json)
                    .path("response").path("body").path("items").path("item");
            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode n : items) {
                result.add(objectMapper.convertValue(n, Map.class));
            }
            log.info("[ASOS raw] stnIds={} → {}건", stnIds, result.size());
            return result;
        } catch (Exception e) {
            log.warn("[ASOS raw 실패] stnIds={} : {}", stnIds, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<Map<String, Object>> fetchRawForecastItems(int nx, int ny, String baseDate, LocalDate targetDate) {
        String baseTime = computeLatestBaseTime();
        log.info("[단기예보 raw] nx={},ny={} base_date={} base_time={}", nx, ny, baseDate, baseTime);

        String url = UriComponentsBuilder
                .fromUriString("https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst")
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1500)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .queryParam("authKey", forecastKey)
                .build(true)
                .toUriString();

        try {
            String json = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(json);

            String rc  = root.path("response").path("header").path("resultCode").asText();
            String msg = root.path("response").path("header").path("resultMsg").asText();
            if (!"00".equals(rc)) {
                log.warn("[단기예보 raw] nx={},ny={} resultCode={} msg={}", nx, ny, rc, msg);
                return new ArrayList<>();
            }

            JsonNode items = root.path("response").path("body").path("items").path("item");
            DateTimeFormatter dateFmt = DateTimeFormatter.BASIC_ISO_DATE;
            LocalDate targetEnd = targetDate.plusDays(1); // D+2

            List<Map<String, Object>> result = new ArrayList<>();
            for (JsonNode n : items) {
                String fcstDate = n.path("fcstDate").asText();
                try {
                    LocalDate day = LocalDate.parse(fcstDate, dateFmt);
                    if (day.isBefore(targetDate) || day.isAfter(targetEnd)) continue;
                } catch (Exception ignored) { continue; }
                result.add(objectMapper.convertValue(n, Map.class));
            }
            log.info("[단기예보 raw] nx={},ny={} → {}건", nx, ny, result.size());
            return result;
        } catch (Exception e) {
            log.warn("[단기예보 raw 실패] nx={},ny={} : {}", nx, ny, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 현재 시각 기준으로 조회 가능한 가장 최근 단기예보 base_time 반환.
     * 단기예보는 02, 05, 08, 11, 14, 17, 20, 23시 발표 후 약 10분 뒤 조회 가능.
     */
    private String computeLatestBaseTime() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        int totalMinutes = now.getHour() * 60 + now.getMinute();
        // (발표시각 * 60 + 10분) 이 지났으면 해당 base_time 사용 가능
        int[][] schedule = {{2,10},{5,10},{8,10},{11,10},{14,10},{17,10},{20,10},{23,10}};
        String[] codes   = {"0200","0500","0800","1100","1400","1700","2000","2300"};
        String result = "2300"; // 자정~02:10 사이: 전날 23:00 발표분
        for (int i = 0; i < schedule.length; i++) {
            if (totalMinutes >= schedule[i][0] * 60 + schedule[i][1]) {
                result = codes[i];
            }
        }
        return result;
    }

    private List<Map<String, Object>> buildRawStationStates(
            List<ChargingStation> allStations, ZonedDateTime callTime, DateTimeFormatter iso) {

        MqttPayloadDto latest = dataProcessingService.getLatestData();
        String ts = callTime.format(iso);
        List<Map<String, Object>> states = new ArrayList<>();

        for (int i = 0; i < allStations.size(); i++) {
            ChargingStation station = allStations.get(i);
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("station_id",   i);
            state.put("station_name", station.getName());
            state.put("timestamp",    ts);
            state.put("error_code",   0);

            boolean hasMqtt = latest != null && latest.getStations() != null
                    && i < latest.getStations().size();

            if (hasMqtt) {
                MqttStationDto m = latest.getStations().get(i);
                state.put("is_physical", m.getHeader().isPhysical());
                state.put("ess_soc", m.getPayload().getStateOfCharge().getSoc());

                List<Map<String, Object>> chargers = new ArrayList<>();
                for (MqttChargerStatusDto cs : m.getPayload().getChargerStatus()) {
                    Map<String, Object> cd = new LinkedHashMap<>();
                    cd.put("charger_id",       String.format("ch-%03d", cs.getChargerId()));
                    cd.put("charger_type",     "fast");
                    cd.put("rated_power_kw",   100.0);
                    cd.put("is_active",        cs.isHasDemand());
                    cd.put("current_power_kw", cs.isHasDemand() ? 7.0 : 0.0);
                    chargers.add(cd);
                }
                state.put("charger_count", chargers.size());
                state.put("chargers", chargers);
            } else {
                state.put("is_physical",   true);
                state.put("ess_soc",       0.5);
                state.put("charger_count", 0);
                state.put("chargers",      new ArrayList<>());
            }
            states.add(state);
        }
        return states;
    }

    // ─────────────────────────────────────────────────────────────
    // 기상청 API 연동
    // ─────────────────────────────────────────────────────────────

    /**
     * ASOS 시간별 관측자료 API 호출 후 PastWeatherItemDto 목록 반환
     * stnIds: 108=서울(수요), 129=서산(PV)
     * 시간 범위: startDt 00:00 ~ endDt 22:00 (191개)
     */
    private List<PastWeatherItemDto> fetchAsosWeather(
            String stnIds, String startDt, String endDt, DateTimeFormatter iso) {

        String url = UriComponentsBuilder
                .fromUriString("https://apis.data.go.kr/1360000/AsosHourlyInfoService/getWthrDataList")
                .queryParam("serviceKey", asosKey)
                .queryParam("numOfRows", 300)
                .queryParam("pageNo", 1)
                .queryParam("dataType", "JSON")
                .queryParam("dataCd", "ASOS")
                .queryParam("dateCd", "HR")
                .queryParam("startDt", startDt)
                .queryParam("startHh", "00")
                .queryParam("endDt", endDt)
                .queryParam("endHh", "22")
                .queryParam("stnIds", stnIds)
                .build(true)
                .toUriString();

        try {
            String json = restTemplate.getForObject(url, String.class);
            return parseAsosToWeatherItems(json, iso);
        } catch (Exception e) {
            log.warn("[ASOS API 실패] stnIds={} : {}", stnIds, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<PastWeatherItemDto> parseAsosToWeatherItems(String json, DateTimeFormatter iso) {
        try {
            JsonNode root = objectMapper.readTree(json);

            String resultCode = root.path("response").path("header").path("resultCode").asText();
            if (!"00".equals(resultCode)) {
                log.warn("[ASOS API] resultCode={}", resultCode);
                return new ArrayList<>();
            }

            JsonNode items = root.path("response").path("body").path("items").path("item");
            DateTimeFormatter asosFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            List<PastWeatherItemDto> result = new ArrayList<>();

            for (JsonNode node : items) {
                PastWeatherItemDto item = new PastWeatherItemDto();
                String tmRaw = node.path("tm").asText("");
                try {
                    LocalDateTime ldt = LocalDateTime.parse(tmRaw, asosFmt);
                    item.setTm(ldt.atZone(KST).format(iso));
                } catch (Exception ex) {
                    item.setTm(tmRaw);
                }
                item.setTa(parseDoubleNode(node, "ta"));
                item.setRn(parseDoubleNode(node, "rn"));
                item.setWs(parseDoubleNode(node, "ws"));
                item.setWd(parseDoubleNode(node, "wd"));
                item.setHm(parseDoubleNode(node, "hm"));
                item.setPa(parseDoubleNode(node, "pa"));
                item.setPs(parseDoubleNode(node, "ps"));
                item.setSs(parseDoubleNode(node, "ss"));
                item.setIcsr(parseDoubleNode(node, "icsr"));
                item.setDsnw(parseDoubleNode(node, "dsnw"));
                item.setHr3Fhsc(parseDoubleNode(node, "hr3Fhsc"));
                item.setDc10Tca(parseDoubleNode(node, "dc10Tca"));
                result.add(item);
            }
            return result;
        } catch (Exception e) {
            log.warn("[ASOS 파싱 실패] : {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 기상청 단기예보 API 호출 후 ForecastWeatherItemDto 목록 반환
     * nx/ny: 강남구(61,125), 서산(68,100)
     * base_time: 2000 (22:10 호출 시 이용 가능한 최신 예보)
     * 필터: targetDate(D+1) ~ targetDate+1일(D+2) 범위 48개
     */
    private List<ForecastWeatherItemDto> fetchVilageFcst(
            int nx, int ny, String baseDate, LocalDate targetDate, DateTimeFormatter iso) {

        String url = UriComponentsBuilder
                .fromUriString("https://apihub.kma.go.kr/api/typ02/openApi/VilageFcstInfoService_2.0/getVilageFcst")
                .queryParam("pageNo", 1)
                .queryParam("numOfRows", 1500)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", "2000")
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .queryParam("authKey", forecastKey)
                .build(true)
                .toUriString();

        try {
            String json = restTemplate.getForObject(url, String.class);
            return parseVilageFcstItems(json, targetDate, iso);
        } catch (Exception e) {
            log.warn("[단기예보 API 실패] nx={}, ny={} : {}", nx, ny, e.getMessage());
            return new ArrayList<>();
        }
    }

    private List<ForecastWeatherItemDto> parseVilageFcstItems(
            String json, LocalDate targetDate, DateTimeFormatter iso) {
        try {
            JsonNode root = objectMapper.readTree(json);

            String resultCode = root.path("response").path("header").path("resultCode").asText();
            if (!"00".equals(resultCode)) {
                log.warn("[단기예보 API] resultCode={}, msg={}",
                        resultCode, root.path("response").path("header").path("resultMsg").asText());
                return new ArrayList<>();
            }

            JsonNode items = root.path("response").path("body").path("items").path("item");

            LocalDate targetEnd = targetDate.plusDays(1); // D+2
            DateTimeFormatter dateFmt = DateTimeFormatter.BASIC_ISO_DATE;
            DateTimeFormatter fcstDtFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

            // pivot: "fcstDate_fcstTime" → ForecastWeatherItemDto
            Map<String, ForecastWeatherItemDto> pivot = new LinkedHashMap<>();
            Map<String, Double> tmnMap = new HashMap<>();
            Map<String, Double> tmxMap = new HashMap<>();

            for (JsonNode node : items) {
                String fcstDate = node.path("fcstDate").asText();
                String fcstTime = node.path("fcstTime").asText();
                String category = node.path("category").asText();
                String value    = node.path("fcstValue").asText();

                // D+1, D+2 범위만 사용
                LocalDate fcstDay = LocalDate.parse(fcstDate, dateFmt);
                if (fcstDay.isBefore(targetDate) || fcstDay.isAfter(targetEnd)) continue;

                if ("TMN".equals(category)) {
                    try { tmnMap.put(fcstDate, Double.parseDouble(value)); } catch (Exception ignored) {}
                    continue;
                }
                if ("TMX".equals(category)) {
                    try { tmxMap.put(fcstDate, Double.parseDouble(value)); } catch (Exception ignored) {}
                    continue;
                }

                String key = fcstDate + "_" + fcstTime;
                pivot.computeIfAbsent(key, k -> {
                    ForecastWeatherItemDto dto = new ForecastWeatherItemDto();
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(fcstDate + fcstTime, fcstDtFmt);
                        dto.setTmef(ldt.atZone(KST).format(iso));
                    } catch (Exception ex) {
                        dto.setTmef(fcstDate + "T" + fcstTime);
                    }
                    return dto;
                });

                applyForecastCategory(pivot.get(key), category, value);
            }

            // TMN/TMX를 해당 날짜의 모든 슬롯에 주입
            List<ForecastWeatherItemDto> result = new ArrayList<>(pivot.values());
            for (ForecastWeatherItemDto dto : result) {
                String tmef = dto.getTmef();
                if (tmef != null && tmef.length() >= 10) {
                    String dateKey = tmef.substring(0, 10).replace("-", "");
                    if (tmnMap.containsKey(dateKey)) dto.setTmn(tmnMap.get(dateKey));
                    if (tmxMap.containsKey(dateKey)) dto.setTmx(tmxMap.get(dateKey));
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("[단기예보 파싱 실패] : {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void applyForecastCategory(ForecastWeatherItemDto dto, String category, String value) {
        try {
            switch (category) {
                case "TMP" -> dto.setTmp(Double.parseDouble(value));
                case "POP" -> dto.setPop(Double.parseDouble(value));
                case "PTY" -> dto.setPty(Integer.parseInt(value));
                case "PCP" -> { try { dto.setPcp(Double.parseDouble(value)); } catch (Exception e) { dto.setPcp(value); } }
                case "SNO" -> { try { dto.setSno(Double.parseDouble(value)); } catch (Exception e) { dto.setSno(value); } }
                case "REH" -> dto.setReh(Double.parseDouble(value));
                case "SKY" -> dto.setSky(Integer.parseInt(value));
                case "WSD" -> dto.setWsd(Double.parseDouble(value));
                case "VEC" -> dto.setVec(Double.parseDouble(value));
                case "UUU" -> dto.setUuu(Double.parseDouble(value));
                case "VVV" -> dto.setVvv(Double.parseDouble(value));
            }
        } catch (Exception ignored) {}
    }

    private Double parseDoubleNode(JsonNode node, String field) {
        JsonNode n = node.path(field);
        if (n.isMissingNode() || n.isNull()) return null;
        String s = n.asText("").trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    }

    public String callAsosApi() {

        String url = UriComponentsBuilder
                .fromUriString("https://apis.data.go.kr/1360000/AsosHourlyInfoService/getWthrDataList")
                .queryParam("serviceKey", "YOUR_KEY")
                .queryParam("numOfRows", "999")
                .queryParam("pageNo", "1")
                .queryParam("dataType", "JSON")
                .queryParam("dataCd", "ASOS")
                .queryParam("dateCd", "HR")
                .queryParam("startDt", "20260424")
                .queryParam("startHh", "00")
                .queryParam("endDt", "20260501")
                .queryParam("endHh", "23")
                .queryParam("stnIds", "108")
                .build(true)
                .toUriString();

        RestTemplate restTemplate = new RestTemplate();
        return restTemplate.getForObject(url, String.class);
    }


    public List<Map<String, String>> parseAsosJson(String json) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);

        JsonNode items = root
                .path("response")
                .path("body")
                .path("items")
                .path("item");

        List<Map<String, String>> result = new ArrayList<>();

        for (JsonNode node : items) {

            Map<String, String> row = new HashMap<>();

            row.put("tm", node.path("tm").asText());
            row.put("ta", node.path("ta").asText());
            row.put("hm", node.path("hm").asText());
            row.put("ws", node.path("ws").asText());
            row.put("wd", node.path("wd").asText());
            row.put("rn", node.path("rn").asText());
            row.put("si", node.path("icsr").asText()); // 🔥 일사량

            result.add(row);
        }

        return result;
    }

    public List<Map<String, String>> getHistoryRaw() {

        try {
            String json = callAsosApi();
            return parseAsosJson(json);

        } catch (Exception e) {
            throw new RuntimeException("ASOS API 호출 실패", e);
        }
    }
}
