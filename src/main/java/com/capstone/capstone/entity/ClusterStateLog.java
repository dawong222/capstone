package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ClusterStateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer timeIndex;
    private Integer dayOfWeek;
    private Double touPrice;
    @Column(name = "grid_limit_w")
    private Double gridLimitW;
    private Boolean transferEnabled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_job_id")
    private ScheduleJob scheduleJob;
}