package libraryagent.agent;

import java.util.Map;

/** One tool call made while answering, for verbose output and testing. */
public record ToolTrace(String tool, Map<String, Object> args, boolean success, String error) {
}
