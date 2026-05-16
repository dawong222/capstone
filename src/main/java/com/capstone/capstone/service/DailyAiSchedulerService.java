package com.capstone.capstone.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyAiSchedulerService {

    private final AiRequestBuilderService aiRequestBuilderService;
    private final AiService aiService;

    /**
     * 매일 22:00 KST — 익일 day-ahead 스케줄 요청
     * DB(충전소 현황 + 7일 스냅샷) + 기상청 API 를 조합해 요청을 직접 빌드 후 AI 서버로 전송.
     * AI 서버는 처리 완료 후 POST /ai/result 로 결과를 콜백.
     */
    @Scheduled(cron = "0 0 22 * * *", zone = "Asia/Seoul")
    public void triggerDailySchedule() {
        log.info("[일별 AI 스케줄] 요청 빌드 시작 (22:00 KST 트리거)");
        try {
            Map<String, Object> payload = aiRequestBuilderService.buildRawAiRequest();
            aiService.sendRaw(payload);
            log.info("[일별 AI 스케줄] AI 서버 전송 완료");
        } catch (Exception e) {
            log.error("[일별 AI 스케줄] 실패: {}", e.getMessage(), e);
        }
    }
}
