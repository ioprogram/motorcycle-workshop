package com.workshop.port.in;

import com.workshop.domain.model.LineItem;

import java.math.BigDecimal;
import java.util.UUID;

public interface UpdateLineItemUseCase {

    LineItem updateLineItem(Command command);

    record Command(UUID orderId, UUID itemId, String description, BigDecimal quantity, BigDecimal unitPrice) {}
}
