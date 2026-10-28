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
    void sentencesStartingWithCommandWordsGoToTheAssistant() {
        assertEquals("chat", parser.parse("reserve two sci-fi books under 300 pages, whichever are available").kind());
        assertEquals("chat", parser.parse("undo that").kind());
        assertEquals("chat", parser.parse("return these two books").kind());
        assertEquals("chat", parser.parse("help me find something cozy").kind());
        assertEquals("chat", parser.parse("account balance please").kind());
        assertEquals("chat", parser.parse("list add \"Summer\" the hobbit please").kind());
    }

    @Test
    void wellFormedCommandsStayCommands() {
        assertEquals("reserve", parser.parse("reserve 978-1000000001").kind());
        assertEquals("return", parser.parse("return c0001").kind());
        assertEquals("renew", parser.parse("renew L0004").kind());
        assertEquals("cancel", parser.parse("cancel R0001").kind());
        assertEquals("checkout", parser.parse("checkout M002 C0005").kind());
        assertEquals("login", parser.parse("login LIB001").kind());
        assertEquals("list", parser.parse("list move \"Summer\" 9781000000001 2").kind());
        assertEquals("undo", parser.parse("undo").kind());
    }

    @Test
    void incompleteCommandsStayCommandsSoUsageIsShown() {
        assertEquals("checkout", parser.parse("checkout M002").kind());
        assertEquals("reserve", parser.parse("reserve").kind());
        assertEquals("list", parser.parse("list create").kind());
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
