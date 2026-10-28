package libraryagent.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import libraryagent.domain.Book;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.copy.BookCopy;
import libraryagent.domain.genre.GenreComponent;
import libraryagent.domain.genre.Subgenre;
import libraryagent.persistence.BookRepository;
import libraryagent.persistence.GenreRepository;

/** Searching and maintaining the catalogue. */
public class CatalogService {

    public static final int MAX_COPIES_PER_TITLE = 20;

    private final BookRepository books;
    private final GenreRepository genres;

    public CatalogService(BookRepository books, GenreRepository genres) {
        this.books = books;
        this.genres = genres;
    }

    /** Books matching every given field, best keyword matches first, then by title. */
    public List<Book> search(BookQuery query) {
        List<Book> pool = BookQuery.blank(query.genre()) ? books.findAll() : genreBooks(query.genre());
        record Scored(Book book, int score) {
        }
        List<Scored> matches = new ArrayList<>();
        for (Book book : pool) {
            if (!containsIgnoreCase(book.getTitle(), query.title())
                    || !containsIgnoreCase(book.getAuthor(), query.author())
                    || (query.maxPages() != null && book.getPageCount() > query.maxPages())) {
                continue;
            }
            int score = keywordScore(book, query.keywords());
            if (query.keywords().isEmpty() || score > 0) {
                matches.add(new Scored(book, score));
            }
        }
        return matches.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed()
                        .thenComparing(s -> s.book().getTitle()))
                .map(Scored::book)
                .toList();
    }

    public Optional<Book> findByIsbn(String isbn) {
        return books.findById(normalizeIsbn(isbn));
    }

    public Book requireBook(String isbn) {
        return findByIsbn(isbn).orElseThrow(() -> new LibraryException("No book with ISBN " + isbn + " is in the catalogue."));
    }

    public Optional<BookCopy> findCopy(String copyId) {
        return books.findCopy(copyId);
    }

    public Availability availability(Book book) {
        return new Availability(book.availableCopies().size(), book.getCopies().size(),
                book.getHoldQueue().size());
    }

    /**
     * Adds a new title with the given number of copies, filed under the given subgenres.
     * Everything is validated before anything is saved.
     */
    public Book addBook(Book book, int copies, List<String> subgenres) {
        String isbn = normalizeIsbn(book.getIsbn());
        if (!isbn.matches("\\d{10}|\\d{13}")) {
            throw new LibraryException("ISBN must have 10 or 13 digits.");
        }
        if (books.findById(isbn).isPresent()) {
            throw new LibraryException("A book with ISBN " + isbn + " is already in the catalogue.");
        }
        if (book.getTitle().isBlank() || book.getAuthor().isBlank()) {
            throw new LibraryException("Title and author are required.");
        }
        if (book.getPageCount() <= 0) {
            throw new LibraryException("Page count must be a positive number.");
        }
        if (copies < 1 || copies > MAX_COPIES_PER_TITLE) {
            throw new LibraryException("Number of copies must be between 1 and " + MAX_COPIES_PER_TITLE + ".");
        }
        if (subgenres.isEmpty()) {
            throw new LibraryException("Choose at least one subgenre.");
        }
        List<Subgenre> targets = subgenres.stream().map(this::requireSubgenre).toList();

        Book saved = new Book(isbn, book.getTitle().strip(), book.getAuthor().strip(), book.getPageCount(),
                book.getDescription().strip());
        for (int i = 0; i < copies; i++) {
            saved.addCopy(new BookCopy(books.nextCopyId(), saved));
        }
        targets.forEach(s -> s.addBook(saved));
        books.save(saved);
        return saved;
    }

    /** Files an existing book under another subgenre. */
    public void assignGenre(Book book, String subgenre) {
        requireSubgenre(subgenre).addBook(book);
        books.save(book);
    }

    /**
     * Real catalogue books that fit the member's taste profile, best first. Books matching a liked
     * genre or author score higher; available books break ties. Borrowed and disliked books are
     * never returned, and books longer than the preferred maximum are skipped. Returns an empty
     * list when the profile has nothing to go on.
     */
    public List<Book> recommendCandidates(Member member, TasteProfile profile, int n) {
        if (profile.isEmpty()) {
            return List.of();
        }
        record Scored(Book book, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Book book : books.findAll()) {
            if (profile.excludedIsbns().contains(book.getIsbn())
                    || (profile.maxPages() > 0 && book.getPageCount() > profile.maxPages())) {
                continue;
            }
            int score = 0;
            for (String genre : profile.likedGenres()) {
                if (genres.find(genre).map(g -> g.contains(book)).orElse(false)) {
                    score += 3;
                }
            }
            for (String author : profile.likedAuthors()) {
                if (containsIgnoreCase(book.getAuthor(), author)) {
                    score += 4;
                }
            }
            if (score > 0) {
                scored.add(new Scored(book, score + (book.availableCopies().isEmpty() ? 0 : 1)));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingInt(Scored::score).reversed().thenComparing(s -> s.book().getTitle()))
                .limit(Math.max(0, n))
                .map(Scored::book)
                .toList();
    }

    /** Names of all subgenres books can be filed under. */
    public List<String> subgenreNames() {
        return genres.root().subgenreNames();
    }

    public GenreComponent genreTree() {
        return genres.root();
    }

    public List<Book> allBooks() {
        return books.findAll();
    }

    private List<Book> genreBooks(String genre) {
        return genres.find(genre)
                .orElseThrow(() -> new LibraryException("There is no genre called \"" + genre.strip()
                        + "\". Known subgenres: " + String.join(", ", subgenreNames()) + "."))
                .getBooks();
    }

    private Subgenre requireSubgenre(String name) {
        return genres.findSubgenre(name)
                .orElseThrow(() -> new LibraryException("\"" + name + "\" is not a subgenre. Choose from: "
                        + String.join(", ", subgenreNames()) + "."));
    }

    private static int keywordScore(Book book, List<String> keywords) {
        String haystack = (book.getTitle() + " " + book.getAuthor() + " " + book.getDescription() + " "
                + String.join(" ", book.getGenres())).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                score++;
            }
        }
        return score;
    }

    private static boolean containsIgnoreCase(String value, String part) {
        return BookQuery.blank(part) || value.toLowerCase(Locale.ROOT).contains(part.strip().toLowerCase(Locale.ROOT));
    }

    static String normalizeIsbn(String isbn) {
        return isbn == null ? "" : isbn.replaceAll("[\\s-]", "");
    }
}
