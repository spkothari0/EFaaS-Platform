package com.efaas.payment.dto.plaid;

import com.efaas.payment.entity.PlaidAccount;

import java.util.UUID;

public record BankAccountDto(
        UUID id,
        String name,
        String mask,
        String type,
        String subtype
) {
    public static BankAccountDto from(PlaidAccount account) {
        return new BankAccountDto(
                account.getId(),
                account.getName(),
                account.getMask(),
                account.getType(),
                account.getSubtype()
        );
    }
}
