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
    @Column(name = "ess_power_w")
    private Double essPowerW;
    @Column(name = "grid_usage_w")
    private Double gridUsageW;
    private Double pvPriority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_result_id")
    private ScheduleResult scheduleResult;

    @OneToMany(mappedBy = "hourlyPlan", cascade = CascadeType.ALL)
    private List<Transfer> transfers;
}