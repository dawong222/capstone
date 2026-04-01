package com.capstone.capstone.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ScheduleJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String requestId;

    private LocalDate scheduleTargetDate;

    private LocalDateTime createdAt;

    private String status;

    @OneToMany(mappedBy = "scheduleJob", cascade = CascadeType.ALL)
    private List<ScheduleResult> results;
}