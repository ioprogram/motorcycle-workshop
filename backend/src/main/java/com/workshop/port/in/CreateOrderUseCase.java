package com.workshop.port.in;

import com.workshop.domain.enums.OrderStatus;
import com.workshop.domain.model.AppUser;
import com.workshop.domain.model.RepairOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CreateOrderUseCase {

    RepairOrder createOrder(CreateCommand command);

    Page<RepairOrder> listOrders(AppUser principal, OrderStatus statusFilter, Pageable pageable);

    RepairOrder getOrderDetail(UUID orderId, AppUser principal);

    record CreateCommand(UUID customerId, UUID motorcycleId, String problemDescription, AppUser mechanic) {}
}
