package com.workshop.port.in;

import com.workshop.domain.model.Motorcycle;

import java.util.UUID;

public interface RegisterMotorcycleUseCase {

    Motorcycle registerMotorcycle(Command command);

    record Command(String licensePlate, String make, String model, int year, UUID customerId) {}
}
