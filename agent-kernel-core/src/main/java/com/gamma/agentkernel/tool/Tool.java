package com.gamma.agentkernel.tool;

import com.gamma.agentkernel.agent.AgentContext;
import com.gamma.agentkernel.error.AgentError;

import java.util.Map;

/**
 * A deterministic, in-process unit of computation/validation. The LLM orchestrates and narrates;
 * tools compute and validate (ADR-0003). Tools are <b>transport-free</b> — a plain in-process
 * interface, no wire/RPC assumptions — so an MCP/remote adapter can be added later as a ring-2 shim
 * without changing this contract.
 */
public interface Tool {

    /** This tool's declarative spec. */
    ToolSpec spec();

    /** Compute a result from {@code args} against the read-only {@code ctx}; throws {@link AgentError}. */
    ToolResult invoke(Map<String, Object> args, AgentContext ctx) throws AgentError;
}
