package libraryagent.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import libraryagent.domain.copy.BookCopy;
import libraryagent.domain.fee.LateFeePolicy;

/** A copy lent to a member, from checkout until it is returned. */
public class Loan {

    public static final int LOAN_DAYS = 21;
    public static final int MAX_RENEWALS = 2;

    private final String loanId;
    private final BookCopy copy;
    private final Member member;
    private final LocalDate checkoutDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private int renewals;
    private BigDecimal fee = BigDecimal.ZERO;

    /** A new loan due {@link #LOAN_DAYS} days after checkout. */
    public Loan(String loanId, BookCopy copy, Member member, LocalDate checkoutDate) {
        this(loanId, copy, member, checkoutDate, checkoutDate.plusDays(LOAN_DAYS), null, 0, BigDecimal.ZERO);
    }

    /** Rebuilds a loan from storage. */
    public Loan(String loanId, BookCopy copy, Member member, LocalDate checkoutDate, LocalDate dueDate,
                LocalDate returnDate, int renewals, BigDecimal fee) {
        this.loanId = Objects.requireNonNull(loanId);
        this.copy = Objects.requireNonNull(copy);
        this.member = Objects.requireNonNull(member);
        this.checkoutDate = Objects.requireNonNull(checkoutDate);
        this.dueDate = Objects.requireNonNull(dueDate);
        this.returnDate = returnDate;
        this.renewals = renewals;
        this.fee = fee == null ? BigDecimal.ZERO : fee;
    }

    public boolean isOpen() {
        return returnDate == null;
    }

    /** Still out and past its due date. */
    public boolean isOverdue(LocalDate today) {
        return isOpen() && today.isAfter(dueDate);
    }

    /** Days past the due date, counted to the return date if returned, otherwise to today. */
    public long daysLate(LocalDate today) {
        LocalDate end = returnDate != null ? returnDate : today;
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, end));
    }

    /** Extends the due date by another loan period. */
    public void renew() {
        if (!isOpen()) {
            throw new LibraryException("This loan has already been returned.");
        }
        if (renewals >= MAX_RENEWALS) {
            throw new LibraryException("This loan has already been renewed the maximum of "
                    + MAX_RENEWALS + " times.");
        }
        dueDate = dueDate.plusDays(LOAN_DAYS);
        renewals++;
    }

    /** Reverses the most recent renewal. */
    public void undoRenew(LocalDate previousDueDate) {
        if (renewals == 0) {
            throw new IllegalStateException("Loan " + loanId + " has no renewal to undo");
        }
        dueDate = Objects.requireNonNull(previousDueDate);
        renewals--;
    }

    /** Fee owed using the given policy, as of today or the return date. */
    public BigDecimal calculateFee(LateFeePolicy policy, LocalDate today) {
        return policy.calculateFee(this, today);
    }

    /** Closes the loan, recording the fee charged. */
    public void close(LocalDate returnedOn, BigDecimal chargedFee) {
        if (!isOpen()) {
            throw new LibraryException("This loan has already been returned.");
        }
        returnDate = Objects.requireNonNull(returnedOn);
        fee = Objects.requireNonNull(chargedFee);
    }

    /** Reopens a returned loan (used when a return is undone). */
    public void reopen() {
        returnDate = null;
        fee = BigDecimal.ZERO;
    }

    public String getLoanId() {
        return loanId;
    }

    public BookCopy getCopy() {
        return copy;
    }

    public Book getBook() {
        return copy.getBook();
    }

    public Member getMember() {
        return member;
    }

    public LocalDate getCheckoutDate() {
        return checkoutDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public int getRenewals() {
        return renewals;
    }

    /** Fee charged when the loan was closed (zero while open). */
    public BigDecimal getFee() {
        return fee;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Loan other && loanId.equals(other.loanId);
    }

    @Override
    public int hashCode() {
        return loanId.hashCode();
    }
}
