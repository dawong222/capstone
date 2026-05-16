package com.capstone.capstone.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiScheduleService {

    private final WeatherApiService weatherApiService;
    private final AiRequestBuilderService aiRequestBuilderService;
    private final AiService aiService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ISO_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    /** IoT에서 받은 payload에 날씨·예측 데이터를 채워 AI 서버로 전송 */
    public void enrichAndSend(Map<String, Object> payload) {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(1);

        String asosStart = today.minusDays(7).format(DATE_FMT);
        String asosEnd   = today.format(DATE_FMT);
        List<Map<String, Object>> asosRaw = weatherApiService.fetchRawAsosItems("108", asosStart, asosEnd);

        payload.put("demand_past_weather_hourly",
                weatherApiService.buildAsosWeatherHourly(asosRaw, "ASOS_108", "Seoul/Gangnam", ISO_FMT));
        payload.put("pv_past_weather_hourly",
                weatherApiService.buildAsosWeatherHourly(asosRaw, "ASOS_108", "Seoul/Gangnam", ISO_FMT));

        List<Map<String, Object>> forecastRaw =
                weatherApiService.fetchRawForecastItems(61, 125, today.format(DATE_FMT), targetDate);
        payload.put("demand_forecast_short_term_hourly",
                aiRequestBuilderService.buildDemandForecast(targetDate));
        payload.put("pv_forecast_short_term_hourly",
                aiRequestBuilderService.buildPvForecast(targetDate));
        payload.put("weather_forecast_short_term_hourly",
                weatherApiService.buildWeatherForecastHourly(forecastRaw));

        log.info("[AI 스케줄 요청] 날씨·예측 데이터 삽입 완료, AI 서버로 전송");
        aiService.sendRaw(payload);
    }
}
