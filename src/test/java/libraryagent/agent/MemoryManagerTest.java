package libraryagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import libraryagent.agent.llm.Message;
import libraryagent.agent.llm.MessagePart;
import org.junit.jupiter.api.Test;

class MemoryManagerTest {

    private static Message answer(String text) {
        return Message.assistant(List.of(new MessagePart.Text(text)), "provider-original");
    }

    @Test
    void sessionsAreSeparate() {
        MemoryManager memory = new MemoryManager();
        memory.appendTurn("a", List.of(Message.user("hi"), answer("hello")), "hi", "hello");
        assertEquals(2, memory.history("a").size());
        assertTrue(memory.history("b").isEmpty());
    }

    @Test
    void longConversationRestartsWithPlainTextRecap() {
        MemoryManager memory = new MemoryManager();
        for (int i = 1; i <= MemoryManager.MAX_USER_TURNS; i++) {
            memory.appendTurn("a", List.of(Message.user("question " + i), answer("answer " + i)),
                    "question " + i, "answer " + i);
        }
        List<Message> history = memory.history("a");
        assertEquals(2, history.size());
        String recap = history.get(0).text();
        assertTrue(recap.contains("User: question 30\nAssistant: answer 30"));
        assertTrue(recap.contains("question 27"));
        assertTrue(!recap.contains("question 26\n"), recap);
        assertNull(history.get(1).providerMessage(), "no provider reasoning blocks survive a restart");
    }
}
