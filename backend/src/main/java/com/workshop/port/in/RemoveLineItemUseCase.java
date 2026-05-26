package com.workshop.port.in;

import java.util.UUID;

public interface RemoveLineItemUseCase {

    void removeLineItem(UUID orderId, UUID itemId);
}
