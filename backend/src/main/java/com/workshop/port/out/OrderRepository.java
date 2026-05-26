package com.workshop.port.out;

import com.workshop.domain.enums.OrderStatus;
import com.workshop.domain.model.RepairOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Optional<RepairOrder> findById(UUID id);

    /**
     * Returns orders filtered by optional mechanic and status.
     * Pass null for either param to skip that filter.
     * Results are sorted by updatedAt DESC (enforced by adapter).
     */
    Page<RepairOrder> findAllFiltered(UUID mechanicId, OrderStatus status, Pageable pageable);

    RepairOrder save(RepairOrder order);
}
