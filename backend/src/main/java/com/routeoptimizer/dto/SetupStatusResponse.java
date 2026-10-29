package com.routeoptimizer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetupStatusResponse {
    private boolean setupRequired;
    private String message;
}
