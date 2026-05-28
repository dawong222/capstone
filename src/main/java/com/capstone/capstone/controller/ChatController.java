package com.capstone.capstone.controller;

import com.capstone.capstone.dto.ChatRequestDto;
import com.capstone.capstone.dto.ChatResponseDto;
import com.capstone.capstone.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/message")
    public ResponseEntity<ChatResponseDto> chat(@RequestBody ChatRequestDto request) {
        ChatResponseDto response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }
}
