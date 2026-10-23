CREATE TABLE IF NOT EXISTS id_counters (
    prefix     TEXT PRIMARY KEY,
    next_value INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS genres (
    name        TEXT PRIMARY KEY COLLATE NOCASE,
    parent_name TEXT REFERENCES genres(name),
    position    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS books (
    isbn        TEXT PRIMARY KEY,
    title       TEXT NOT NULL,
    author      TEXT NOT NULL,
    page_count  INTEGER NOT NULL CHECK (page_count > 0),
    description TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS book_genres (
    isbn       TEXT NOT NULL REFERENCES books(isbn),
    genre_name TEXT NOT NULL REFERENCES genres(name),
    PRIMARY KEY (isbn, genre_name)
);

CREATE TABLE IF NOT EXISTS copies (
    copy_id      TEXT PRIMARY KEY,
    isbn         TEXT NOT NULL REFERENCES books(isbn),
    state        TEXT NOT NULL,
    reserved_for TEXT
);
CREATE INDEX IF NOT EXISTS idx_copies_isbn ON copies(isbn);

CREATE TABLE IF NOT EXISTS members (
    member_id    TEXT PRIMARY KEY,
    name         TEXT NOT NULL,
    email        TEXT NOT NULL UNIQUE COLLATE NOCASE,
    type         TEXT NOT NULL CHECK (type IN ('STANDARD', 'STUDENT')),
    is_librarian INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS preferences (
    member_id        TEXT PRIMARY KEY REFERENCES members(member_id),
    genres           TEXT NOT NULL,
    authors          TEXT NOT NULL,
    max_pages        INTEGER NOT NULL,
    tracking_enabled INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS loans (
    loan_id       TEXT PRIMARY KEY,
    copy_id       TEXT NOT NULL REFERENCES copies(copy_id),
    member_id     TEXT NOT NULL REFERENCES members(member_id),
    checkout_date TEXT NOT NULL,
    due_date      TEXT NOT NULL,
    return_date   TEXT,
    renewals      INTEGER NOT NULL,
    fee           TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_loans_member ON loans(member_id);
CREATE INDEX IF NOT EXISTS idx_loans_copy ON loans(copy_id);

CREATE TABLE IF NOT EXISTS reservations (
    reservation_id TEXT PRIMARY KEY,
    member_id      TEXT NOT NULL REFERENCES members(member_id),
    isbn           TEXT NOT NULL REFERENCES books(isbn),
    copy_id        TEXT REFERENCES copies(copy_id),
    status         TEXT NOT NULL,
    created_at     TEXT NOT NULL,
    ready_at       TEXT
);
CREATE INDEX IF NOT EXISTS idx_reservations_member ON reservations(member_id);

CREATE TABLE IF NOT EXISTS reading_lists (
    list_id   TEXT PRIMARY KEY,
    member_id TEXT NOT NULL REFERENCES members(member_id),
    name      TEXT NOT NULL COLLATE NOCASE,
    UNIQUE (member_id, name)
);

CREATE TABLE IF NOT EXISTS reading_list_items (
    list_id  TEXT NOT NULL REFERENCES reading_lists(list_id) ON DELETE CASCADE,
    isbn     TEXT NOT NULL REFERENCES books(isbn),
    position INTEGER NOT NULL,
    PRIMARY KEY (list_id, isbn)
);

CREATE TABLE IF NOT EXISTS feedback (
    member_id TEXT NOT NULL REFERENCES members(member_id),
    isbn      TEXT NOT NULL REFERENCES books(isbn),
    rating    TEXT NOT NULL CHECK (rating IN ('LIKED', 'NEUTRAL', 'DISLIKED')),
    comment   TEXT NOT NULL,
    rated_on  TEXT NOT NULL,
    PRIMARY KEY (member_id, isbn)
);
