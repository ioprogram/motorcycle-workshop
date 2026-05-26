package com.workshop.domain.exception;

import com.workshop.domain.enums.OrderStatus;

public class IllegalTransitionException extends DomainException {

    public IllegalTransitionException(OrderStatus from, OrderStatus to) {
        super("Illegal transition from " + from + " to " + to);
    }
}
