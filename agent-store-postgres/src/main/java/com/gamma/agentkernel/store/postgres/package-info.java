/**
 * RING 2 — a pgvector-backed {@link com.gamma.agentkernel.retrieve.Retriever} companion. Implements the
 * ring-1 retrieval seam over PostgreSQL + pgvector, returning qualitative grounding
 * {@link com.gamma.agentkernel.tool.Evidence} (never figures; ADR-0001/0008). Compiles against
 * {@code java.sql} only — the JDBC driver is the consuming application's runtime concern. The
 * {@link com.gamma.agentkernel.store.postgres.EmbeddingModel} seam lives here, not in ring-1, because
 * vector retrieval needs an embedder the dependency-free core deliberately lacks.
 */
package com.gamma.agentkernel.store.postgres;
