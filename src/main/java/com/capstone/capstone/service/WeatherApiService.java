package com.capstone.capstone.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeatherApiService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${weather.asos.key}")
    private String asosKey;

    @Value("${weather.forecast.key}")
    private String forecastKey;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    // ─── ASOS 과거 관측 ───────────────────────────────────────────────

    public List<Map<String, Object>> fetchRawAsosItems(String stnIds, String startDt, String endDt) {
        try {
            String url = buildAsosUrl(stnIds, startDt, endDt);
            log.info("[ASOS raw 요청] url={}", url);
            String body = httpGet(url);
            log.info("[ASOS raw 응답] body={}", body.length() > 500 ? body.substring(0, 500) : body);
            JsonNode root = objectMapper.readTree(body);
            String rc  = root.path("response").path("header").path("resultCode").asText();
            String msg = root.path("response").path("header").path("resultMsg").asText();
            if (!"00".equals(rc)) {
                log.warn("[ASOS raw 실패] stnIds={} resultCode={} msg={}", stnIds, rc, msg);
                return new ArrayList<>();
            }
            JsonNode items = root.path("response").path("body").path("items").path("item");
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

    public List<Map<String, Object>> getHistoryRaw() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        String startDt = LocalDate.now().minusDays(7).format(fmt);
        String endDt   = LocalDate.now().format(fmt);
        return fetchRawAsosItems("108", startDt, endDt);
    }

    // ─── 단기예보 ─────────────────────────────────────────────────────

    public List<Map<String, Object>> fetchRawForecastItems(int nx, int ny, String baseDate, LocalDate targetDate) {
        String baseTime = computeLatestBaseTime();
        log.info("[단기예보 raw] nx={},ny={} base_date={} base_time={}", nx, ny, baseDate, baseTime);
        try {
            String json = restTemplate.getForObject(buildVilageFcstUrl(nx, ny, baseDate, baseTime), String.class);
            JsonNode root = objectMapper.readTree(json);

            String rc  = root.path("response").path("header").path("resultCode").asText();
            String msg = root.path("response").path("header").path("resultMsg").asText();
            if (!"00".equals(rc)) {
                log.warn("[단기예보 raw] nx={},ny={} resultCode={} msg={}", nx, ny, rc, msg);
                return new ArrayList<>();
            }

            JsonNode items = root.path("response").path("body").path("items").path("item");
            DateTimeFormatter dateFmt    = DateTimeFormatter.BASIC_ISO_DATE;
            DateTimeFormatter fcstDtFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
            DateTimeFormatter isoFmt    = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
            LocalDate targetEnd = targetDate.plusDays(1);

            Map<String, Map<String, Object>> pivot = new LinkedHashMap<>();
            for (JsonNode n : items) {
                String fcstDate = n.path("fcstDate").asText();
                String fcstTime = n.path("fcstTime").asText();
                String category = n.path("category").asText();
                String value    = n.path("fcstValue").asText();

                try {
                    LocalDate day = LocalDate.parse(fcstDate, dateFmt);
                    if (day.isBefore(targetDate) || day.isAfter(targetEnd)) continue;
                } catch (Exception ignored) { continue; }

                String key = fcstDate + "_" + fcstTime;
                Map<String, Object> slot = pivot.computeIfAbsent(key, k -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    try {
                        m.put("tm", LocalDateTime.parse(fcstDate + fcstTime, fcstDtFmt).atZone(KST).format(isoFmt));
                    } catch (Exception ex) {
                        m.put("tm", fcstDate + "T" + fcstTime);
                    }
                    return m;
                });

                try {
                    slot.put(category.toLowerCase(), Double.parseDouble(value));
                } catch (NumberFormatException e) {
                    slot.put(category.toLowerCase(), value);
                }
            }

            List<Map<String, Object>> result = new ArrayList<>(pivot.values());
            log.info("[단기예보 raw] nx={},ny={} → {}건(피벗)", nx, ny, result.size());
            return result;
        } catch (Exception e) {
            log.warn("[단기예보 raw 실패] nx={},ny={} : {}", nx, ny, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ─── private helpers ──────────────────────────────────────────────

    private String buildAsosUrl(String stnIds, String startDt, String endDt) {
        return "https://apis.data.go.kr/1360000/AsosHourlyInfoService/getWthrDataList"
                + "?serviceKey=" + asosKey
                + "&pageNo=1"
                + "&numOfRows=300"
                + "&dataType=JSON"
                + "&dataCd=ASOS"
                + "&dateCd=HR"
                + "&startDt=" + startDt
                + "&startHh=00"
                + "&endDt=" + endDt
                + "&endHh=23"
                + "&stnIds=" + stnIds;
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Content-type", "application/json");
        BufferedReader rd = conn.getResponseCode() >= 200 && conn.getResponseCode() <= 300
                ? new BufferedReader(new InputStreamReader(conn.getInputStream()))
                : new BufferedReader(new InputStreamReader(conn.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) sb.append(line);
        rd.close();
        conn.disconnect();
        return sb.toString();
    }

    private String buildVilageFcstUrl(int nx, int ny, String baseDate, String baseTime) {
        return UriComponentsBuilder
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
    }

    private String computeLatestBaseTime() {
        ZonedDateTime now = ZonedDateTime.now(KST);
        int totalMinutes = now.getHour() * 60 + now.getMinute();
        int[][] schedule = {{2,10},{5,10},{8,10},{11,10},{14,10},{17,10},{20,10},{23,10}};
        String[] codes   = {"0200","0500","0800","1100","1400","1700","2000","2300"};
        String result = "2300";
        for (int i = 0; i < schedule.length; i++) {
            if (totalMinutes >= schedule[i][0] * 60 + schedule[i][1]) result = codes[i];
        }
        return result;
    }

    public List<Map<String, Object>> buildAsosWeatherHourly(
            List<Map<String, Object>> asosRaw, String source, String locationName, DateTimeFormatter iso) {
        DateTimeFormatter asosFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : asosRaw) {
            String tmRaw = String.valueOf(r.getOrDefault("tm", ""));
            String ts;
            try { ts = LocalDateTime.parse(tmRaw, asosFmt).atZone(KST).format(iso); }
            catch (Exception e) { ts = tmRaw; }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", ts); row.put("tm", ts); row.put("source", source); row.put("location_name", locationName);
            row.put("ta", toDouble(r,"ta")); row.put("temperature_c", toDouble(r,"ta"));
            row.put("rn", toDouble(r,"rn")); row.put("precipitation_mm", toDouble(r,"rn"));
            row.put("ws", toDouble(r,"ws")); row.put("wind_speed_ms", toDouble(r,"ws"));
            row.put("wd", toDouble(r,"wd")); row.put("wind_direction_deg", toDouble(r,"wd"));
            row.put("hm", toDouble(r,"hm")); row.put("humidity_pct", toDouble(r,"hm"));
            row.put("pa", toDouble(r,"pa")); row.put("pressure_hpa", toDouble(r,"pa"));
            row.put("ps", toDouble(r,"ps")); row.put("sea_level_pressure_hpa", toDouble(r,"ps"));
            row.put("ss", toDouble(r,"ss")); row.put("sunshine_hours", toDouble(r,"ss"));
            row.put("icsr", toDouble(r,"icsr")); row.put("solar_radiation_mj_m2", toDouble(r,"icsr"));
            row.put("dsnw", toDouble(r,"dsnw")); row.put("snow_cm", toDouble(r,"dsnw"));
            row.put("hr3Fhsc", toDouble(r,"hr3Fhsc")); row.put("new_snow_3h_cm", toDouble(r,"hr3Fhsc"));
            row.put("dc10Tca", toDouble(r,"dc10Tca")); row.put("cloud_amount", toDouble(r,"dc10Tca"));
            list.add(row);
        }
        return list;
    }

    public List<Map<String, Object>> buildForecastWeatherHourly(
            List<Map<String, Object>> forecastRaw, String forecastLocation) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : forecastRaw) {
            String ts = String.valueOf(r.getOrDefault("tm", ""));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("timestamp", ts); row.put("tmef", ts); row.put("forecast_location", forecastLocation);
            row.put("TMP", toDouble(r,"tmp")); row.put("POP", toDouble(r,"pop"));
            row.put("PTY", toDouble(r,"pty").intValue()); row.put("PCP", toDouble(r,"pcp"));
            row.put("SNO", toDouble(r,"sno")); row.put("REH", toDouble(r,"reh"));
            row.put("SKY", toDouble(r,"sky").intValue()); row.put("WSD", toDouble(r,"wsd"));
            row.put("VEC", toDouble(r,"vec")); row.put("UUU", toDouble(r,"uuu"));
            row.put("VVV", toDouble(r,"vvv")); row.put("TMN", toDouble(r,"tmn"));
            row.put("TMX", toDouble(r,"tmx")); row.put("LGT", toDouble(r,"lgt").intValue());
            list.add(row);
        }
        return list;
    }

    private Double toDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString().trim()); } catch (Exception e) { return 0.0; }
    }
}
