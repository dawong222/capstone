package com.capstone.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {
    private String answer;
    private String intent;
    private Map<String, Object> context;
}
