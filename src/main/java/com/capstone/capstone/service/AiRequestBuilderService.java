package com.capstone.capstone.service;

import com.capstone.capstone.entity.*;
import com.capstone.capstone.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
public class AiRequestBuilderService {

    private final WeatherApiService weatherApiService;
    private final ChargingStationRepository stationRepository;
    private final StationStateRepository stationStateRepository;
    private final ChargerRepository chargerRepository;
    private final ChargerStateRepository chargerStateRepository;
    private final ConstraintsRepository constraintsRepository;
    private final HourlySnapshotRepository snapshotRepository;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // ─── 원래 파이프라인: DB + 기상청 API로 AI 요청 직접 생성 ─────────────

    @Transactional(readOnly = true)
    public Map<String, Object> buildRawAiRequest() {
        LocalDate today      = LocalDate.now();
        LocalDate targetDate = today.plusDays(1);
        ZonedDateTime callTime = ZonedDateTime.now(KST);
        DateTimeFormatter iso     = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        String ts = callTime.format(iso);

        // ── 1. 스테이션 목록 (DB) ──────────────────────────────────────
        List<ChargingStation> allStations = stationRepository.findAll();
        allStations.sort(Comparator.comparing(ChargingStation::getId));

        // ── 2. 제약조건 (DB) ───────────────────────────────────────────
        Map<Long, Constraints> conMap = buildConstraintsMap();
        Constraints refCon = conMap.values().stream().findFirst().orElse(null);

        double socMin    = val(refCon, c -> c.getSocMin(),             0.1);
        double socMax    = val(refCon, c -> c.getSocMax(),             0.9);
        double maxCharge = val(refCon, c -> c.getEssMaxCharge(),      50.0);
        double maxDisch  = val(refCon, c -> c.getEssMaxDischarge(),   50.0);
        double essCap    = val(refCon, c -> c.getEssCapacityKwh(),   100.0);
        double gridLim   = val(refCon, c -> c.getGridImportLimitKw(),120.0);

        Map<String, Object> req = new LinkedHashMap<>();

        // ── 메타 ───────────────────────────────────────────────────────
        req.put("request_id", String.format("backend-schedule-request-%s-%s-0001",
                today.format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                callTime.format(DateTimeFormatter.ofPattern("HHmmss"))));
        req.put("request_timestamp",    ts);
        req.put("schedule_target_date", targetDate.toString());
        req.put("schedule_horizon_hours", 24);
        req.put("schedule_mode", "day-ahead");
        req.put("timezone", "Asia/Seoul");

        // ── target_window ──────────────────────────────────────────────
        Map<String, Object> window = new LinkedHashMap<>();
        window.put("start",           targetDate.atStartOfDay(KST).format(iso));
        window.put("end",             targetDate.plusDays(1).atStartOfDay(KST).format(iso));
        window.put("slot_unit",       "hour");
        window.put("slot_count",      24);
        window.put("slot_definition", "slot 0 = 00:00~01:00, slot 23 = 23:00~24:00");
        req.put("target_window", window);

        // ── cluster_state ──────────────────────────────────────────────
        // station_index=0 : 물리(LH강남힐스테이트), 나머지 : 시뮬레이션
        List<Integer> physicalIds  = new ArrayList<>();
        List<Integer> simulatedIds = new ArrayList<>();
        for (ChargingStation s : allStations) {
            if (Integer.valueOf(0).equals(s.getStationIndex())) physicalIds.add(s.getStationIndex());
            else if (s.getStationIndex() != null)               simulatedIds.add(s.getStationIndex());
        }
        Map<String, Object> clusterState = new LinkedHashMap<>();
        clusterState.put("station_count",        allStations.size());
        clusterState.put("physical_station_ids",  physicalIds);
        clusterState.put("simulated_station_ids", simulatedIds);
        clusterState.put("grid_limit_kw",        200.0);
        req.put("cluster_state", clusterState);

        // ── constraints (cluster-level) ────────────────────────────────
        Map<String, Object> constraints = new LinkedHashMap<>();
        constraints.put("soc_min", socMin);
        constraints.put("soc_max", socMax);
        req.put("constraints", constraints);

        // ── transfer ───────────────────────────────────────────────────
        Map<String, Object> transfer = new LinkedHashMap<>();
        transfer.put("enabled",     true);
        transfer.put("capacity_kw", 30.0);
        transfer.put("loss_rate",   0.03);
        req.put("transfer", transfer);

        // ── grid_constraints ───────────────────────────────────────────
        Map<String, Object> gridConstraints = new LinkedHashMap<>();
        gridConstraints.put("cluster_grid_limit_kw", 200.0);
        gridConstraints.put("station_grid_limit_kw", gridLim);
        gridConstraints.put("peak_limit_kw",         200.0);
        req.put("grid_constraints", gridConstraints);

        // ── ess_constraints ────────────────────────────────────────────
        Map<String, Object> essConstraints = new LinkedHashMap<>();
        essConstraints.put("ess_capacity_kwh_per_station", essCap);
        essConstraints.put("ess_min_soc",          socMin);
        essConstraints.put("ess_max_soc",          socMax);
        essConstraints.put("ess_max_charge_kw",    maxCharge);
        essConstraints.put("ess_max_discharge_kw", maxDisch);
        essConstraints.put("round_trip_efficiency", 0.9025);
        req.put("ess_constraints", essConstraints);

        // ── transfer_topology ──────────────────────────────────────────
        List<String> stationOrder = allStations.stream().map(ChargingStation::getName).toList();
        Map<String, Object> topology = new LinkedHashMap<>();
        topology.put("transfer_enabled", true);
        topology.put("station_order", stationOrder);
        topology.put("adjacency_matrix_5x5", List.of(
                List.of(0,1,1,0,0), List.of(1,0,1,1,0), List.of(1,1,0,1,1),
                List.of(0,1,1,0,1), List.of(0,0,1,1,0)));
        topology.put("transfer_capacity_kw_matrix_5x5", List.of(
                List.of(0,30,30,0,0),  List.of(30,0,30,30,0),  List.of(30,30,0,30,30),
                List.of(0,30,30,0,30), List.of(0,0,30,30,0)));
        topology.put("transfer_loss_rate_matrix_5x5", List.of(
                List.of(0.0,0.03,0.03,0.0,0.0), List.of(0.03,0.0,0.03,0.03,0.0),
                List.of(0.03,0.03,0.0,0.03,0.03), List.of(0.0,0.03,0.03,0.0,0.03),
                List.of(0.0,0.0,0.03,0.03,0.0)));
        req.put("transfer_topology", topology);

        // ── TOU 요금 ───────────────────────────────────────────────────
        Map<String, Double> touPriceMap = new LinkedHashMap<>();
        for (int slot = 0; slot < 24; slot++) touPriceMap.put(String.valueOf(slot), getTouPrice(slot));
        req.put("tou_price_krw_per_kwh", touPriceMap);

        List<Map<String, Object>> tou = new ArrayList<>();
        for (int slot = 0; slot < 24; slot++) {
            ZonedDateTime st = targetDate.atTime(slot, 0).atZone(KST);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("slot",              slot);
            row.put("hour",              slot);
            row.put("slot_label",        String.format("%02d:00~%02d:00", slot, slot + 1));
            row.put("time_start",        st.format(iso));
            row.put("time_end",          st.plusHours(1).format(iso));
            row.put("tou_level",         getTouLevel(slot));
            row.put("price_krw_per_kwh", getTouPrice(slot));
            tou.add(row);
        }
        req.put("tou_price_hourly", tou);

        // ── stations (DB 기반 현재 상태 + 충전기 스펙) ─────────────────
        List<Map<String, Object>> stationList = buildStationListFromDb(allStations, conMap, ts);
        req.put("stations",               stationList);
        req.put("station_current_states", stationList);

        // ── 과거 수요/발전 이력 (hourly_snapshot, 최근 7일) ────────────
        LocalDateTime snapshotFrom = today.minusDays(7).atStartOfDay();
        LocalDateTime snapshotTo   = LocalDateTime.now();
        List<HourlySnapshot> snapshots = snapshotRepository
                .findByRecordedAtBetweenOrderByRecordedAt(snapshotFrom, snapshotTo);
        req.put("demand_past_demand_hourly", buildDemandHourly(snapshots, iso));
        req.put("pv_past_generation_hourly", buildPvHourly(snapshots, iso));

        // ── 날씨 데이터 (기상청 API) ───────────────────────────────────
        String asosStart = today.minusDays(7).format(dateFmt);
        String asosEnd   = today.format(dateFmt);
        List<Map<String, Object>> asosRaw = weatherApiService.fetchRawAsosItems("108", asosStart, asosEnd);
        req.put("demand_past_weather_hourly", weatherApiService.buildAsosWeatherHourly(asosRaw, "ASOS_108", "Seoul/Gangnam",        iso));
        req.put("pv_past_weather_hourly",     weatherApiService.buildAsosWeatherHourly(asosRaw, "ASOS_108", "Seoul/Gangnam", iso));

        List<Map<String, Object>> forecastRaw =
                weatherApiService.fetchRawForecastItems(61, 125, today.format(dateFmt), targetDate);
        req.put("demand_forecast_short_term_hourly",  buildDemandForecastHourly(allStations, snapshots, targetDate, iso));
        req.put("pv_forecast_short_term_hourly",      buildPvForecastHourly(allStations, snapshots, targetDate, iso));
        req.put("weather_forecast_short_term_hourly", weatherApiService.buildWeatherForecastHourly(forecastRaw));

        log.info("[AI 요청 빌드 완료] target={}, stations={}, snapshots={}",
                targetDate, allStations.size(), snapshots.size());
        return req;
    }

