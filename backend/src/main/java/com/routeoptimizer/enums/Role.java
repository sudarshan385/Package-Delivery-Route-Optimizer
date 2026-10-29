package com.routeoptimizer.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.routeoptimizer.exception.BadRequestException;

public enum Role {
    ADMIN,
    DRIVER,
    USER;

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null) {
            return null;
        }
        for (Role role : values()) {
            if (role.name().equalsIgnoreCase(value.trim())) {
                return role;
            }
        }
        throw new BadRequestException("Unsupported role: '" + value + "'. Supported roles are: ADMIN, DRIVER, USER");
    }
}
