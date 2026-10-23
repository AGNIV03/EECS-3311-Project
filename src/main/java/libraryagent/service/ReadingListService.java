package libraryagent.service;

import java.util.List;
import java.util.Optional;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.ReadingList;
import libraryagent.persistence.ReadingListRepository;

/** Creating and editing members' reading lists. Positions are zero-based. */
public class ReadingListService {

    public static final int MAX_NAME_LENGTH = 60;

    private final ReadingListRepository lists;

    public ReadingListService(ReadingListRepository lists) {
        this.lists = lists;
    }

    public ReadingList createList(Member member, String name) {
        if (name == null || name.isBlank()) {
            throw new LibraryException("A reading list needs a name.");
        }
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new LibraryException("List names can be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (lists.findByName(member, trimmed).isPresent()) {
            throw new LibraryException("You already have a list called \"" + trimmed + "\".");
        }
        ReadingList list = new ReadingList(lists.nextId(), member, trimmed);
        lists.save(list);
        return list;
    }

    public void addBook(ReadingList list, Book book) {
        list.add(book);
        lists.save(list);
    }

    /** Puts a book back at a specific position (used to undo a removal). */
    public void insertBook(ReadingList list, Book book, int position) {
        list.insert(book, position);
        lists.save(list);
    }

    /** Removes a book and returns the position it had. */
    public int removeBook(ReadingList list, Book book) {
        int position = list.remove(book);
        lists.save(list);
        return position;
    }

    /** Moves a book and returns its previous position. */
    public int moveBook(ReadingList list, Book book, int position) {
        int from = list.move(book, position);
        lists.save(list);
        return from;
    }

    public List<ReadingList> listsFor(Member member) {
        return lists.findByMember(member);
    }

    public Optional<ReadingList> findList(Member member, String name) {
        return name == null ? Optional.empty() : lists.findByName(member, name);
    }

    public ReadingList requireList(Member member, String name) {
        return findList(member, name).orElseThrow(() -> new LibraryException(
                "You don't have a reading list called \"" + name + "\"."));
    }
}
