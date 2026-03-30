package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PowerMetricsDto {
    private double p_pv;
    private double p_load;
    private double p_ess;
    private double p_grid;
    private double p_tr;
}
