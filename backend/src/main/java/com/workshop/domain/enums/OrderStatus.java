package com.workshop.domain.enums;

import java.util.Map;
import java.util.Set;

public enum OrderStatus {
    DRAFT, QUOTED, APPROVED, IN_PROGRESS, COMPLETED, REJECTED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
        DRAFT,       Set.of(QUOTED),
        QUOTED,      Set.of(APPROVED, REJECTED),
        APPROVED,    Set.of(IN_PROGRESS, REJECTED, CANCELLED),
        IN_PROGRESS, Set.of(COMPLETED)
    );

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }
}
