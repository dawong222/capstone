package com.capstone.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HourlySolarDto {
    private int hour;
    private double actualKw;
}
