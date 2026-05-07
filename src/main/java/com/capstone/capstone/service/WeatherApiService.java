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
import java.net.URLEncoder;
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
            String body = httpGet(buildAsosUrl(stnIds, startDt, endDt));
            JsonNode items = objectMapper.readTree(body)
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

    private String buildAsosUrl(String stnIds, String startDt, String endDt) throws Exception {
        StringBuilder sb = new StringBuilder("http://apis.data.go.kr/1360000/AsosHourlyInfoService/getWthrDataList");
        sb.append("?").append(URLEncoder.encode("serviceKey", "UTF-8")).append("=").append(asosKey);
        sb.append("&").append(URLEncoder.encode("pageNo",    "UTF-8")).append("=").append(URLEncoder.encode("1",    "UTF-8"));
        sb.append("&").append(URLEncoder.encode("numOfRows", "UTF-8")).append("=").append(URLEncoder.encode("300",  "UTF-8"));
        sb.append("&").append(URLEncoder.encode("dataType",  "UTF-8")).append("=").append(URLEncoder.encode("JSON", "UTF-8"));
        sb.append("&").append(URLEncoder.encode("dataCd",    "UTF-8")).append("=").append(URLEncoder.encode("ASOS", "UTF-8"));
        sb.append("&").append(URLEncoder.encode("dateCd",    "UTF-8")).append("=").append(URLEncoder.encode("HR",   "UTF-8"));
        sb.append("&").append(URLEncoder.encode("startDt",   "UTF-8")).append("=").append(URLEncoder.encode(startDt, "UTF-8"));
        sb.append("&").append(URLEncoder.encode("startHh",   "UTF-8")).append("=").append(URLEncoder.encode("00",  "UTF-8"));
        sb.append("&").append(URLEncoder.encode("endDt",     "UTF-8")).append("=").append(URLEncoder.encode(endDt,   "UTF-8"));
        sb.append("&").append(URLEncoder.encode("endHh",     "UTF-8")).append("=").append(URLEncoder.encode("22",  "UTF-8"));
        sb.append("&").append(URLEncoder.encode("stnIds",    "UTF-8")).append("=").append(URLEncoder.encode(stnIds,  "UTF-8"));
        return sb.toString();
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


}
