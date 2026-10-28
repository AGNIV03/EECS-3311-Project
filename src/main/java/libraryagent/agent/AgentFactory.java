package libraryagent.agent;

import libraryagent.agent.llm.LLMClient;
import libraryagent.agent.tools.CheckAvailabilityTool;
import libraryagent.agent.tools.GetBookDetailsTool;
import libraryagent.agent.tools.SearchCatalogTool;
import libraryagent.agent.tools.ToolManager;
import libraryagent.controller.LibraryServices;

/** Builds the agent: the planner with its prompt composer, memory and registered tools. */
public final class AgentFactory {

    private AgentFactory() {
    }

    public static Planner createPlanner(LibraryServices services, LLMClient llm, int maxSteps) {
        ToolManager tools = new ToolManager();
        tools.register(new SearchCatalogTool(services.catalog()));
        tools.register(new GetBookDetailsTool(services.catalog()));
        tools.register(new CheckAvailabilityTool(services.catalog(), services.circulation()));
        return new Planner(llm, tools, new PromptComposer(services.clock()), new MemoryManager(), maxSteps);
    }
}