    // ─── 스테이션 목록 빌드 (DB: station_state, power_metrics, charger, charger_state) ───

    private List<Map<String, Object>> buildStationListFromDb(
            List<ChargingStation> allStations,
            Map<Long, Constraints> conMap,
            String ts) {

        List<Map<String, Object>> result = new ArrayList<>();

        for (ChargingStation station : allStations) {
            int idx = station.getStationIndex() != null ? station.getStationIndex() : result.size();
            boolean isPhysical = Integer.valueOf(0).equals(station.getStationIndex());

            // station_state + power_metrics (DB)
            StationState ss = stationStateRepository.findByStationId(station.getId()).orElse(null);
            PowerMetrics  pm = ss != null ? ss.getPowerMetrics() : null;

            // per-station 제약조건 (DB)
            Constraints con = conMap.get(station.getId());
            double stationEssCap   = con != null && con.getEssCapacityKwh() != null ? con.getEssCapacityKwh()    : 100.0;
            double stationMaxChg   = con != null && con.getEssMaxCharge()   != null ? con.getEssMaxCharge()      : 50.0;
            double stationMaxDsch  = con != null && con.getEssMaxDischarge()!= null ? con.getEssMaxDischarge()   : 50.0;

            Map<String, Object> s = new LinkedHashMap<>();
            s.put("station_id",   idx);
            s.put("station_name", station.getName());
            s.put("is_physical",  isPhysical);

            // current_state
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("timestamp", ts);
            state.put("soc",       ss != null && ss.getSoc()  != null ? ss.getSoc()              : 0.5);
            state.put("ess_soc",   ss != null && ss.getSoc()  != null ? ss.getSoc()              : 0.5);
            state.put("p_pv_kw",   pm != null && pm.getPPv()  != null ? pm.getPPv()  / 1000.0    : 0.0);
            state.put("p_load_kw", pm != null && pm.getPLoad()!= null ? pm.getPLoad() / 1000.0   : 0.0);
            state.put("p_grid_kw", pm != null && pm.getPGrid()!= null ? pm.getPGrid() / 1000.0   : 0.0);
            state.put("error_code", 0);
            s.put("current_state", state);

            s.put("ess_capacity_kwh",       stationEssCap);
            s.put("ess_max_charge_kw",       stationMaxChg);
            s.put("ess_max_discharge_kw",    stationMaxDsch);
            s.put("ess_charge_efficiency",    0.95);
            s.put("ess_discharge_efficiency", 0.95);

            // chargers (DB: charger + charger_state)
            s.put("chargers", buildChargerList(station, ss));

            result.add(s);
        }
        return result;
    }

