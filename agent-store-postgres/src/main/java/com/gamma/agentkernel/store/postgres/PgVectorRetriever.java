package com.gamma.agentkernel.store.postgres;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import com.gamma.agentkernel.retrieve.ContextBudget;
import com.gamma.agentkernel.retrieve.Retriever;
import com.gamma.agentkernel.tool.CredibilityTier;
import com.gamma.agentkernel.tool.Evidence;

/**
 * A pgvector-backed {@link Retriever}: it embeds the query, runs a nearest-neighbour search over a
 * PostgreSQL + pgvector table, and returns the matching chunks as qualitative grounding {@link Evidence}
 * — text snippets with a {@code source_ref} locator, <b>never figures</b> (ADR-0001/0008). It compiles
 * against {@code java.sql} only; the JDBC driver is the consuming application's runtime concern.
 *
 * <p><b>Abstain-safe.</b> Like {@link Retriever#NONE}, RAG is optional grounding — a missing dependency,
 * blank query, un-embeddable query, or store error yields an empty result rather than failing the agent.
 * The constructor never touches the database.
 *
 * <p>The default SQL targets a {@code doc_chunk(content, source_ref, embedding)} table and orders by
 * cosine distance ({@code <=>}); supply a {@link Config} to point at a different schema. The query vector
 * is bound as a pgvector literal (first {@code ?}); the {@code top-k} (derived from the budget) is the
 * second {@code ?}. Confidence is reported as {@code 1 - distance} clamped to {@code [0,1]}.
 */
public final class PgVectorRetriever implements Retriever {

    /** Default nearest-neighbour query over {@code doc_chunk} ordered by cosine distance. */
    static final String DEFAULT_SQL =
            "SELECT content, source_ref, (embedding <=> CAST(? AS vector)) AS distance "
                    + "FROM doc_chunk ORDER BY distance ASC LIMIT ?";

    private final DataSource dataSource;
    private final EmbeddingModel embedder;
    private final Config config;

    public PgVectorRetriever(DataSource dataSource, EmbeddingModel embedder, Config config) {
        this.dataSource = dataSource;
        this.embedder = embedder;
        this.config = (config == null) ? Config.defaults() : config;
    }

    /** A retriever over the default {@code doc_chunk} schema (tier INDICATIVE, tierLabel "doc"). */
    public static PgVectorRetriever create(DataSource dataSource, EmbeddingModel embedder) {
        return new PgVectorRetriever(dataSource, embedder, Config.defaults());
    }

    @Override
    public List<Evidence> retrieve(String query, ContextBudget budget) {
        if (dataSource == null || embedder == null || query == null || query.isBlank()) {
            return List.of();
        }
        float[] vector;
        try {
            vector = embedder.embed(query);
        } catch (RuntimeException e) {
            return List.of(); // embedder unavailable/failed — abstain rather than error
        }
        if (vector == null || vector.length == 0) {
            return List.of();
        }

        int k = topK(budget);
        List<Hit> hits = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(config.sql())) {
            statement.setString(1, vectorLiteral(vector));
            statement.setInt(2, k);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    hits.add(new Hit(rs.getString("content"), rs.getString("source_ref"),
                            rs.getDouble("distance")));
                }
            }
        } catch (SQLException e) {
            return List.of(); // RAG is optional grounding — never fail the agent on a store error
        }
        return toEvidence(hits, config.tier(), config.tierLabel());
    }

    // ── pure helpers (no JDBC) ───────────────────────────────────────────────────

    /** Top-k from the budget: ~200 tokens/chunk, ≥1; default 3 when no budget (mirrors DocRetriever). */
    static int topK(ContextBudget budget) {
        return (budget == null) ? 3 : Math.max(1, budget.retrievedTokens() / 200);
    }

    /** Format a vector as a pgvector literal, e.g. {@code [0.5,-1.0]}. */
    static String vectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    /** Map ranked hits to grounding evidence; confidence = {@code 1 - distance} clamped to [0,1]. */
    static List<Evidence> toEvidence(List<Hit> hits, CredibilityTier tier, String tierLabel) {
        List<Evidence> out = new ArrayList<>(hits.size());
        for (Hit h : hits) {
            double confidence = Math.max(0.0, Math.min(1.0, 1.0 - h.distance()));
            out.add(new Evidence(h.content(), tier, tierLabel, h.sourceRef(), confidence, null));
        }
        return out;
    }

    /** One row of the similarity search. */
    record Hit(String content, String sourceRef, double distance) {
    }

    /**
     * Where and how to search: the SQL (two ordered {@code ?}: the vector literal, then the top-k limit),
     * and the {@link CredibilityTier}/label stamped on every returned snippet.
     */
    public record Config(String sql, CredibilityTier tier, String tierLabel) {

        /** The default {@code doc_chunk} schema, tier INDICATIVE, tierLabel "doc". */
        public static Config defaults() {
            return new Config(DEFAULT_SQL, CredibilityTier.INDICATIVE, "doc");
        }
    }
}
