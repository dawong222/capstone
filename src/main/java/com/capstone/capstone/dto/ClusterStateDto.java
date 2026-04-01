package com.capstone.capstone.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ClusterStateDto {

    private int timeIndex;
    private int dayOfWeek;
    private double touPrice;
    private double gridLimitW;
    private boolean transferEnabled;
}