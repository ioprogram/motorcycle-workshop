package com.workshop.port.out;

import com.workshop.domain.model.QuoteToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TokenRepository {

    Optional<QuoteToken> findByTokenHash(String tokenHash);

    Optional<QuoteToken> findActiveByRepairOrderId(UUID orderId);

    /** CAS update: marks token USED only when ACTIVE and not expired. Returns rows affected. */
    int markUsed(String tokenHash);

    /** CAS update: marks token INVALIDATED only when ACTIVE and not expired. Returns rows affected. */
    int markInvalidated(String tokenHash);

    List<QuoteToken> findAllActiveExpiredBefore(Instant threshold);

    QuoteToken save(QuoteToken token);
}
