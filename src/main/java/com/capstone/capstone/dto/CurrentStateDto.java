package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CurrentStateDto {

    private int demandCount;
    private List<ChargerDto> chargers;
    private PowerDto power;
    private double soc;
}