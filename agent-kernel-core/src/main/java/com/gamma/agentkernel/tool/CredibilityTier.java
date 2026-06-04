package com.gamma.agentkernel.tool;

/**
 * Credibility ranking of a piece of {@link Evidence}, highest trust first. An enum for the common
 * vocabulary; {@link Evidence#tierLabel()} is the {@code 0.x} String escape hatch for app-specific
 * tiers that don't fit (e.g. CxO's richer provenance). Whether to promote this to an app-extensible
 * interface is revisited at {@code 1.0}, once a 2nd consumer has exercised real tier vocabularies
 * (ADR-0004).
 */
public enum CredibilityTier {
    /** Source of record; the authoritative system for this fact. */
    AUTHORITATIVE,
    /** Official but not the system of record (published figure, official doc). */
    OFFICIAL,
    /** Indicative/estimated from a credible source. */
    INDICATIVE,
    /** Derived/computed by a deterministic tool from other evidence. */
    DERIVED,
    /** Supplied by the user in the request. */
    USER_PROVIDED,
    /** An assumption or default with no stronger backing. */
    ASSUMPTION
}
