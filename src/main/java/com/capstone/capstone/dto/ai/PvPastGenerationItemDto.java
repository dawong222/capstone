package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class PvPastGenerationItemDto {

    @JsonProperty("tm")
    private String tm;

    @JsonProperty("slot_start")
    private String slotStart;

    @JsonProperty("slot_end")
    private String slotEnd;

    @JsonProperty("gen_kwh")
    private double genKwh;
}
