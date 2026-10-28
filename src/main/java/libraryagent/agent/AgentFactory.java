package libraryagent.agent;

import libraryagent.agent.llm.LLMClient;
import libraryagent.agent.tools.AccountSummaryTool;
import libraryagent.agent.tools.CheckAvailabilityTool;
import libraryagent.agent.tools.GetBookDetailsTool;
import libraryagent.agent.tools.RenewLoanTool;
import libraryagent.agent.tools.ReserveBookTool;
import libraryagent.agent.tools.ReturnBookTool;
import libraryagent.agent.tools.SearchCatalogTool;
import libraryagent.agent.tools.ToolManager;
import libraryagent.agent.tools.UndoLastActionTool;
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
        tools.register(new ReserveBookTool(services.catalog(), services.circulation()));
        tools.register(new ReturnBookTool(services.circulation()));
        tools.register(new RenewLoanTool(services.circulation()));
        tools.register(new AccountSummaryTool(services.circulation(), services.readingLists()));
        tools.register(new UndoLastActionTool());
        return new Planner(llm, tools, new PromptComposer(services.clock()), new MemoryManager(), maxSteps);
    }
}
