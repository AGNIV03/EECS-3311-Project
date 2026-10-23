package libraryagent.command;

import libraryagent.domain.Book;
import libraryagent.domain.ReadingList;
import libraryagent.service.ReadingListService;

/** Moves a book to a new (zero-based) position in a reading list. Undo moves it back. */
public class MoveInReadingListCommand implements Command {

    private final ReadingListService service;
    private final ReadingList list;
    private final Book book;
    private final int to;
    private int from;

    public MoveInReadingListCommand(ReadingListService service, ReadingList list, Book book, int to) {
        this.service = service;
        this.list = list;
        this.book = book;
        this.to = to;
    }

    @Override
    public CommandResult execute() {
        from = service.moveBook(list, book, to);
        int shownPosition = list.indexOf(book) + 1;
        return CommandResult.ok("Moved \"" + book.getTitle() + "\" to position " + shownPosition + " in "
                + list.getName() + ".");
    }

    @Override
    public void undo() {
        service.moveBook(list, book, from);
    }

    @Override
    public String describe() {
        return "move of \"" + book.getTitle() + "\" in " + list.getName();
    }
}
