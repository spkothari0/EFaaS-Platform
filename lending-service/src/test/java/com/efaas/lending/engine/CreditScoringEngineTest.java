package com.efaas.lending.engine;

import com.efaas.common.dto.FinancialProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CreditScoringEngineTest {

    private CreditScoringEngine engine;
    private UUID tenantId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        engine = new CreditScoringEngine();
        tenantId = UUID.randomUUID();
        accountId = UUID.randomUUID();
    }

    // ── DTI factor ────────────────────────────────────────────────────────────────

    @Test
    void score_lowDti_maxDtiPoints() {
        // Monthly payment = $500/12 ≈ $41.67, income = $10,000 → DTI ≈ 0.4%
        FinancialProfile profile = profile(10000.0, 10000.0, 10000.0, 30);
        int score = engine.score(profile, 50000L, 12); // $500 over 12 months
        assertThat(score).isGreaterThanOrEqualTo(CreditScoringEngine.APPROVE_THRESHOLD);
    }

    @Test
    void score_highDti_zeroDtiPoints() {
        // Monthly payment = $5000, income = $1000 → DTI = 500%
        FinancialProfile profile = profile(1000.0, 1000.0, 5000.0, 30);
        // DTI ≥ 50% → 0 pts for DTI
        int score = engine.score(profile, 500000L, 1);
        assertThat(score).isLessThan(CreditScoringEngine.APPROVE_THRESHOLD);
    }

    // ── Payment history factor ─────────────────────────────────────────────────

    @Test
    void score_highTransactionCount_maxHistoryPoints() {
        FinancialProfile profile = new FinancialProfile(tenantId, accountId,
                10000.0, 8000.0, null, 10000.0, 30, LocalDate.now());
        int score = engine.score(profile, 100000L, 12);
        assertThat(score).isGreaterThanOrEqualTo(CreditScoringEngine.APPROVE_THRESHOLD);
    }

    @Test
    void score_noTransactions_zeroHistoryPoints() {
        // DTI > 50% (0 pts), no tx history (0 pts), low income (0 pts) → well below threshold
        // monthly payment = $6001 on $1000 income → DTI > 600%
        FinancialProfile profile = new FinancialProfile(tenantId, accountId,
                1000.0, 800.0, null, 1000.0, 0, LocalDate.now());
        int score = engine.score(profile, 600100L, 1); // $6001 in 1 month
        assertThat(score).isLessThan(CreditScoringEngine.APPROVE_THRESHOLD);
    }

    // ── Balance stability factor ───────────────────────────────────────────────

    @Test
    void score_highAvailableRatio_maxStabilityPoints() {
        // available/current = 9000/10000 = 90% → 200 pts
        FinancialProfile profile = new FinancialProfile(tenantId, accountId,
                10000.0, 9000.0, null, 10000.0, 30, LocalDate.now());
        int score = engine.score(profile, 100000L, 12);
        assertThat(score).isGreaterThanOrEqualTo(CreditScoringEngine.APPROVE_THRESHOLD);
    }

    @Test
    void score_lowCurrentBalance_zeroStabilityPoints() {
        FinancialProfile profile = new FinancialProfile(tenantId, accountId,
                0.0, 0.0, null, 5000.0, 20, LocalDate.now());
        int score = engine.score(profile, 50000L, 12);
        assertThat(score).isLessThan(CreditScoringEngine.APPROVE_THRESHOLD);
    }

    // ── Approved / denied boundary ────────────────────────────────────────────

    @Test
    void score_excellentProfile_approved() {
        FinancialProfile profile = profile(15000.0, 12000.0, 15000.0, 35);
        int score = engine.score(profile, 120000L, 12);
        assertThat(score).isGreaterThanOrEqualTo(CreditScoringEngine.APPROVE_THRESHOLD);
    }

    @Test
    void score_weakProfile_denied() {
        FinancialProfile profile = new FinancialProfile(tenantId, accountId,
                200.0, 50.0, null, 500.0, 2, LocalDate.now());
        int score = engine.score(profile, 500000L, 6);
        assertThat(score).isLessThan(CreditScoringEngine.CONDITIONAL_THRESHOLD);
    }

    @Test
    void score_cappedAt1000() {
        FinancialProfile profile = profile(50000.0, 50000.0, 50000.0, 50);
        int score = engine.score(profile, 10000L, 60);
        assertThat(score).isLessThanOrEqualTo(1000);
    }

    @Test
    void score_neverNegative() {
        FinancialProfile profile = new FinancialProfile(tenantId, accountId,
                null, null, null, null, 0, LocalDate.now());
        int score = engine.score(profile, 999999999L, 1);
        assertThat(score).isGreaterThanOrEqualTo(0);
    }

    // ── helper ─────────────────────────────────────────────────────────────────

    private FinancialProfile profile(double current, double available, double income, int txCount) {
        return new FinancialProfile(tenantId, accountId, current, available, null, income, txCount, LocalDate.now());
    }
}
