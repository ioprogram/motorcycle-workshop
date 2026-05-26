package com.workshop.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "motorcycles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Motorcycle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String licensePlate;

    @Column(nullable = false)
    private String make;

    @Column(nullable = false)
    private String model;

    @Column(nullable = false)
    private int year;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(nullable = false)
    private Customer customer;

    @Version
    private Long version;

    @PrePersist
    @PreUpdate
    void normaliseLicensePlate() {
        if (licensePlate != null) {
            licensePlate = licensePlate.toUpperCase();
        }
    }
}
