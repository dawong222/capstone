package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double power;
    private Long targetStationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hourly_plan_id")
    private HourlyPlan hourlyPlan;
}