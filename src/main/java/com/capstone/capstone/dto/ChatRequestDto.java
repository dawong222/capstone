package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatRequestDto {
    private String sessionId;
    private String message;
}
