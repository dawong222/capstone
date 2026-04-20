package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class IoTDataDto {
    private int station_id;
    private boolean is_physical;
    private String timestamp;

    private List<ChargerStatusDto> charger_status;
    private PowerDto power_metrics;

    private double soc;
    private double capacity_wh;

    private boolean is_active;
    private int error_code;
}
