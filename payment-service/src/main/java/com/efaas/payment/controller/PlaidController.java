package com.efaas.payment.controller;

import com.efaas.payment.dto.plaid.*;
import com.efaas.payment.service.PlaidService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/plaid")
@RequiredArgsConstructor
@Tag(name = "Plaid Bank-Link", description = "Bank account linking and ACH payments via Plaid")
public class PlaidController {

    private final PlaidService plaidService;

    @PostMapping("/link-token")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a Plaid Link token",
            description = "Returns a short-lived link_token used to initialize Plaid Link on the client side.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Link token created"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public LinkTokenResponse createLinkToken(
            @Valid @RequestBody CreateLinkTokenRequest request,
            @Parameter(hidden = true)
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return plaidService.createLinkToken(tenantId, request.userId());
    }

    @PostMapping("/exchange-token")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Exchange a Plaid public token",
            description = "Exchanges the public_token returned by Plaid Link for a permanent access_token. " +
                    "Persists linked accounts and publishes a BankAccountLinkedEvent to Kafka.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Token exchanged, accounts linked"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired public token")
    })
    public LinkedAccountsResponse exchangeToken(
            @Valid @RequestBody ExchangeTokenRequest request,
            @Parameter(hidden = true)
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return plaidService.exchangePublicToken(tenantId, request.publicToken());
    }

    @GetMapping("/accounts")
    @Operation(summary = "List linked bank accounts",
            description = "Returns all active bank accounts linked via Plaid for this tenant.")
    @ApiResponse(responseCode = "200", description = "List of linked accounts")
    public LinkedAccountsResponse getAccounts(
            @Parameter(hidden = true)
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return plaidService.getLinkedAccounts(tenantId);
    }

    @DeleteMapping("/accounts/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Unlink a bank account",
            description = "Deactivates the account. If it is the last account in an Item, " +
                    "the Item is also removed from Plaid (access token revoked).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account unlinked"),
            @ApiResponse(responseCode = "404", description = "Account not found")
    })
    public void unlinkAccount(
            @PathVariable("accountId") UUID accountId,
            @Parameter(hidden = true)
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        plaidService.unlinkAccount(tenantId, accountId);
    }

    @PostMapping("/payments/ach")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Initiate an ACH bank transfer",
            description = "Uses the Plaid Transfer API to debit a linked bank account. " +
                    "Amount is in smallest currency unit (cents). Publishes AchPaymentInitiatedEvent to Kafka.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "ACH transfer initiated"),
            @ApiResponse(responseCode = "400", description = "Invalid request or transfer declined"),
            @ApiResponse(responseCode = "404", description = "Bank account not found")
    })
    public AchPaymentResponse initiateAchPayment(
            @Valid @RequestBody InitiateAchPaymentRequest request,
            @Parameter(hidden = true)
            @RequestHeader("X-Tenant-Id") UUID tenantId) {
        return plaidService.initiateAchPayment(tenantId, request);
    }
}
