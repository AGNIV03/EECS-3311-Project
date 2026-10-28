package libraryagent.ui.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CliCommandParserTest {

    private final CliCommandParser parser = new CliCommandParser();

    @Test
    void commandWordsAreCaseInsensitive() {
        assertEquals(new CliAction("reserve", List.of("9781000000001")), parser.parse("  RESERVE 9781000000001 "));
    }

    @Test
    void quotedArgumentsStayTogether() {
        CliAction action = parser.parse("list add \"Summer reads\" 9781000000001");
        assertEquals(List.of("add", "Summer reads", "9781000000001"), action.args());
    }

    @Test
    void emptyQuotesProduceAnEmptyArgument() {
        assertEquals(List.of("create", ""), parser.parse("list create \"\"").args());
    }

    @Test
    void unknownInputIsSentToTheAssistantVerbatim() {
        CliAction action = parser.parse("  find me something like The Hobbit ");
        assertEquals("chat", action.kind());
        assertEquals("find me something like The Hobbit", action.arg(0));
    }

    @Test
    void quitIsAnAliasForExitAndBlankLinesAreEmpty() {
        assertEquals("exit", parser.parse("quit").kind());
        assertEquals("empty", parser.parse("   ").kind());
    }

    @Test
    void restJoinsRemainingArguments() {
        CliAction action = parser.parse("search hobbit  tolkien");
        assertEquals("hobbit tolkien", action.rest(0));
        assertEquals(null, action.rest(5));
    }
}
