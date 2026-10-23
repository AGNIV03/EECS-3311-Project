package libraryagent.service;

/** How many copies of a book are free right now, and how many members are waiting. */
public record Availability(int availableCopies, int totalCopies, int queueLength) {

    public boolean isAvailable() {
        return availableCopies > 0;
    }
}
