package com.workshop.port.in;

import com.workshop.domain.enums.LineItemType;
import com.workshop.domain.model.AppUser;
import com.workshop.domain.model.LineItem;

import java.math.BigDecimal;
import java.util.UUID;

public interface AddLineItemUseCase {

    LineItem addLineItem(Command command);

    record Command(
            UUID orderId,
            LineItemType type,
            String description,
            BigDecimal quantity,
            BigDecimal unitPrice,
            AppUser principal
    ) {}
}
