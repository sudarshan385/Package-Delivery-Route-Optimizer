package com.routeoptimizer.research.ddqn.exception;

public class NoValidActionException extends RuntimeException {
    public NoValidActionException(String message) {
        super(message);
    }
}
