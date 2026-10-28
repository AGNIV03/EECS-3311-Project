package libraryagent.agent.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import libraryagent.agent.AgentContext;
import libraryagent.agent.llm.ToolSpec;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.domain.ReadingPreferences;
import libraryagent.service.MemberService;

/** Creates an account from details gathered in conversation, saves preferences and logs the new member in. */
public class RegisterMemberTool extends AbstractTool {

    private final MemberService members;

    public RegisterMemberTool(MemberService members) {
        this.members = members;
    }

    @Override
    public String name() {
        return "registerMember";
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(name(),
                "Register a new library member and log them in. Before calling, ask for any missing required "
                        + "detail (name, email, member type). Favourite genres must be genres from the catalogue: "
                        + "if the user names one that isn't (e.g. 'sci-fi'), map it to the closest real subgenre "
                        + "or ask. Preferences are optional. Only for users who are not logged in.",
                properties(
                        "name", stringProperty("Full name."),
                        "email", stringProperty("Email address."),
                        "memberType", enumProperty("standard, or student for students.", "standard", "student"),
                        "favoriteGenres", stringArrayProperty("Catalogue genres the member enjoys."),
                        "favoriteAuthors", stringArrayProperty("Authors the member enjoys."),
                        "maxPages", integerProperty("Preferred maximum book length in pages, if they gave one.")),
                List.of("name", "email", "memberType"));
    }

    @Override
    protected List<String> validate(Map<String, Object> args) {
        List<String> errors = new ArrayList<>();
        requireString(args, "name", errors);
        requireString(args, "email", errors);
        requireString(args, "memberType", errors);
        checkInteger(args, "maxPages", 1, errors);
        return errors;
    }

    @Override
    protected ToolResult doExecute(Map<String, Object> args, AgentContext context) {
        context.getMember().filter(m -> !m.isLibrarian()).ifPresent(m -> {
            throw new LibraryException("You are already logged in as " + m.getName() + " (" + m.getMemberId()
                    + "), so a new account can't be created in this session.");
        });
        // Check the preferences first so a bad genre doesn't leave a half-created account.
        List<String> genres = members.canonicalGenres(strings(args, "favoriteGenres"));
        Integer maxPages = integer(args, "maxPages");

        Member member = members.register(string(args, "name"), string(args, "email"),
                MemberType.parse(string(args, "memberType")));
        members.updatePreferences(member, new ReadingPreferences(genres, strings(args, "favoriteAuthors"),
                maxPages == null ? 0 : maxPages, true));
        if (context.getMember().isEmpty()) {
            context.login(member);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("memberId", member.getMemberId());
        data.put("name", member.getName());
        data.put("memberType", member.getType().name().toLowerCase());
        data.put("borrowLimit", member.getBorrowLimit());
        data.put("favouriteGenres", member.getPreferences().getFavoriteGenres());
        data.put("favouriteAuthors", member.getPreferences().getFavoriteAuthors());
        data.put("loggedIn", context.getMember().map(m -> m.equals(member)).orElse(false));
        return ToolResult.ok(data);
    }
}
