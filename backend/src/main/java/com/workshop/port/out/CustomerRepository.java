package com.workshop.port.out;

import com.workshop.domain.model.Customer;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository {

    Optional<Customer> findById(UUID id);

    Customer save(Customer customer);
}
