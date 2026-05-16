package com.capstone.capstone.mqtt;

// import com.capstone.capstone.service.AiService;
// import com.fasterxml.jackson.core.type.TypeReference;
// import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// import java.util.Map;

@Slf4j
@Component
public class MqttAiRequestSubscriber {

    // private final AiService aiService;
    // private final ObjectMapper objectMapper;

    // ── [데모 모드] IoT가 만든 AI 요청을 그대로 전달 ──────────────────────────
    // 원래 파이프라인(DB + 기상청 API로 요청 직접 생성)으로 전환 시 이 메서드 제거하고
    // AiRequestBuilderService.buildRawAiRequest() 기반 스케줄러를 사용할 것
    // @ServiceActivator(inputChannel = "mqttAiRequestChannel")
    // public void handleMessage(String payload) {
    //     log.info("[MQTT AI 요청 수신] payload 길이={}", payload.length());
    //     try {
    //         Map<String, Object> request = objectMapper.readValue(payload, new TypeReference<>() {});
    //         aiService.sendRaw(request);
    //     } catch (Exception e) {
    //         log.error("[MQTT AI 요청 처리 실패] {}", e.getMessage());
    //     }
    // }
    // ── [데모 모드 끝] ────────────────────────────────────────────────────────
}
