package com.workshop.port.in;

import com.workshop.domain.model.Customer;

public interface CreateCustomerUseCase {

    Customer createCustomer(Command command);

    record Command(String fullName, String phone, String email) {}
}
