/**
 * Observability: a sealed {@code AgentEvent} (started/completed/failed, model/tool calls),
 * {@code AuditSink}, and the default {@code RingBufferAuditSink}. Keys, summaries, and provenance
 * only — never data-plane values (ADR-0008). Durable sinks (CVVE ledger) are ring-2.
 *
 * <p>K0 skeleton — types are added in K1.
 */
package com.gamma.agentkernel.observe;
