package com.gamma.agentkernel.store.postgres;

/**
 * The embedding seam vector retrieval needs but ring-1 deliberately lacks. Ring-1's {@code Retriever}
 * takes a {@code String} query and returns {@code Evidence}; turning that query into a vector for a
 * pgvector similarity search requires an embedder, which is a heavyweight, provider-specific concern. So
 * the seam lives here in ring-2 (supplied by the application — e.g. a LangChain4j embedding model), keeping
 * ring-1 dependency-free.
 *
 * <p>Whether to promote {@code EmbeddingModel} into ring-1 is a {@code 1.0} reshape candidate — revisit
 * once a second vector consumer exists (the rule of three). Until then it is intentionally ring-2 only.
 */
@FunctionalInterface
public interface EmbeddingModel {

    /** Embed {@code text} into a dense vector. May return {@code null}/empty to signal "cannot embed". */
    float[] embed(String text);
}