    private List<Map<String, Object>> buildChargerList(ChargingStation station, StationState ss) {
        List<Charger> chargers = chargerRepository.findByStationIdOrderByChargerIndex(station.getId());
        List<Map<String, Object>> list = new ArrayList<>();

        for (Charger charger : chargers) {
            ChargerState cs = ss != null
                    ? chargerStateRepository
                            .findByStationStateIdAndChargerId(ss.getId(), charger.getId())
                            .orElse(null)
                    : null;

            boolean isActive  = cs != null && Boolean.TRUE.equals(cs.getIsActive());
            boolean hasDemand = isActive;
            double  ratedPow  = charger.getRatedPowerKw() != null ? charger.getRatedPowerKw() : 0.0;
            double  demand    = resolveChargerDemand(cs, isActive, ratedPow);
            String  type      = charger.getChargerType() != null ? charger.getChargerType() : "slow";

            Map<String, Object> c = new LinkedHashMap<>();
            c.put("charger_id",       charger.getChargerIndex());
            c.put("type",             type);
            c.put("charger_type",     type);
            c.put("rated_power_kw",   ratedPow);
            c.put("is_active",        isActive);
            c.put("has_demand",       hasDemand);
            c.put("power_demand_kw",  demand);
            c.put("current_power_kw", isActive ? demand : 0.0);
            list.add(c);
        }
        return list;
    }

