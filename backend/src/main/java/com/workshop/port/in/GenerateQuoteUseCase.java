package com.workshop.port.in;

import com.workshop.domain.model.RepairOrder;

import java.util.UUID;

public interface GenerateQuoteUseCase {

    RepairOrder generateQuote(UUID orderId);
}
