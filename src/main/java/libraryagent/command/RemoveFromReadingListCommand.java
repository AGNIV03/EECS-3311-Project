package libraryagent.command;

import libraryagent.domain.Book;
import libraryagent.domain.ReadingList;
import libraryagent.service.ReadingListService;

/** Removes a book from a reading list. Undo puts it back where it was. */
public class RemoveFromReadingListCommand implements Command {

    private final ReadingListService service;
    private final ReadingList list;
    private final Book book;
    private int position;

    public RemoveFromReadingListCommand(ReadingListService service, ReadingList list, Book book) {
        this.service = service;
        this.list = list;
        this.book = book;
    }

    @Override
    public CommandResult execute() {
        position = service.removeBook(list, book);
        return CommandResult.ok("Removed \"" + book.getTitle() + "\" from " + list.getName() + ".");
    }

    @Override
    public void undo() {
        service.insertBook(list, book, position);
    }

    @Override
    public String describe() {
        return "removal of \"" + book.getTitle() + "\" from " + list.getName();
    }
}
