package libraryagent.persistence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import libraryagent.domain.Book;
import libraryagent.domain.Member;
import libraryagent.domain.ReadingList;

/** Reading lists with their ordered books. */
public class ReadingListRepository implements Repository<ReadingList> {

    private final Database db;
    private final Map<String, ReadingList> lists = new LinkedHashMap<>();

    public ReadingListRepository(Database db, BookRepository books, MemberRepository members) {
        this.db = db;
        db.query("SELECT list_id, member_id, name FROM reading_lists ORDER BY list_id",
                        rs -> new ReadingList(rs.getString(1), members.findById(rs.getString(2)).orElseThrow(),
                                rs.getString(3)))
                .forEach(l -> lists.put(l.getListId(), l));
        db.query("SELECT list_id, isbn FROM reading_list_items ORDER BY list_id, position", rs -> {
            Book book = books.findById(rs.getString(2)).orElseThrow();
            lists.get(rs.getString(1)).add(book);
            return book;
        });
    }

    @Override
    public void save(ReadingList list) {
        db.inTransaction(() -> {
            db.update("INSERT INTO reading_lists(list_id, member_id, name) VALUES (?, ?, ?) "
                            + "ON CONFLICT(list_id) DO UPDATE SET name = excluded.name",
                    list.getListId(), list.getOwner().getMemberId(), list.getName());
            db.update("DELETE FROM reading_list_items WHERE list_id = ?", list.getListId());
            List<Book> books = list.getBooks();
            for (int i = 0; i < books.size(); i++) {
                db.update("INSERT INTO reading_list_items(list_id, isbn, position) VALUES (?, ?, ?)",
                        list.getListId(), books.get(i).getIsbn(), i);
            }
        });
        lists.put(list.getListId(), list);
    }

    @Override
    public Optional<ReadingList> findById(String listId) {
        return Optional.ofNullable(lists.get(listId));
    }

    @Override
    public List<ReadingList> findAll() {
        return List.copyOf(lists.values());
    }

    public List<ReadingList> findByMember(Member member) {
        return lists.values().stream().filter(l -> l.getOwner().equals(member)).toList();
    }

    public Optional<ReadingList> findByName(Member member, String name) {
        return findByMember(member).stream().filter(l -> l.getName().equalsIgnoreCase(name.strip())).findFirst();
    }

    public String nextId() {
        return db.nextId("RL", 3);
    }
}
