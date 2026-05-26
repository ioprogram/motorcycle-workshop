package com.workshop.port.in;

import com.workshop.domain.model.AppUser;

import java.util.UUID;

public interface ApproveQuoteUseCase {

    TokenIssuanceResult approveQuote(UUID orderId, AppUser issuedBy);
}
