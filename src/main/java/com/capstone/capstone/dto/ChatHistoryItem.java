package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ChatHistoryItem {
    private String role;
    private String content;
}
