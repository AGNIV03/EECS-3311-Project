package libraryagent.persistence;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.domain.ReadingPreferences;

/** Members and their reading preferences. */
public class MemberRepository implements Repository<Member> {

    private static final String SEPARATOR = "|";

    private final Database db;
    private final Map<String, Member> members = new LinkedHashMap<>();

    public MemberRepository(Database db) {
        this.db = db;
        db.query("SELECT m.member_id, m.name, m.email, m.type, m.is_librarian, "
                        + "p.genres, p.authors, p.max_pages, p.tracking_enabled "
                        + "FROM members m LEFT JOIN preferences p ON p.member_id = m.member_id "
                        + "ORDER BY m.member_id",
                rs -> {
                    ReadingPreferences prefs = rs.getString(6) == null
                            ? new ReadingPreferences()
                            : new ReadingPreferences(split(rs.getString(6)), split(rs.getString(7)),
                                    rs.getInt(8), rs.getInt(9) != 0);
                    return new Member(rs.getString(1), rs.getString(2), rs.getString(3),
                            MemberType.valueOf(rs.getString(4)), rs.getInt(5) != 0, prefs);
                })
                .forEach(m -> members.put(m.getMemberId(), m));
    }

    @Override
    public void save(Member member) {
        ReadingPreferences prefs = member.getPreferences();
        db.inTransaction(() -> {
            db.update("INSERT INTO members(member_id, name, email, type, is_librarian) VALUES (?, ?, ?, ?, ?) "
                            + "ON CONFLICT(member_id) DO UPDATE SET name = excluded.name, email = excluded.email, "
                            + "type = excluded.type, is_librarian = excluded.is_librarian",
                    member.getMemberId(), member.getName(), member.getEmail(), member.getType().name(),
                    member.isLibrarian() ? 1 : 0);
            db.update("INSERT INTO preferences(member_id, genres, authors, max_pages, tracking_enabled) "
                            + "VALUES (?, ?, ?, ?, ?) ON CONFLICT(member_id) DO UPDATE SET genres = excluded.genres, "
                            + "authors = excluded.authors, max_pages = excluded.max_pages, "
                            + "tracking_enabled = excluded.tracking_enabled",
                    member.getMemberId(), String.join(SEPARATOR, prefs.getFavoriteGenres()),
                    String.join(SEPARATOR, prefs.getFavoriteAuthors()), prefs.getMaxPages(),
                    prefs.isTrackingEnabled() ? 1 : 0);
        });
        members.put(member.getMemberId(), member);
    }

    @Override
    public Optional<Member> findById(String memberId) {
        return Optional.ofNullable(members.get(memberId.strip().toUpperCase()));
    }

    @Override
    public List<Member> findAll() {
        return List.copyOf(members.values());
    }

    public Optional<Member> findByEmail(String email) {
        return members.values().stream().filter(m -> m.getEmail().equalsIgnoreCase(email.strip())).findFirst();
    }

    public String nextId() {
        return db.nextId("M", 3);
    }

    private static List<String> split(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split("\\" + SEPARATOR)).filter(s -> !s.isBlank()).toList();
    }
}
