package com.capstone.capstone.controller;

import com.capstone.capstone.service.DataProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final DataProcessingService dataProcessingService;

    @GetMapping(value = "/telemetry", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        dataProcessingService.addEmitter(emitter);
        return emitter;
    }
}
