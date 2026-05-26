package com.workshop.port.in;

import java.time.Instant;
import java.util.UUID;

public record TokenIssuanceResult(
        UUID orderId,
        String rawToken,
        Instant expiresAt,
        int renewalSequence
) {}
