package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ForecastWeatherItemDto {

    @JsonProperty("tmef")
    private String tmef;

    @JsonProperty("TMP")
    private Double tmp;      // 기온, ℃

    @JsonProperty("POP")
    private Double pop;      // 강수확률, %

    @JsonProperty("PTY")
    private Integer pty;     // 강수형태 (0=없음,1=비,2=비/눈,3=눈,4=소나기)

    @JsonProperty("PCP")
    private Object pcp;      // 1시간 강수량 (float or string)

    @JsonProperty("SNO")
    private Object sno;      // 1시간 신적설 (float or string)

    @JsonProperty("REH")
    private Double reh;      // 상대습도, %

    @JsonProperty("SKY")
    private Integer sky;     // 하늘상태 (1=맑음,3=구름많음,4=흐림)

    @JsonProperty("WSD")
    private Double wsd;      // 풍속, m/s

    @JsonProperty("VEC")
    private Double vec;      // 풍향, deg

    @JsonProperty("UUU")
    private Double uuu;      // 동서 바람 성분, m/s

    @JsonProperty("VVV")
    private Double vvv;      // 남북 바람 성분, m/s

    @JsonProperty("TMN")
    private Double tmn;      // 일 최저기온, ℃

    @JsonProperty("TMX")
    private Double tmx;      // 일 최고기온, ℃
}
