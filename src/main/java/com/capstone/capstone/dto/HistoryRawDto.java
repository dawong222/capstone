package com.capstone.capstone.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.HashMap;
import java.util.Map;

public class HistoryRawDto {
    private Map<String, String> data = new HashMap<>();

    @JsonAnySetter
    public void put(String key, String value) {
        data.put(key, value);
    }
}
