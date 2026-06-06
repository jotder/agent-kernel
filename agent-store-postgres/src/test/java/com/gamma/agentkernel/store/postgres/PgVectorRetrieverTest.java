package com.gamma.agentkernel.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamma.agentkernel.retrieve.ContextBudget;
import com.gamma.agentkernel.tool.CredibilityTier;
import com.gamma.agentkernel.tool.Evidence;

/**
 * Pure-helper + abstain-safety tests of {@link PgVectorRetriever} (no database): budget→top-k derivation,
 * the pgvector literal format, hit→{@link Evidence} mapping, and the abstain-safe guards that make RAG
 * optional grounding rather than a failure mode. The JDBC path is covered by
 * {@link PgVectorRetrieverJdbcTest}.
 */
class PgVectorRetrieverTest {

    @Test
    void topKFromBudgetMirrorsDocRetriever() {
        assertThat(PgVectorRetriever.topK(null)).isEqualTo(3);            // no budget → 3
        assertThat(PgVectorRetriever.topK(ContextBudget.standard(1000))).isEqualTo(3); // 700/200
        assertThat(PgVectorRetriever.topK(ContextBudget.standard(100))).isEqualTo(1);  // 70/200 → ≥1
    }

    @Test
    void formatsAPgvectorLiteral() {
        assertThat(PgVectorRetriever.vectorLiteral(new float[] {0.5f, -1.0f})).isEqualTo("[0.5,-1.0]");
        assertThat(PgVectorRetriever.vectorLiteral(new float[] {})).isEqualTo("[]");
    }

    @Test
    void mapsHitsToGroundingEvidenceWithClampedConfidence() {
        List<Evidence> evidence = PgVectorRetriever.toEvidence(List.of(
                new PgVectorRetriever.Hit("Clubhouse and pool are flagship amenities.", "doc:amenities#p1", 0.2),
                new PgVectorRetriever.Hit("RERA approval covers towers A and B only.", "doc:rera#p2", 1.5)),
                CredibilityTier.INDICATIVE, "doc");

        assertThat(evidence).hasSize(2);
        assertThat(evidence.get(0).value()).isEqualTo("Clubhouse and pool are flagship amenities.");
        assertThat(evidence.get(0).tier()).isEqualTo(CredibilityTier.INDICATIVE);
        assertThat(evidence.get(0).tierLabel()).isEqualTo("doc");
        assertThat(evidence.get(0).sourceRef()).isEqualTo("doc:amenities#p1");
        assertThat(evidence.get(0).confidence()).isCloseTo(0.8, within(1e-9)); // 1 - 0.2
        assertThat(evidence.get(1).confidence()).isEqualTo(0.0);               // 1 - 1.5 clamped to 0
    }

    @Test
    void abstainsWhenMisconfiguredOrQueryBlank() {
        EmbeddingModel embedder = text -> new float[] {1f, 2f, 3f};
        ContextBudget budget = ContextBudget.standard(1000);
        ThrowingDataSource noConnect = new ThrowingDataSource(); // fails if JDBC is ever touched

        // No DataSource → empty (constructor never connects).
        assertThat(new PgVectorRetriever(null, embedder, null).retrieve("amenities?", budget)).isEmpty();
        // No embedder → empty (without touching the DataSource).
        assertThat(new PgVectorRetriever(noConnect, null, null).retrieve("amenities?", budget)).isEmpty();
        // Blank / null query → empty.
        assertThat(PgVectorRetriever.create(noConnect, embedder).retrieve("  ", budget)).isEmpty();
        assertThat(PgVectorRetriever.create(noConnect, embedder).retrieve(null, budget)).isEmpty();
        // Embedder returns nothing → empty (no query to run).
        assertThat(PgVectorRetriever.create(noConnect, text -> new float[] {})
                .retrieve("amenities?", budget)).isEmpty();
    }
}
