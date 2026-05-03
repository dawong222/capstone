package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TouPriceItemDto {

    @JsonProperty("slot")
    private int slot;

    @JsonProperty("time_start")
    private String timeStart;

    @JsonProperty("time_end")
    private String timeEnd;

    @JsonProperty("tou_level")
    private String touLevel;

    @JsonProperty("price_krw_per_kwh")
    private double priceKrwPerKwh;
}
