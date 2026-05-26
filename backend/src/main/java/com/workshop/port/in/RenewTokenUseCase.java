package com.workshop.port.in;

import com.workshop.domain.model.AppUser;

import java.util.UUID;

public interface RenewTokenUseCase {

    TokenIssuanceResult renewToken(UUID orderId, AppUser issuedBy);
}
