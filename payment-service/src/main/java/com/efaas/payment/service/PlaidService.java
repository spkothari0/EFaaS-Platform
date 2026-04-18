package com.efaas.payment.service;

import com.efaas.common.event.AchPaymentInitiatedEvent;
import com.efaas.common.event.BankAccountLinkedEvent;
import com.efaas.payment.dto.plaid.*;
import com.efaas.payment.entity.AchPayment;
import com.efaas.payment.entity.AchPaymentStatus;
import com.efaas.payment.entity.PlaidAccount;
import com.efaas.payment.entity.PlaidItem;
import com.efaas.payment.exception.BankAccountNotFoundException;
import com.efaas.payment.exception.PlaidException;
import com.efaas.payment.kafka.PlaidEventPublisher;
import com.efaas.payment.repository.AchPaymentRepository;
import com.efaas.payment.repository.PlaidAccountRepository;
import com.efaas.payment.repository.PlaidItemRepository;
import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaidService {

    private final PlaidApi plaidApi;
    private final PlaidItemRepository plaidItemRepository;
    private final PlaidAccountRepository plaidAccountRepository;
    private final AchPaymentRepository achPaymentRepository;
    private final PlaidEventPublisher eventPublisher;

    @Value("${plaid.webhook-url}")
    private String webhookUrl;

    // ─── Link Token ──────────────────────────────────────────────────────────────

    /**
     * Creates a Plaid Link token to initialize the Plaid Link UI on the client side.
     * The userId identifies the end-user within a tenant (opaque string).
     */
    public LinkTokenResponse createLinkToken(UUID tenantId, String userId) {
        LinkTokenCreateRequestUser user = new LinkTokenCreateRequestUser()
                .clientUserId(userId);

        LinkTokenCreateRequest request = new LinkTokenCreateRequest()
                .user(user)
                .clientName("EFaaS Platform")
                .products(List.of(Products.TRANSACTIONS, Products.AUTH))
                .countryCodes(List.of(CountryCode.US))
                .language("en")
                .webhook(webhookUrl);

        try {
            Response<LinkTokenCreateResponse> response = plaidApi.linkTokenCreate(request).execute();
            assertSuccess(response, "linkTokenCreate");
            LinkTokenCreateResponse body = response.body();
            log.info("Created Plaid link token for tenant {} user {}", tenantId, userId);
            return new LinkTokenResponse(body.getLinkToken(), body.getExpiration().toString());
        } catch (IOException e) {
            throw new PlaidException("Network error calling Plaid linkTokenCreate", "PLAID_NETWORK_ERROR", e);
        }
    }

    // ─── Exchange Public Token ────────────────────────────────────────────────────

    /**
     * Exchanges a short-lived public_token (from Plaid Link) for a permanent access_token.
     * Persists the PlaidItem and all linked accounts. Publishes BankAccountLinkedEvent.
     */
    @Transactional
    public LinkedAccountsResponse exchangePublicToken(UUID tenantId, String publicToken) {
        // 1. Exchange public token for access token
        ItemPublicTokenExchangeRequest exchangeRequest = new ItemPublicTokenExchangeRequest()
                .publicToken(publicToken);

        String accessToken;
        String plaidItemId;
        try {
            Response<ItemPublicTokenExchangeResponse> response =
                    plaidApi.itemPublicTokenExchange(exchangeRequest).execute();
            assertSuccess(response, "itemPublicTokenExchange");
            assert response.body() != null;
            accessToken = response.body().getAccessToken();
            plaidItemId = response.body().getItemId();
        } catch (IOException e) {
            throw new PlaidException("Network error calling Plaid itemPublicTokenExchange", "PLAID_NETWORK_ERROR", e);
        }

        // 2. Get institution details via itemGet
        String institutionId = null;
        String institutionName = null;
        try {
            Response<ItemGetResponse> itemResponse =
                    plaidApi.itemGet(new ItemGetRequest().accessToken(accessToken)).execute();
            if (itemResponse.isSuccessful() && itemResponse.body() != null) {
                institutionId = itemResponse.body().getItem().getInstitutionId();
            }
        } catch (IOException e) {
            log.warn("Could not fetch item details for tenant {}: {}", tenantId, e.getMessage());
        }

        // 3. Fetch institution name (best-effort)
        if (institutionId != null) {
            try {
                InstitutionsGetByIdRequest instRequest = new InstitutionsGetByIdRequest()
                        .institutionId(institutionId)
                        .countryCodes(List.of(CountryCode.US));
                Response<InstitutionsGetByIdResponse> instResponse =
                        plaidApi.institutionsGetById(instRequest).execute();
                if (instResponse.isSuccessful() && instResponse.body() != null) {
                    institutionName = instResponse.body().getInstitution().getName();
                }
            } catch (IOException e) {
                log.warn("Could not fetch institution name for {}: {}", institutionId, e.getMessage());
            }
        }

        // 4. Persist PlaidItem (access token stored as-is — TODO: encrypt at rest in production)
        PlaidItem item = PlaidItem.builder()
                .tenantId(tenantId)
                .plaidItemId(plaidItemId)
                .accessToken(accessToken)
                .institutionId(institutionId)
                .institutionName(institutionName != null ? institutionName : institutionId)
                .status("ACTIVE")
                .build();
        item = plaidItemRepository.save(item);

        // 5. Fetch and persist linked bank accounts
        List<PlaidAccount> savedAccounts = fetchAndSaveAccounts(tenantId, item, accessToken);

        // 6. Publish event
        eventPublisher.publishBankAccountLinked(new BankAccountLinkedEvent(
                tenantId, plaidItemId, institutionId,
                item.getInstitutionName(), savedAccounts.size()));

        log.info("Bank item {} linked for tenant {} ({} accounts)", plaidItemId, tenantId, savedAccounts.size());

        List<BankAccountDto> dtos = savedAccounts.stream().map(BankAccountDto::from).toList();
        return new LinkedAccountsResponse(dtos);
    }

    private List<PlaidAccount> fetchAndSaveAccounts(UUID tenantId, PlaidItem item, String accessToken) {
        try {
            Response<AccountsGetResponse> response =
                    plaidApi.accountsGet(new AccountsGetRequest().accessToken(accessToken)).execute();
            assertSuccess(response, "accountsGet");

            List<PlaidAccount> accounts = response.body().getAccounts().stream()
                    .map(a -> PlaidAccount.builder()
                            .tenantId(tenantId)
                            .plaidItem(item)
                            .plaidAccountId(a.getAccountId())
                            .name(a.getName())
                            .mask(a.getMask())
                            .type(a.getType() != null ? a.getType().getValue() : null)
                            .subtype(a.getSubtype() != null ? a.getSubtype().getValue() : null)
                            .active(true)
                            .build())
                    .toList();

            return plaidAccountRepository.saveAll(accounts);
        } catch (IOException e) {
            throw new PlaidException("Network error calling Plaid accountsGet", "PLAID_NETWORK_ERROR", e);
        }
    }

    // ─── List Accounts ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public LinkedAccountsResponse getLinkedAccounts(UUID tenantId) {
        List<BankAccountDto> accounts = plaidAccountRepository.findActiveByTenantId(tenantId)
                .stream().map(BankAccountDto::from).toList();
        return new LinkedAccountsResponse(accounts);
    }

    // ─── Unlink Account ──────────────────────────────────────────────────────────

    /**
     * Deactivates a single account. If it is the last active account in an item,
     * the item is also removed from Plaid and marked REMOVED.
     */
    @Transactional
    public void unlinkAccount(UUID tenantId, UUID accountId) {
        PlaidAccount account = plaidAccountRepository.findByIdAndTenantId(accountId, tenantId)
                .orElseThrow(() -> new BankAccountNotFoundException(
                        "Bank account not found: " + accountId));

        account.setActive(false);
        plaidAccountRepository.save(account);

        // Check if any other active accounts remain for this item
        PlaidItem item = account.getPlaidItem();
        List<PlaidAccount> remaining = plaidAccountRepository.findByPlaidItem_Id(item.getId())
                .stream().filter(PlaidAccount::getActive).toList();

        if (remaining.isEmpty()) {
            // Remove the item from Plaid (revokes access token)
            try {
                plaidApi.itemRemove(new ItemRemoveRequest().accessToken(item.getAccessToken())).execute();
            } catch (IOException e) {
                log.warn("Could not remove Plaid item {} (will still mark as REMOVED locally): {}",
                        item.getPlaidItemId(), e.getMessage());
            }
            item.setStatus("REMOVED");
            plaidItemRepository.save(item);
            log.info("Plaid item {} removed for tenant {}", item.getPlaidItemId(), tenantId);
        }

        log.info("Bank account {} unlinked for tenant {}", accountId, tenantId);
    }

    // ─── Initiate ACH Payment ────────────────────────────────────────────────────

    /**
     * Initiates an ACH bank transfer using the Plaid Transfer API.
     *
     * Flow:
     *  1. Look up PlaidAccount and its parent PlaidItem for the access token.
     *  2. Create a TransferAuthorization (Plaid risk assessment).
     *  3. If approved, create the Transfer.
     *  4. Persist AchPayment and publish Kafka event.
     */
    @Transactional
    public AchPaymentResponse initiateAchPayment(UUID tenantId, InitiateAchPaymentRequest request) {
        // Idempotency guard
        return achPaymentRepository.findByIdempotencyKey(request.idempotencyKey())
                .map(existing -> {
                    log.info("Returning existing ACH payment for idempotency key: {}", request.idempotencyKey());
                    return AchPaymentResponse.from(existing);
                })
                .orElseGet(() -> createNewAchPayment(tenantId, request));
    }

    private AchPaymentResponse createNewAchPayment(UUID tenantId, InitiateAchPaymentRequest request) {
        PlaidAccount account = plaidAccountRepository.findByIdAndTenantId(request.accountId(), tenantId)
                .orElseThrow(() -> new BankAccountNotFoundException(
                        "Bank account not found: " + request.accountId()));

        if (!account.getActive()) {
            throw new BankAccountNotFoundException("Bank account is no longer active: " + request.accountId());
        }

        PlaidItem item = account.getPlaidItem();
        String accessToken = item.getAccessToken();
        String plaidAccountId = account.getPlaidAccountId();

        // Amount in cents → dollars as string (Plaid Transfer API uses decimal dollars)
        String amountStr = String.format("%.2f", request.amount() / 100.0);

        // Step 1: Create transfer authorization
        TransferAuthorizationUserInRequest transferUser = new TransferAuthorizationUserInRequest()
                .legalName(request.accountHolderName());

        TransferAuthorizationCreateRequest authRequest = new TransferAuthorizationCreateRequest()
                .accessToken(accessToken)
                .accountId(plaidAccountId)
                .type(TransferType.DEBIT)
                .network(TransferNetwork.ACH)
                .amount(amountStr)
                .achClass(ACHClass.WEB)
                .user(transferUser);

        String authorizationId;
        try {
            Response<TransferAuthorizationCreateResponse> authResponse =
                    plaidApi.transferAuthorizationCreate(authRequest).execute();
            assertSuccess(authResponse, "transferAuthorizationCreate");

            TransferAuthorizationDecision decision =
                    authResponse.body().getAuthorization().getDecision();
            if (decision != TransferAuthorizationDecision.APPROVED) {
                throw new PlaidException(
                        "Transfer authorization declined (decision: " + decision + ")",
                        "TRANSFER_AUTHORIZATION_DECLINED");
            }
            authorizationId = authResponse.body().getAuthorization().getId();
        } catch (IOException e) {
            throw new PlaidException("Network error calling Plaid transferAuthorizationCreate",
                    "PLAID_NETWORK_ERROR", e);
        }

        // Step 2: Create the transfer
        TransferCreateRequest transferRequest = new TransferCreateRequest()
                .accessToken(accessToken)
                .accountId(plaidAccountId)
                .authorizationId(authorizationId)
                .description(request.description() != null ? request.description() : "EFaaS Transfer")
                .amount(amountStr);

        String plaidTransferId;
        try {
            Response<TransferCreateResponse> transferResponse =
                    plaidApi.transferCreate(transferRequest).execute();
            assertSuccess(transferResponse, "transferCreate");
            plaidTransferId = transferResponse.body().getTransfer().getId();
        } catch (IOException e) {
            throw new PlaidException("Network error calling Plaid transferCreate", "PLAID_NETWORK_ERROR", e);
        }

        // Step 3: Persist and publish
        AchPayment payment = AchPayment.builder()
                .tenantId(tenantId)
                .plaidAccountDbId(account.getId())
                .plaidTransferId(plaidTransferId)
                .plaidAuthorizationId(authorizationId)
                .idempotencyKey(request.idempotencyKey())
                .amount(request.amount())
                .currency(request.currency())
                .description(request.description())
                .status(AchPaymentStatus.PENDING)
                .build();

        payment = achPaymentRepository.save(payment);

        eventPublisher.publishAchPaymentInitiated(new AchPaymentInitiatedEvent(
                tenantId, payment.getId(), plaidTransferId, payment.getAmount(), payment.getCurrency()));

        log.info("ACH transfer {} initiated for tenant {} (amount: {} {})",
                plaidTransferId, tenantId, amountStr, request.currency());

        return AchPaymentResponse.from(payment);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private <T> void assertSuccess(Response<T> response, String operation) throws IOException {
        if (!response.isSuccessful() || response.body() == null) {
            String errorBody = response.errorBody() != null ? response.errorBody().string() : "unknown";
            throw new PlaidException(
                    "Plaid " + operation + " failed (HTTP " + response.code() + "): " + errorBody,
                    "PLAID_API_ERROR");
        }
    }
}
