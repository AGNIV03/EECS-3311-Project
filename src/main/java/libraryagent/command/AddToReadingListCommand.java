package libraryagent.command;

import libraryagent.domain.Book;
import libraryagent.domain.ReadingList;
import libraryagent.service.ReadingListService;

/** Adds a book to the end of a reading list. Undo removes it. */
public class AddToReadingListCommand implements Command {

    private final ReadingListService service;
    private final ReadingList list;
    private final Book book;

    public AddToReadingListCommand(ReadingListService service, ReadingList list, Book book) {
        this.service = service;
        this.list = list;
        this.book = book;
    }

    @Override
    public CommandResult execute() {
        service.addBook(list, book);
        return CommandResult.ok("Added \"" + book.getTitle() + "\" to " + list.getName() + ".");
    }

    @Override
    public void undo() {
        service.removeBook(list, book);
    }

    @Override
    public String describe() {
        return "addition of \"" + book.getTitle() + "\" to " + list.getName();
    }
}
