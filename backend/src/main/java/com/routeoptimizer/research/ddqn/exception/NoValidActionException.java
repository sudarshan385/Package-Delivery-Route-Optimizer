package com.routeoptimizer.research.ddqn.exception;

public class NoValidActionException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public NoValidActionException(String message) {
        super(message);
    }
}
