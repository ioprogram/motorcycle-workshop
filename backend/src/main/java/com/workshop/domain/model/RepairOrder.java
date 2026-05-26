package com.workshop.domain.model;

import com.workshop.domain.enums.OrderStatus;
import com.workshop.domain.exception.IllegalTransitionException;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "repair_orders")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepairOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.DRAFT;

    @Setter
    @Column(nullable = false)
    private String problemDescription;

    @Setter
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private int tokenRenewalCount = 0;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private AppUser mechanic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Motorcycle motorcycle;

    @OneToMany(mappedBy = "repairOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<LineItem> lineItems = new ArrayList<>();

    @OneToOne(mappedBy = "repairOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private InspectionChecklist inspectionChecklist;

    @OneToMany(mappedBy = "repairOrder")
    @Builder.Default
    private List<QuoteToken> quoteTokens = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void transitionTo(OrderStatus next) {
        if (!this.status.canTransitionTo(next)) {
            throw new IllegalTransitionException(this.status, next);
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }

    public void incrementTokenRenewalCount() {
        this.tokenRenewalCount++;
        this.updatedAt = Instant.now();
    }

    // Called once at order creation only. InspectionChecklist is immutable after attachment.
    public void setInspectionChecklist(InspectionChecklist checklist) {
        this.inspectionChecklist = checklist;
    }
}
