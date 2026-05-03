package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PastWeatherItemDto {

    @JsonProperty("tm")
    private String tm;

    @JsonProperty("ta")
    private Double ta;       // 기온, ℃

    @JsonProperty("rn")
    private Double rn;       // 강수량, mm

    @JsonProperty("ws")
    private Double ws;       // 풍속, m/s

    @JsonProperty("wd")
    private Double wd;       // 풍향, deg

    @JsonProperty("hm")
    private Double hm;       // 상대습도, %

    @JsonProperty("pa")
    private Double pa;       // 현지기압, hPa

    @JsonProperty("ps")
    private Double ps;       // 해면기압, hPa

    @JsonProperty("ss")
    private Double ss;       // 일조시간, hr

    @JsonProperty("icsr")
    private Double icsr;     // 일사량, MJ/m²

    @JsonProperty("dsnw")
    private Double dsnw;     // 적설, cm

    @JsonProperty("hr3Fhsc")
    private Double hr3Fhsc;  // 3시간신적설, cm

    @JsonProperty("dc10Tca")
    private Double dc10Tca;  // 전운량, 0~10
}
