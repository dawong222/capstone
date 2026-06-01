package com.capstone.capstone.service;

import com.capstone.capstone.dto.ChatHistoryItem;
import com.capstone.capstone.dto.ChatRequestDto;
import com.capstone.capstone.dto.ChatResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final WeatherApiService weatherApiService;

    @Value("${ai.llm.chat-url}")
    private String llmChatUrl;

    public ChatResponseDto chat(ChatRequestDto request) {
        String message = request.getMessage();

        if (message == null || message.isBlank()) {
            return ChatResponseDto.builder()
                    .answer("질문을 입력해 주세요.")
                    .intent("EMPTY")
                    .context(Map.of("status", "no_data", "message", "질문이 비어 있습니다."))
                    .build();
        }

        String intent = classifyIntent(message, request.getHistory());
        Map<String, Object> context = buildContext(intent, message);

        Map<String, Object> llmRequest = new LinkedHashMap<>();
        llmRequest.put("sessionId", request.getSessionId());
        llmRequest.put("question", message);
        llmRequest.put("intent", intent);
        llmRequest.put("context", context);
        llmRequest.put("history", request.getHistory() == null ? List.of() : request.getHistory());
        llmRequest.put("max_new_tokens", 420);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> httpRequest = new HttpEntity<>(llmRequest, headers);

        log.info("[LLM 챗봇 요청] intent={}, message={}", intent, message);

        String raw;
        try {
            raw = restTemplate.postForObject(llmChatUrl, httpRequest, String.class);
        } catch (Exception e) {
            log.error("[LLM 챗봇 요청 실패] {}", e.getMessage());
            return ChatResponseDto.builder()
                    .answer("LLM 서버에 연결할 수 없습니다.")
                    .intent(intent)
                    .context(context)
                    .build();
        }

        log.info("[LLM 챗봇 응답] {}", raw);

        if (raw == null) {
            return ChatResponseDto.builder()
                    .answer("응답을 받지 못했습니다.")
                    .intent(intent)
                    .context(context)
                    .build();
        }

        JsonNode llmResponse;
        try {
            llmResponse = objectMapper.readTree(raw);
        } catch (Exception e) {
            log.error("[LLM 응답 파싱 실패] {}", e.getMessage());
            return ChatResponseDto.builder()
                    .answer("응답 파싱에 실패했습니다.")
                    .intent(intent)
                    .context(context)
                    .build();
        }

        if (llmResponse.hasNonNull("answer")) {
            return ChatResponseDto.builder()
                    .answer(llmResponse.get("answer").asText())
                    .intent(intent)
                    .context(context)
                    .build();
        }

        return ChatResponseDto.builder()
                .answer("답변을 생성하지 못했습니다.")
                .intent(intent)
                .context(context)
                .build();
    }

    // ── Intent 분류 ──────────────────────────────────────────────

    private String classifyIntent(String question, List<ChatHistoryItem> history) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);

        String directIntent = classifyIntentFromText(q);
        if (!"UNKNOWN".equals(directIntent)) {
            return directIntent;
        }

        boolean isFollowUp = containsAny(q,
                "그 시간", "그시간", "그 시간대", "그시간대",
                "그 충전소", "그충전소",
                "거기", "그때", "방금", "아까",
                "그럼", "그러면", "그건", "그 값");

        if (isFollowUp && history != null && !history.isEmpty()) {
            StringBuilder historyText = new StringBuilder();
            for (ChatHistoryItem item : history) {
                if (item == null || item.getContent() == null) continue;
                historyText.append(" ").append(item.getContent().toLowerCase(Locale.ROOT));
            }
            String historyIntent = classifyIntentFromText(historyText.toString());
            if (!"UNKNOWN".equals(historyIntent)) {
                return historyIntent;
            }
        }

        return "SCHEDULE_SUMMARY";
    }

    private String classifyIntentFromText(String s) {
        if (containsAny(s, "비", "날씨", "강수", "눈", "기온", "습도")) return "WEATHER_TOMORROW";
        if (containsAny(s, "충전기", "충전 중", "충전중", "사용중", "사용 중", "몇 개", "몇개")) return "CURRENT_CHARGER_STATUS";
        if (containsAny(s, "ess", "soc", "배터리", "충전율", "에너지저장장치")) return "CURRENT_ESS_STATUS";
        if (containsAny(s, "태양광", "pv", "발전량")) return "PV_PEAK_TOMORROW";
        if (containsAny(s, "계통", "grid", "전력 사용", "전력사용")) return "GRID_PEAK_TOMORROW";
        if (containsAny(s, "전송", "융통", "이송", "transfer")) return "TRANSFER_STATUS";
        if (containsAny(s, "수요", "피크", "가장 높", "최대")) return "DEMAND_PEAK_TOMORROW";
        if (containsAny(s, "요약", "스케줄", "스케쥴", "운영 계획", "운영계획")) return "SCHEDULE_SUMMARY";
        return "UNKNOWN";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    // ── Context DB 조회 ──────────────────────────────────────────

    private Map<String, Object> buildContext(String intent, String question) {
        return switch (intent) {
            case "DEMAND_PEAK_TOMORROW"   -> demandPeakByDate(question);
            case "CURRENT_ESS_STATUS"     -> currentEssStatus();
            case "CURRENT_CHARGER_STATUS" -> currentChargerStatus();
            case "SCHEDULE_SUMMARY"       -> scheduleSummary();
            case "TRANSFER_STATUS"        -> transferStatus();
            case "PV_PEAK_TOMORROW"       -> pvPeakTomorrow();
            case "GRID_PEAK_TOMORROW"     -> gridPeakTomorrow();
            case "WEATHER_TOMORROW"       -> weatherTomorrow();
            default -> noData("해당 질문에 대한 데이터를 찾을 수 없습니다.");
        };
    }

    private Map<String, Object> demandPeakByDate(String question) {
        LocalDate explicitDate = extractTargetDate(question);

        Long jobId;
        LocalDate targetDate;

        if (explicitDate != null) {
            targetDate = explicitDate;
            jobId = findJobIdByTargetDate(targetDate);
            if (jobId == null) {
                Map<String, Object> ctx = new LinkedHashMap<>();
                ctx.put("status", "no_data");
                ctx.put("data_type", "prediction");
                ctx.put("target_date", targetDate.toString());
                ctx.put("message", targetDate + " 스케줄링 데이터가 현재 DB에 없습니다.");
                return ctx;
            }
        } else {
            jobId = tomorrowOrLatestJobId();
            if (jobId == null) return noData("저장된 AI 스케줄링 결과가 없습니다.");

            Map<String, Object> job = jdbcTemplate.queryForMap(
                    "SELECT schedule_target_date FROM schedule_job WHERE id = ?", jobId);
            Object raw = job.get("schedule_target_date");
            targetDate = (raw instanceof java.sql.Date)
                    ? ((java.sql.Date) raw).toLocalDate()
                    : LocalDate.parse(raw.toString());
        }

        List<Map<String, Object>> clusterPeaks = getClusterDemandPeakRows(jobId);
        List<Map<String, Object>> stationPeaks = getStationDemandPeakRows(jobId);

        if (clusterPeaks.isEmpty() && stationPeaks.isEmpty())
            return noData("수요 예측 데이터가 없습니다.");

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("status", "ok");
        ctx.put("data_type", "prediction");
        ctx.put("target_date", targetDate.toString());
        ctx.put("cluster_peak_hours", clusterPeaks);
        ctx.put("station_peak_hours", stationPeaks);
        ctx.put("source_tables", List.of("schedule_job", "station_demand_forecast", "charging_station"));
        ctx.put("note", "이 값은 실제 확정 수요가 아니라 AI 수요 예측 결과입니다.");
        return ctx;
    }

    private LocalDate extractTargetDate(String question) {
        if (question == null) return null;
        Pattern p = Pattern.compile("(\\d{1,2})\\s*월\\s*(\\d{1,2})\\s*일");
        Matcher m = p.matcher(question);
        if (m.find()) {
            int month = Integer.parseInt(m.group(1));
            int day   = Integer.parseInt(m.group(2));
            int year  = LocalDate.now(ZoneId.of("Asia/Seoul")).getYear();
            return LocalDate.of(year, month, day);
        }
        return null;
    }

    private Long findJobIdByTargetDate(LocalDate targetDate) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM schedule_job
                WHERE schedule_target_date = ?
                  AND UPPER(TRIM(status)) IN ('COMPLETED', 'SUCCESS', 'DONE')
                ORDER BY created_at DESC NULLS LAST, id DESC
                LIMIT 1
                """, Long.class, targetDate);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private List<Map<String, Object>> getClusterDemandPeakRows(Long jobId) {
        return jdbcTemplate.queryForList("""
                WITH hourly AS (
                    SELECT hour, SUM(predicted_demand_kwh) AS total_predicted_demand_kwh
                    FROM station_demand_forecast
                    WHERE schedule_job_id = ?
                    GROUP BY hour
                ),
                ranked AS (
                    SELECT hour, total_predicted_demand_kwh,
                           RANK() OVER (ORDER BY total_predicted_demand_kwh DESC) AS rnk
                    FROM hourly
                )
                SELECT hour, total_predicted_demand_kwh
                FROM ranked
                WHERE rnk = 1
                ORDER BY hour
                """, jobId);
    }

    private List<Map<String, Object>> getStationDemandPeakRows(Long jobId) {
        return jdbcTemplate.queryForList("""
                WITH station_hourly AS (
                    SELECT sdf.station_index, cs.name AS station_name,
                           sdf.hour, sdf.predicted_demand_kwh
                    FROM station_demand_forecast sdf
                    LEFT JOIN charging_station cs ON cs.station_index = sdf.station_index
                    WHERE sdf.schedule_job_id = ?
                ),
                ranked AS (
                    SELECT station_index, station_name, hour, predicted_demand_kwh,
                           RANK() OVER (PARTITION BY station_index ORDER BY predicted_demand_kwh DESC) AS rnk
                    FROM station_hourly
                )
                SELECT station_index, station_name, hour, predicted_demand_kwh
                FROM ranked
                WHERE rnk = 1
                ORDER BY station_index, hour
                """, jobId);
    }

    private Map<String, Object> currentEssStatus() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT station_id, soc, demand_count, updated_at
                FROM station_state
                ORDER BY station_id
                """);

        if (rows.isEmpty()) {
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("status", "no_data");
            context.put("data_type", "realtime_db_latest");
            context.put("message", "현재 저장된 ESS 상태 데이터가 없습니다.");
            context.put("source_tables", List.of("station_state", "power_metrics", "charging_station"));
            return context;
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("status", "ok");
        context.put("data_type", "realtime_db_latest");
        context.put("stations", rows);
        context.put("source_tables", List.of("station_state"));
        return context;
    }

    private Map<String, Object> currentChargerStatus() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT cs.charger_id, c.charger_type, c.station_id, cs.mode, cs.power_demand, cs.is_active
                FROM charger_state cs
                JOIN charger c ON c.id = cs.charger_id
                ORDER BY c.station_id, c.charger_index
                """);

        if (rows.isEmpty()) return noData("충전기 상태 데이터가 없습니다.");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("status", "ok");
        context.put("data_type", "realtime_db_latest");
        context.put("chargers", rows);
        context.put("source_tables", List.of("charger_state", "charger"));
        return context;
    }

    private Map<String, Object> scheduleSummary() {
        Long jobId = tomorrowOrLatestJobId();
        if (jobId == null) return noData("스케줄 데이터가 없습니다.");

        List<Map<String, Object>> jobRows = jdbcTemplate.queryForList("""
                SELECT schedule_target_date, status, cost_reduction_krw, cost_reduction_pct,
                       grid_only_cost_krw, llm_summary_text
                FROM schedule_job WHERE id = ?
                """, jobId);

        if (jobRows.isEmpty()) return noData("스케줄 데이터가 없습니다.");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("status", "ok");
        context.put("data_type", "schedule");
        context.put("job_id", jobId);
        context.put("schedule", jobRows.get(0));
        context.put("source_tables", List.of("schedule_job", "schedule_result", "hourly_plan"));
        return context;
    }

    private Map<String, Object> transferStatus() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT t.target_station_id,
                       SUM(t.transfer_energy_kwh) AS total_transfer_kwh,
                       SUM(t.received_energy_kwh) AS total_received_kwh
                FROM transfer t
                GROUP BY t.target_station_id
                ORDER BY t.target_station_id
                """);

        if (rows.isEmpty()) return noData("전력 이송 데이터가 없습니다.");

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("status", "ok");
        context.put("data_type", "transfer");
        context.put("transfers", rows);
        context.put("source_tables", List.of("transfer", "hourly_plan"));
        return context;
    }

    private Map<String, Object> pvPeakTomorrow() {
        Long jobId = tomorrowOrLatestJobId();
        if (jobId == null) return noData("스케줄 데이터가 없습니다.");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT hp.hour, SUM(hp.pv_generation_pred_kwh) AS total_pv_kwh
                FROM hourly_plan hp
                JOIN schedule_result sr ON sr.id = hp.schedule_result_id
                WHERE sr.schedule_job_id = ?
                GROUP BY hp.hour
                ORDER BY total_pv_kwh DESC
                LIMIT 1
                """, jobId);

        if (rows.isEmpty()) return noData("태양광 발전 예측 데이터가 없습니다.");

        Map<String, Object> peak = rows.get(0);
        int peakHour = ((Number) peak.get("hour")).intValue();
        double peakPv = ((Number) peak.get("total_pv_kwh")).doubleValue();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("status", "ok");
        context.put("data_type", "prediction");
        context.put("peak_time_range", String.format("%02d:00~%02d:00", peakHour, peakHour + 1));
        context.put("peak_total_pv_kwh", Math.round(peakPv * 10.0) / 10.0);
        context.put("source_tables", List.of("schedule_job", "schedule_result", "hourly_plan"));
        return context;
    }

    private Map<String, Object> gridPeakTomorrow() {
        Long jobId = tomorrowOrLatestJobId();
        if (jobId == null) return noData("스케줄 데이터가 없습니다.");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT hp.hour, SUM(hp.grid_usage_kwh) AS total_grid_kwh
                FROM hourly_plan hp
                JOIN schedule_result sr ON sr.id = hp.schedule_result_id
                WHERE sr.schedule_job_id = ?
                GROUP BY hp.hour
                ORDER BY total_grid_kwh DESC
                LIMIT 1
                """, jobId);

        if (rows.isEmpty()) return noData("계통 사용량 예측 데이터가 없습니다.");

        Map<String, Object> peak = rows.get(0);
        int peakHour = ((Number) peak.get("hour")).intValue();
        double peakGrid = ((Number) peak.get("total_grid_kwh")).doubleValue();

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("status", "ok");
        context.put("data_type", "prediction");
        context.put("peak_time_range", String.format("%02d:00~%02d:00", peakHour, peakHour + 1));
        context.put("peak_total_grid_kwh", Math.round(peakGrid * 10.0) / 10.0);
        context.put("source_tables", List.of("schedule_job", "schedule_result", "hourly_plan"));
        return context;
    }

    private Map<String, Object> weatherTomorrow() {
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
        String baseDate = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);

        List<Map<String, Object>> raw = weatherApiService.fetchRawForecastItems(61, 125, baseDate, tomorrow);
        if (raw.isEmpty()) return noData("날씨 예보 데이터를 가져올 수 없습니다.");

        List<Map<String, Object>> hourly = weatherApiService.buildWeatherForecastHourly(raw);
        if (hourly.isEmpty()) return noData("날씨 예보 데이터를 가져올 수 없습니다.");

        double minTemp = Double.MAX_VALUE, maxTemp = -Double.MAX_VALUE;
        double maxPop = 0, maxPrecip = 0;
        double sumHumidity = 0;
        int count = 0;
        boolean willRain = false;

        for (Map<String, Object> h : hourly) {
            double tmp = toDouble(h, "temperature_c");
            double pop = toDouble(h, "pop_pct");
            double pcp = toDouble(h, "precipitation_mm");
            double pty = toDouble(h, "pty");

            if (tmp < minTemp) minTemp = tmp;
            if (tmp > maxTemp) maxTemp = tmp;
            if (pop > maxPop) maxPop = pop;
            if (pcp > maxPrecip) maxPrecip = pcp;
            if (pty > 0) willRain = true;
            sumHumidity += toDouble(h, "humidity_pct");
            count++;
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("status", "ok");
        context.put("data_type", "weather_forecast");
        context.put("target_date", tomorrow.toString());
        context.put("min_temp_c", Math.round(minTemp * 10.0) / 10.0);
        context.put("max_temp_c", Math.round(maxTemp * 10.0) / 10.0);
        context.put("max_pop_pct", (int) maxPop);
        context.put("max_precipitation_mm", Math.round(maxPrecip * 10.0) / 10.0);
        context.put("avg_humidity_pct", count > 0 ? (int) (sumHumidity / count) : 0);
        context.put("will_rain", willRain);
        context.put("hourly_count", hourly.size());
        context.put("source", "KMA_SHORT_TERM");
        return context;
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    private Long tomorrowOrLatestJobId() {
        LocalDate tomorrow = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);

        List<Long> tomorrowIds = jdbcTemplate.queryForList("""
                SELECT id FROM schedule_job
                WHERE schedule_target_date = ?
                  AND UPPER(TRIM(status)) IN ('COMPLETED', 'SUCCESS', 'DONE')
                ORDER BY created_at DESC NULLS LAST, id DESC
                LIMIT 1
                """, Long.class, tomorrow);

        if (!tomorrowIds.isEmpty()) return tomorrowIds.get(0);

        List<Long> latestIds = jdbcTemplate.queryForList("""
                SELECT id FROM schedule_job
                WHERE UPPER(TRIM(status)) IN ('COMPLETED', 'SUCCESS', 'DONE')
                ORDER BY schedule_target_date DESC, created_at DESC NULLS LAST, id DESC
                LIMIT 1
                """, Long.class);

        if (!latestIds.isEmpty()) return latestIds.get(0);

        List<Long> anyIds = jdbcTemplate.queryForList("""
                SELECT id FROM schedule_job
                ORDER BY schedule_target_date DESC, created_at DESC NULLS LAST, id DESC
                LIMIT 1
                """, Long.class);

        return anyIds.isEmpty() ? null : anyIds.get(0);
    }

    private Map<String, Object> noData(String message) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("status", "no_data");
        context.put("message", message);
        return context;
    }

    private double toDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString().trim()); } catch (Exception e) { return 0.0; }
    }
}
