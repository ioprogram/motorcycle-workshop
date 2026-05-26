package com.workshop.port.out;

import com.workshop.domain.model.Motorcycle;

import java.util.Optional;
import java.util.UUID;

public interface MotorcycleRepository {

    Optional<Motorcycle> findById(UUID id);

    boolean existsByLicensePlate(String licensePlate);

    Motorcycle save(Motorcycle motorcycle);
}
