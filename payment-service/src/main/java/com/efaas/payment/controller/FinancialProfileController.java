package com.efaas.payment.controller;

import com.efaas.common.dto.FinancialProfile;
import com.efaas.payment.service.FinancialProfileService;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Internal endpoint consumed only by the Lending Service.
 * Not routed through the API gateway — called directly on port 8082.
 * Returns an aggregated FinancialProfile from Plaid balance + transaction data.
 */
@Slf4j
@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class FinancialProfileController {

    private final FinancialProfileService financialProfileService;

    @GetMapping("/financial-profile/{accountId}")
    public FinancialProfile getFinancialProfile(
            @PathVariable UUID accountId,
            @RequestParam UUID tenantId) {
        log.debug("Internal financial profile request: accountId={}, tenantId={}", accountId, tenantId);
        return financialProfileService.buildProfile(tenantId, accountId);
    }
}
