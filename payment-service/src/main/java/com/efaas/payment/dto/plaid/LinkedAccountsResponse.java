package com.efaas.payment.dto.plaid;

import java.util.List;

public record LinkedAccountsResponse(List<BankAccountDto> accounts) {}
