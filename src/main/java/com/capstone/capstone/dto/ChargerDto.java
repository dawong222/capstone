package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChargerDto {

    private Long chargerId;
    private String type;
    private double powerDemandW;
    private boolean isActive;
}