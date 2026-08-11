package com.qualitywebsite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DateRange {
    private LocalDateTime start;
    private LocalDateTime end;
    private String periodLabel;
}
