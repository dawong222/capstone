package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class HourlyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer hour;
    private String essMode;
    private Double essPowerKw;
    private Double essPowerSignedKw;
    private Double essEnergyKwh;
    private Double gridUsageKw;
    private Double gridUsageKwh;
    private Double pvGenerationPredKwh;
    private Double loadPredKwh;
    private Double pvPriority;
    private Double expectedSoc;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_result_id")
    private ScheduleResult scheduleResult;

    @OneToMany(mappedBy = "hourlyPlan", cascade = CascadeType.ALL)
    private List<Transfer> transfers;
}