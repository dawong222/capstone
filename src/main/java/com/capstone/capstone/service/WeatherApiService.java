package com.capstone.capstone.service;

import com.capstone.capstone.dto.ai.ForecastWeatherItemDto;
import com.capstone.capstone.dto.ai.PastWeatherItemDto;
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

    public List<PastWeatherItemDto> fetchAsosWeather(
            String stnIds, String startDt, String endDt, DateTimeFormatter iso) {
        try {
            String body = httpGet(buildAsosUrl(stnIds, startDt, endDt));
            return parseAsosToWeatherItems(body, iso);
        } catch (Exception e) {
            log.warn("[ASOS API 실패] stnIds={} : {}", stnIds, e.getMessage());
            return new ArrayList<>();
        }
    }

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

    public List<Map<String, String>> getHistoryRaw() {
        try {
            String json = callAsosApi();
            return parseAsosJson(json);
        } catch (Exception e) {
            throw new RuntimeException("ASOS API 호출 실패", e);
        }
    }

    // ─── 단기예보 ─────────────────────────────────────────────────────

    public List<ForecastWeatherItemDto> fetchVilageFcst(
            int nx, int ny, String baseDate, LocalDate targetDate, DateTimeFormatter iso) {
        try {
            String json = restTemplate.getForObject(buildVilageFcstUrl(nx, ny, baseDate, "2000"), String.class);
            return parseVilageFcstItems(json, targetDate, iso);
        } catch (Exception e) {
            log.warn("[단기예보 API 실패] nx={}, ny={} : {}", nx, ny, e.getMessage());
            return new ArrayList<>();
        }
    }

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
                    item.setTm(LocalDateTime.parse(tmRaw, asosFmt).atZone(KST).format(iso));
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
            LocalDate targetEnd = targetDate.plusDays(1);
            DateTimeFormatter dateFmt   = DateTimeFormatter.BASIC_ISO_DATE;
            DateTimeFormatter fcstDtFmt = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

            Map<String, ForecastWeatherItemDto> pivot = new LinkedHashMap<>();
            Map<String, Double> tmnMap = new HashMap<>();
            Map<String, Double> tmxMap = new HashMap<>();

            for (JsonNode node : items) {
                String fcstDate = node.path("fcstDate").asText();
                String fcstTime = node.path("fcstTime").asText();
                String category = node.path("category").asText();
                String value    = node.path("fcstValue").asText();

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
                        dto.setTmef(LocalDateTime.parse(fcstDate + fcstTime, fcstDtFmt).atZone(KST).format(iso));
                    } catch (Exception ex) {
                        dto.setTmef(fcstDate + "T" + fcstTime);
                    }
                    return dto;
                });
                applyForecastCategory(pivot.get(key), category, value);
            }

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

    private String callAsosApi() {
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
        return new RestTemplate().getForObject(url, String.class);
    }

    private List<Map<String, String>> parseAsosJson(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode items = root.path("response").path("body").path("items").path("item");
        List<Map<String, String>> result = new ArrayList<>();
        for (JsonNode node : items) {
            Map<String, String> row = new HashMap<>();
            row.put("tm", node.path("tm").asText());
            row.put("ta", node.path("ta").asText());
            row.put("hm", node.path("hm").asText());
            row.put("ws", node.path("ws").asText());
            row.put("wd", node.path("wd").asText());
            row.put("rn", node.path("rn").asText());
            row.put("si", node.path("icsr").asText());
            result.add(row);
        }
        return result;
    }
}
