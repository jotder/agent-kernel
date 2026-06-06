package com.gamma.agentkernel.store.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.gamma.agentkernel.retrieve.ContextBudget;
import com.gamma.agentkernel.tool.CredibilityTier;
import com.gamma.agentkernel.tool.Evidence;

/**
 * Exercises {@link PgVectorRetriever}'s JDBC path against a real driver (H2): parameter binding (the vector
 * literal as the first {@code ?}, the top-k as the second), {@code ResultSet} → {@link Evidence} mapping,
 * ordering, and the budget-derived {@code LIMIT}. The pgvector {@code <=>} operator itself is a SQL string
 * (verified against a live store in the consumer/deployment); here an H2-valid distance expression stands in
 * so the surrounding plumbing is covered offline.
 */
class PgVectorRetrieverJdbcTest {

    /** H2-valid stand-in for the pgvector query: distance = |len(query-literal) − len(stored embedding)|. */
    private static final String H2_SQL =
            "SELECT content, source_ref, ABS(LENGTH(?) - LENGTH(embedding)) AS distance "
                    + "FROM doc_chunk ORDER BY distance ASC LIMIT ?";

    private final JdbcDataSource dataSource = new JdbcDataSource();
    private final EmbeddingModel embedder = text -> new float[] {1f, 2f, 3f}; // literal "[1.0,2.0,3.0]" (len 13)

    @BeforeEach
    void seed() throws SQLException {
        dataSource.setURL("jdbc:h2:mem:ragtest;DB_CLOSE_DELAY=-1");
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("DROP TABLE IF EXISTS doc_chunk");
            s.execute("CREATE TABLE doc_chunk (content VARCHAR(512), source_ref VARCHAR(256), "
                    + "embedding VARCHAR(64))");
            s.execute("INSERT INTO doc_chunk VALUES "
                    + "('Clubhouse and rooftop pool are flagship amenities.', 'doc:amenities#p1', '1234567890123'),"
                    + "('Possession expected Dec 2026 per builder brochure.', 'doc:brochure#p3', '123'),"
                    + "('RERA approval covers towers A and B only.', 'doc:rera#p2', '12')");
        }
    }

    @Test
    void retrievesRankedQualitativeChunksAsEvidence() {
        PgVectorRetriever retriever = new PgVectorRetriever(dataSource, embedder,
                new PgVectorRetriever.Config(H2_SQL, CredibilityTier.INDICATIVE, "doc"));

        List<Evidence> evidence = retriever.retrieve("what amenities are offered?", ContextBudget.standard(1000));

        assertThat(evidence).hasSize(3);
        // Closest stored embedding length (13) to the query literal length (13) ranks first.
        Evidence top = evidence.get(0);
        assertThat(top.value()).isEqualTo("Clubhouse and rooftop pool are flagship amenities.");
        assertThat(top.sourceRef()).isEqualTo("doc:amenities#p1");
        assertThat(top.tier()).isEqualTo(CredibilityTier.INDICATIVE);
        assertThat(top.tierLabel()).isEqualTo("doc");
        assertThat(evidence).extracting(e -> (String) e.sourceRef())
                .containsExactly("doc:amenities#p1", "doc:brochure#p3", "doc:rera#p2");
        // Grounding is qualitative text — never a figure (ADR-0001).
        assertThat(top.value()).isInstanceOf(String.class);
    }

    @Test
    void honoursTheBudgetDerivedLimit() {
        PgVectorRetriever retriever = new PgVectorRetriever(dataSource, embedder,
                new PgVectorRetriever.Config(H2_SQL, CredibilityTier.INDICATIVE, "doc"));

        // standard(300) → retrievedTokens 210 → top-k = 210/200 = 1
        List<Evidence> evidence = retriever.retrieve("amenities?", ContextBudget.standard(300));

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).sourceRef()).isEqualTo("doc:amenities#p1");
    }
}
