package com.routeoptimizer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.routeoptimizer.enums.Role;
import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private Role role;
    private Boolean active;

    @JsonProperty("isVerified")
    private Boolean isVerified;

    @JsonProperty("isBlocked")
    private Boolean isBlocked;

    private Long driverId;
    private OffsetDateTime createdAt;

    @JsonProperty("verified")
    public Boolean getVerified() {
        return isVerified;
    }

    @JsonProperty("blocked")
    public Boolean getBlocked() {
        return isBlocked;
    }
}
