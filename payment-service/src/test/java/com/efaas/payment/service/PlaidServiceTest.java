package com.efaas.payment.service;

import com.efaas.payment.dto.plaid.*;
import com.efaas.payment.entity.*;
import com.efaas.payment.exception.BankAccountNotFoundException;
import com.efaas.payment.exception.PlaidException;
import com.efaas.payment.kafka.PlaidEventPublisher;
import com.efaas.payment.repository.AchPaymentRepository;
import com.efaas.payment.repository.PlaidAccountRepository;
import com.efaas.payment.repository.PlaidItemRepository;
import com.plaid.client.model.*;
import com.plaid.client.request.PlaidApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import retrofit2.Call;
import retrofit2.Response;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlaidServiceTest {

    @Mock PlaidApi plaidApi;
    @Mock PlaidItemRepository plaidItemRepository;
    @Mock PlaidAccountRepository plaidAccountRepository;
    @Mock AchPaymentRepository achPaymentRepository;
    @Mock PlaidEventPublisher eventPublisher;

    @InjectMocks PlaidService plaidService;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(plaidService, "webhookUrl", "http://localhost:8082/webhooks/plaid");
    }

    // ─── createLinkToken ─────────────────────────────────────────────────────────

    @Test
    void createLinkToken_success() throws Exception {
        LinkTokenCreateResponse body = new LinkTokenCreateResponse()
                .linkToken("link-sandbox-abc123")
                .expiration(java.time.OffsetDateTime.now().plusHours(4));

        Call<LinkTokenCreateResponse> call = mockCall(body);
        when(plaidApi.linkTokenCreate(any())).thenReturn(call);

        LinkTokenResponse result = plaidService.createLinkToken(tenantId, "user-123");

        assertThat(result.linkToken()).isEqualTo("link-sandbox-abc123");
        verify(plaidApi).linkTokenCreate(any());
    }

    // ─── exchangePublicToken ──────────────────────────────────────────────────────

    @Test
    void exchangePublicToken_persistsItemAndAccounts() throws Exception {
        // Exchange
        ItemPublicTokenExchangeResponse exchangeBody = new ItemPublicTokenExchangeResponse()
                .accessToken("access-sandbox-token")
                .itemId("item-abc123");
        Call<ItemPublicTokenExchangeResponse> exchangeCall = mockCall(exchangeBody);
        when(plaidApi.itemPublicTokenExchange(any())).thenReturn(exchangeCall);

        // ItemGet
        ItemWithConsentFields plaidItem = new ItemWithConsentFields().institutionId("ins_3");
        ItemGetResponse itemGetBody = new ItemGetResponse().item(plaidItem);
        Call<ItemGetResponse> itemGetCall = mockCall(itemGetBody);
        when(plaidApi.itemGet(any())).thenReturn(itemGetCall);

        // InstitutionsGetById
        Institution institution = new Institution().name("Chase");
        InstitutionsGetByIdResponse instBody = new InstitutionsGetByIdResponse().institution(institution);
        Call<InstitutionsGetByIdResponse> instCall = mockCall(instBody);
        when(plaidApi.institutionsGetById(any())).thenReturn(instCall);

        // AccountsGet
        AccountBase acc = new AccountBase()
                .accountId("acc-1")
                .name("Checking")
                .mask("1234")
                .type(AccountType.DEPOSITORY)
                .subtype(AccountSubtype.CHECKING);
        AccountsGetResponse accountsBody = new AccountsGetResponse().accounts(List.of(acc));
        Call<AccountsGetResponse> accountsCall = mockCall(accountsBody);
        when(plaidApi.accountsGet(any())).thenReturn(accountsCall);

        // Repository stubs
        PlaidItem savedItem = PlaidItem.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .plaidItemId("item-abc123").accessToken("access-sandbox-token")
                .institutionName("Chase").status("ACTIVE").build();
        when(plaidItemRepository.save(any())).thenReturn(savedItem);

        PlaidAccount savedAccount = PlaidAccount.builder()
                .id(UUID.randomUUID()).tenantId(tenantId).plaidItem(savedItem)
                .plaidAccountId("acc-1").name("Checking").mask("1234")
                .type("depository").subtype("checking").active(true).build();
        when(plaidAccountRepository.saveAll(any())).thenReturn(List.of(savedAccount));

        LinkedAccountsResponse result = plaidService.exchangePublicToken(tenantId, "public-sandbox-xyz");

        assertThat(result.accounts()).hasSize(1);
        assertThat(result.accounts().get(0).name()).isEqualTo("Checking");
        verify(plaidItemRepository).save(any());
        verify(plaidAccountRepository).saveAll(any());
        verify(eventPublisher).publishBankAccountLinked(any());
    }

    // ─── getLinkedAccounts ───────────────────────────────────────────────────────

    @Test
    void getLinkedAccounts_returnsActiveTenantAccounts() {
        PlaidItem item = PlaidItem.builder().id(UUID.randomUUID()).build();
        PlaidAccount a1 = PlaidAccount.builder().id(UUID.randomUUID()).tenantId(tenantId)
                .plaidItem(item).name("Savings").active(true).build();
        PlaidAccount a2 = PlaidAccount.builder().id(UUID.randomUUID()).tenantId(tenantId)
                .plaidItem(item).name("Checking").active(true).build();

        when(plaidAccountRepository.findActiveByTenantId(tenantId)).thenReturn(List.of(a1, a2));

        LinkedAccountsResponse result = plaidService.getLinkedAccounts(tenantId);

        assertThat(result.accounts()).hasSize(2);
    }

    // ─── unlinkAccount ───────────────────────────────────────────────────────────

    @Test
    void unlinkAccount_notFound_throws() {
        when(plaidAccountRepository.findByIdAndTenantId(any(), any())).thenReturn(Optional.empty());
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> plaidService.unlinkAccount(tenantId, accountId))
                .isInstanceOf(BankAccountNotFoundException.class);
    }

    // ─── initiateAchPayment ──────────────────────────────────────────────────────

    @Test
    void initiateAchPayment_idempotency_returnsExisting() {
        AchPayment existing = AchPayment.builder()
                .id(UUID.randomUUID()).tenantId(tenantId)
                .amount(5000L).currency("usd").status(AchPaymentStatus.PENDING)
                .idempotencyKey("key-1").build();
        when(achPaymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        InitiateAchPaymentRequest request = new InitiateAchPaymentRequest(
                UUID.randomUUID(), 5000L, "usd", "desc", "Test User", "key-1");
        AchPaymentResponse result = plaidService.initiateAchPayment(tenantId, request);

        assertThat(result.id()).isEqualTo(existing.getId());
        verifyNoInteractions(plaidApi);
    }

    @Test
    void initiateAchPayment_authorizationDeclined_throws() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(achPaymentRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());

        PlaidItem item = PlaidItem.builder().id(UUID.randomUUID())
                .accessToken("access-sandbox-token").build();
        PlaidAccount account = PlaidAccount.builder().id(accountId).tenantId(tenantId)
                .plaidItem(item).plaidAccountId("acc-1").active(true).build();
        when(plaidAccountRepository.findByIdAndTenantId(accountId, tenantId))
                .thenReturn(Optional.of(account));

        TransferAuthorization authorization = new TransferAuthorization()
                .id("auth-1")
                .decision(TransferAuthorizationDecision.DECLINED);
        TransferAuthorizationCreateResponse authBody =
                new TransferAuthorizationCreateResponse().authorization(authorization);
        Call<TransferAuthorizationCreateResponse> authCall = mockCall(authBody);
        when(plaidApi.transferAuthorizationCreate(any())).thenReturn(authCall);

        InitiateAchPaymentRequest request = new InitiateAchPaymentRequest(
                accountId, 5000L, "usd", "desc", "Test User", "key-new");

        assertThatThrownBy(() -> plaidService.initiateAchPayment(tenantId, request))
                .isInstanceOf(PlaidException.class)
                .hasMessageContaining("declined");
    }

    // ─── Helper ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> Call<T> mockCall(T body) throws Exception {
        Call<T> call = mock(Call.class);
        when(call.execute()).thenReturn(Response.success(body));
        return call;
    }
}
