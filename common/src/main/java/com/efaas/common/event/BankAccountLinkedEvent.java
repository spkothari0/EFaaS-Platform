package com.efaas.common.event;

import com.efaas.common.enums.ServiceName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Published when a user successfully links a bank account via Plaid Link.
 * Topic: plaid-topic
 * Consumed by: Lending Service (income verification), Fraud Service (identity graph)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class BankAccountLinkedEvent extends DomainEvent {

    private String plaidItemId;
    private String institutionId;
    private String institutionName;
    private int accountCount;

    public BankAccountLinkedEvent(UUID tenantId, String plaidItemId,
                                   String institutionId, String institutionName, int accountCount) {
        super();
        this.plaidItemId = plaidItemId;
        this.institutionId = institutionId;
        this.institutionName = institutionName;
        this.accountCount = accountCount;
        initializeEventMetadata(tenantId.toString(), ServiceName.PAYMENT_SERVICE);
    }

    @Override
    public String getEventName() {
        return "bank-account.linked";
    }
}
