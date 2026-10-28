package libraryagent.agent;

import libraryagent.agent.llm.LLMClient;
import libraryagent.agent.tools.AccountSummaryTool;
import libraryagent.agent.tools.CheckAvailabilityTool;
import libraryagent.agent.tools.CreateCuratedListTool;
import libraryagent.agent.tools.RecommendBooksTool;
import libraryagent.agent.tools.RecordFeedbackTool;
import libraryagent.agent.tools.GetBookDetailsTool;
import libraryagent.agent.tools.ManageReadingListTool;
import libraryagent.agent.tools.RegisterMemberTool;
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

    public static GenreTagger createTagger(LibraryServices services, LLMClient llm) {
        return new GenreTagger(llm, new PromptComposer(services.clock()), services.catalog());
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
        tools.register(new RegisterMemberTool(services.members()));
        tools.register(new ManageReadingListTool(services.readingLists(), services.catalog()));
        tools.register(new RecordFeedbackTool(services.feedback(), services.catalog()));
        tools.register(new RecommendBooksTool(services.feedback(), services.catalog()));
        tools.register(new CreateCuratedListTool(services.readingLists(), services.feedback(), services.catalog()));
        tools.register(new UndoLastActionTool());
        return new Planner(llm, tools, new PromptComposer(services.clock()), new MemoryManager(), maxSteps);
    }
}
