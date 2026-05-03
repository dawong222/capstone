package com.capstone.capstone.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TargetWindowDto {

    @JsonProperty("start")
    private String start;

    @JsonProperty("end")
    private String end;

    @JsonProperty("slot_unit")
    private String slotUnit;

    @JsonProperty("slot_count")
    private int slotCount;

    @JsonProperty("slot_definition")
    private String slotDefinition;
}
