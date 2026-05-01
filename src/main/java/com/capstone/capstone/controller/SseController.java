package com.capstone.capstone.controller;

import com.capstone.capstone.service.DataProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final DataProcessingService dataProcessingService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    @GetMapping(value = "/telemetry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        dataProcessingService.addEmitter(emitter);

        // 25초마다 heartbeat 전송 (Nginx/프록시 타임아웃 방지)
        ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
        }, 25, 25, TimeUnit.SECONDS);

        emitter.onCompletion(() -> heartbeat.cancel(true));
        emitter.onTimeout(() -> heartbeat.cancel(true));
        emitter.onError(e -> heartbeat.cancel(true));

        return emitter;
    }
}
