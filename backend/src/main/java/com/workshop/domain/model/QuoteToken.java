package com.workshop.domain.model;

import com.workshop.domain.enums.TokenStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "quote_tokens",
    indexes = {
        @Index(name = "ix_qt_hash",    columnList = "token_hash"),
        @Index(name = "ix_qt_expires", columnList = "expires_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private RepairOrder repairOrder;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TokenStatus status;

    @Column(nullable = false)
    private int renewalSequence;

    private Instant usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser issuedBy;

    @Version
    private Long version;
}
