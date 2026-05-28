package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ChatRequestDto {
    private String sessionId;
    private String message;
    private List<ChatHistoryItem> history;
}
