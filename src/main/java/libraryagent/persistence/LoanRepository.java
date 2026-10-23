package libraryagent.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.copy.BookCopy;

/** Open and closed loans. */
public class LoanRepository implements Repository<Loan> {

    private final Database db;
    private final Map<String, Loan> loans = new LinkedHashMap<>();

    public LoanRepository(Database db, BookRepository books, MemberRepository members) {
        this.db = db;
        db.query("SELECT loan_id, copy_id, member_id, checkout_date, due_date, return_date, renewals, fee "
                        + "FROM loans ORDER BY loan_id",
                rs -> new Loan(rs.getString(1),
                        books.findCopy(rs.getString(2)).orElseThrow(),
                        members.findById(rs.getString(3)).orElseThrow(),
                        LocalDate.parse(rs.getString(4)),
                        LocalDate.parse(rs.getString(5)),
                        rs.getString(6) == null ? null : LocalDate.parse(rs.getString(6)),
                        rs.getInt(7),
                        new BigDecimal(rs.getString(8))))
                .forEach(l -> loans.put(l.getLoanId(), l));
    }

    @Override
    public void save(Loan loan) {
        db.update("INSERT INTO loans(loan_id, copy_id, member_id, checkout_date, due_date, return_date, renewals, fee) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(loan_id) DO UPDATE SET "
                        + "due_date = excluded.due_date, return_date = excluded.return_date, "
                        + "renewals = excluded.renewals, fee = excluded.fee",
                loan.getLoanId(), loan.getCopy().getCopyId(), loan.getMember().getMemberId(),
                loan.getCheckoutDate().toString(), loan.getDueDate().toString(),
                loan.getReturnDate() == null ? null : loan.getReturnDate().toString(),
                loan.getRenewals(), loan.getFee().toPlainString());
        loans.put(loan.getLoanId(), loan);
    }

    /** Removes a loan entirely (used when a checkout is undone). */
    public void delete(Loan loan) {
        db.update("DELETE FROM loans WHERE loan_id = ?", loan.getLoanId());
        loans.remove(loan.getLoanId());
    }

    @Override
    public Optional<Loan> findById(String loanId) {
        return Optional.ofNullable(loans.get(loanId.strip().toUpperCase()));
    }

    @Override
    public List<Loan> findAll() {
        return List.copyOf(loans.values());
    }

    public Optional<Loan> findOpenByCopy(BookCopy copy) {
        return loans.values().stream().filter(l -> l.isOpen() && l.getCopy().equals(copy)).findFirst();
    }

    /** Every loan the member has had, oldest first. */
    public List<Loan> findByMember(Member member) {
        return loans.values().stream().filter(l -> l.getMember().equals(member)).toList();
    }

    public List<Loan> findOpen() {
        return loans.values().stream().filter(Loan::isOpen).toList();
    }

    public String nextId() {
        return db.nextId("L", 4);
    }
}
