package com.workshop.port.in;

import com.workshop.domain.model.RepairOrder;

import java.util.UUID;

public interface RejectQuoteUseCase {

    RepairOrder rejectQuote(UUID orderId);
}
