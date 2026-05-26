package com.workshop.port.in;

import com.workshop.domain.model.QuoteToken;
import com.workshop.domain.model.RepairOrder;

public interface CustomerApprovalUseCase {

    QuoteView viewQuote(String rawToken);

    void approveQuote(String rawToken);

    void rejectQuote(String rawToken);

    record QuoteView(RepairOrder order, QuoteToken token) {}
}
