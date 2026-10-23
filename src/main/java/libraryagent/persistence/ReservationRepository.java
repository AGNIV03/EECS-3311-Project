package libraryagent.persistence;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import libraryagent.domain.Book;
import libraryagent.domain.Member;
import libraryagent.domain.Reservation;
import libraryagent.domain.ReservationStatus;

/** Reservations. Loading rebuilds each book's hold queue from its waiting reservations. */
public class ReservationRepository implements Repository<Reservation> {

    private final Database db;
    private final Map<String, Reservation> reservations = new LinkedHashMap<>();

    public ReservationRepository(Database db, BookRepository books, MemberRepository members) {
        this.db = db;
        db.query("SELECT reservation_id, member_id, isbn, copy_id, status, created_at, ready_at "
                        + "FROM reservations ORDER BY created_at, reservation_id",
                rs -> new Reservation(rs.getString(1),
                        members.findById(rs.getString(2)).orElseThrow(),
                        books.findById(rs.getString(3)).orElseThrow(),
                        LocalDateTime.parse(rs.getString(6)),
                        ReservationStatus.valueOf(rs.getString(5)),
                        rs.getString(7) == null ? null : LocalDateTime.parse(rs.getString(7)),
                        rs.getString(4) == null ? null : books.findCopy(rs.getString(4)).orElseThrow()))
                .forEach(r -> {
                    reservations.put(r.getReservationId(), r);
                    if (r.getStatus() == ReservationStatus.WAITING) {
                        r.getBook().getHoldQueue().enqueue(r);
                    }
                });
    }

    @Override
    public void save(Reservation r) {
        db.update("INSERT INTO reservations(reservation_id, member_id, isbn, copy_id, status, created_at, ready_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(reservation_id) DO UPDATE SET "
                        + "copy_id = excluded.copy_id, status = excluded.status, ready_at = excluded.ready_at",
                r.getReservationId(), r.getMember().getMemberId(), r.getBook().getIsbn(),
                r.getCopy() == null ? null : r.getCopy().getCopyId(), r.getStatus().name(),
                r.getCreatedAt().toString(), r.getReadyAt() == null ? null : r.getReadyAt().toString());
        reservations.put(r.getReservationId(), r);
    }

    @Override
    public Optional<Reservation> findById(String id) {
        return Optional.ofNullable(reservations.get(id.strip().toUpperCase()));
    }

    @Override
    public List<Reservation> findAll() {
        return List.copyOf(reservations.values());
    }

    public List<Reservation> findByMember(Member member) {
        return reservations.values().stream().filter(r -> r.getMember().equals(member)).toList();
    }

    public Optional<Reservation> findActive(Member member, Book book) {
        return reservations.values().stream()
                .filter(r -> r.isActive() && r.getMember().equals(member) && r.getBook().equals(book))
                .findFirst();
    }

    public List<Reservation> findByStatus(ReservationStatus status) {
        return reservations.values().stream().filter(r -> r.getStatus() == status).toList();
    }

    public String nextId() {
        return db.nextId("R", 4);
    }
}
