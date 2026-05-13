package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ScheduleHistoryItemDto {
    private String requestId;
    private String targetDate;
    private String createdAt;
    private String status;
    private Double costReductionKrw;
    private Double costReductionPct;
    private Double gridOnlyCostKrw;
}