    // charger_state.power_demand 우선, 없으면 활성 시 정격 출력 사용
    private double resolveChargerDemand(ChargerState cs, boolean isActive, double ratedPow) {
        if (cs != null && cs.getPowerDemand() != null && cs.getPowerDemand() > 0) {
            return cs.getPowerDemand();
        }
        return isActive ? ratedPow : 0.0;
    }

    // ─── 수요/PV 예측 (hourly_snapshot 7일 평균) ──────────────────────────

    @Transactional(readOnly = true)
    public List<Map<String, Object>> buildDemandForecast(LocalDate targetDate) {
        List<ChargingStation> stations = stationRepository.findAll();
        stations.sort(Comparator.comparing(ChargingStation::getId));
        List<HourlySnapshot> snapshots = snapshotRepository.findByRecordedAtBetweenOrderByRecordedAt(
                targetDate.minusDays(7).atStartOfDay(), LocalDateTime.now());
        DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        return buildDemandForecastHourly(stations, snapshots, targetDate, iso);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> buildPvForecast(LocalDate targetDate) {
        List<ChargingStation> stations = stationRepository.findAll();
        stations.sort(Comparator.comparing(ChargingStation::getId));
        List<HourlySnapshot> snapshots = snapshotRepository.findByRecordedAtBetweenOrderByRecordedAt(
                targetDate.minusDays(7).atStartOfDay(), LocalDateTime.now());
        DateTimeFormatter iso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
        return buildPvForecastHourly(stations, snapshots, targetDate, iso);
    }

    private List<Map<String, Object>> buildDemandForecastHourly(
            List<ChargingStation> allStations, List<HourlySnapshot> snapshots,
            LocalDate targetDate, DateTimeFormatter iso) {

        DateTimeFormatter tmFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        Map<Integer, Map<Integer, List<Double>>> avgMap = new HashMap<>();
        for (HourlySnapshot s : snapshots) {
            if (s.getStation() == null || s.getPLoad() == null || s.getStation().getStationIndex() == null) continue;
            int stIdx = s.getStation().getStationIndex();
            int hour  = s.getRecordedAt().getHour();
            avgMap.computeIfAbsent(stIdx, k -> new HashMap<>())
                  .computeIfAbsent(hour, k -> new ArrayList<>())
                  .add(s.getPLoad() / 1000.0);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChargingStation station : allStations) {
            int idx = station.getStationIndex() != null ? station.getStationIndex() : 0;
            Map<Integer, List<Double>> hourMap = avgMap.getOrDefault(idx, Collections.emptyMap());
            for (int hour = 0; hour < 24; hour++) {
                ZonedDateTime slotTime = targetDate.atTime(hour, 0).atZone(KST);
                List<Double> vals = hourMap.getOrDefault(hour, Collections.emptyList());
                double avg = vals.isEmpty() ? 0.0 : vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("station_id",   idx);
                row.put("station_name", station.getName());
                row.put("timestamp",    slotTime.format(iso));
                row.put("tm",          slotTime.format(tmFmt));
                row.put("slot_start",  hour);
                row.put("slot_end",    hour + 1);
                row.put("demand_kwh",  avg);
                list.add(row);
            }
        }
        return list;
    }

    private List<Map<String, Object>> buildPvForecastHourly(
            List<ChargingStation> allStations, List<HourlySnapshot> snapshots,
            LocalDate targetDate, DateTimeFormatter iso) {

        DateTimeFormatter tmFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        Map<Integer, Map<Integer, List<Double>>> avgMap = new HashMap<>();
        for (HourlySnapshot s : snapshots) {
            if (s.getStation() == null || s.getPPv() == null || s.getStation().getStationIndex() == null) continue;
            int stIdx = s.getStation().getStationIndex();
            int hour  = s.getRecordedAt().getHour();
            avgMap.computeIfAbsent(stIdx, k -> new HashMap<>())
                  .computeIfAbsent(hour, k -> new ArrayList<>())
                  .add(s.getPPv() / 1000.0);
        }
        List<Map<String, Object>> list = new ArrayList<>();
        for (ChargingStation station : allStations) {
            int idx = station.getStationIndex() != null ? station.getStationIndex() : 0;
            Map<Integer, List<Double>> hourMap = avgMap.getOrDefault(idx, Collections.emptyMap());
            for (int hour = 0; hour < 24; hour++) {
                ZonedDateTime slotTime = targetDate.atTime(hour, 0).atZone(KST);
                List<Double> vals = hourMap.getOrDefault(hour, Collections.emptyList());
                double avg = vals.isEmpty() ? 0.0 : vals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("station_id",        idx);
                row.put("station_name",      station.getName());
                row.put("timestamp",         slotTime.format(iso));
                row.put("tm",               slotTime.format(tmFmt));
                row.put("slot_start",       hour);
                row.put("slot_end",         hour + 1);
                row.put("gen_kwh",          avg);
                row.put("pv_generation_kwh", avg);
                list.add(row);
            }
        }
        return list;
    }

    // ─── 과거 수요/발전 이력 ─────────────────────────────────────────────

    private List<Map<String, Object>> buildDemandHourly(List<HourlySnapshot> snapshots, DateTimeFormatter iso) {
        DateTimeFormatter tmFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        List<Map<String, Object>> list = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            if (s.getStation() == null || s.getPLoad() == null) continue;
            ZonedDateTime slotStart = s.getRecordedAt().atZone(KST);
            int hour = slotStart.getHour();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("station_id",   s.getStation().getStationIndex());
            row.put("station_name", s.getStation().getName());
            row.put("timestamp",    slotStart.format(iso));
            row.put("tm",          slotStart.format(tmFmt));
            row.put("slot_start",  hour);
            row.put("slot_end",    hour + 1);
            row.put("demand_kwh",  s.getPLoad() / 1000.0);
            list.add(row);
        }
        return list;
    }

    private List<Map<String, Object>> buildPvHourly(List<HourlySnapshot> snapshots, DateTimeFormatter iso) {
        DateTimeFormatter tmFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
        List<Map<String, Object>> list = new ArrayList<>();
        for (HourlySnapshot s : snapshots) {
            if (s.getStation() == null || s.getPPv() == null) continue;
            ZonedDateTime slotStart = s.getRecordedAt().atZone(KST);
            int hour = slotStart.getHour();
            double genKwh = s.getPPv() / 1000.0;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("station_id",        s.getStation().getStationIndex());
            row.put("station_name",      s.getStation().getName());
            row.put("timestamp",         slotStart.format(iso));
            row.put("tm",               slotStart.format(tmFmt));
            row.put("slot_start",       hour);
            row.put("slot_end",         hour + 1);
            row.put("gen_kwh",          genKwh);
            row.put("pv_generation_kwh", genKwh);
            list.add(row);
        }
        return list;
    }

    // ─── TOU 요금 ────────────────────────────────────────────────────────

    private String getTouLevel(int slot) {
        if (slot <= 7 || slot >= 22)                    return "off_peak";
        if (slot == 11 || (slot >= 13 && slot <= 17))   return "on_peak";
        return "mid_peak";
    }

    private double getTouPrice(int slot) {
        if (slot <= 7 || slot >= 22)                    return 83.1;
        if (slot == 11 || (slot >= 13 && slot <= 17))   return 270.8;
        return 140.0;
    }

    // ─── 제약조건 맵 (stationId → Constraints) ───────────────────────────

    private Map<Long, Constraints> buildConstraintsMap() {
        Map<Long, Constraints> map = new HashMap<>();
        for (Constraints c : constraintsRepository.findAllWithStation()) {
            if (c.getStation() != null) map.put(c.getStation().getId(), c);
        }
        return map;
    }

    // ─── 함수형 null-safe getter ─────────────────────────────────────────

    @FunctionalInterface
    private interface ConstraintGetter {
        Double get(Constraints c);
    }

    private double val(Constraints c, ConstraintGetter getter, double def) {
        if (c == null) return def;
        Double v = getter.get(c);
        return v != null ? v : def;
    }
}
