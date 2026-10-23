package libraryagent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import libraryagent.domain.copy.BookCopy;
import org.junit.jupiter.api.Test;

class LoanAndFeeTest {

    private static final LocalDate CHECKOUT = LocalDate.of(2026, 10, 1);
    private static final LocalDate DUE = CHECKOUT.plusDays(Loan.LOAN_DAYS);

    private final Book book = new Book("978-1", "Dune", "Frank Herbert", 600, "");

    private Loan loanFor(MemberType type) {
        Member member = new Member("M1", "Ana", "a@x.com", type, false, new ReadingPreferences());
        return new Loan("L1", new BookCopy("C1", book), member, CHECKOUT);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    @Test
    void newLoanIsDueInTwentyOneDays() {
        Loan loan = loanFor(MemberType.STANDARD);
        assertEquals(LocalDate.of(2026, 10, 22), loan.getDueDate());
        assertFalse(loan.isOverdue(DUE));
        assertTrue(loan.isOverdue(DUE.plusDays(1)));
    }

    @Test
    void standardPolicyChargesFromFirstLateDay() {
        Loan loan = loanFor(MemberType.STANDARD);
        assertEquals(money("0.00"), loan.calculateFee(MemberType.STANDARD.feePolicy(), DUE));
        assertEquals(money("0.25"), loan.calculateFee(MemberType.STANDARD.feePolicy(), DUE.plusDays(1)));
        assertEquals(money("2.50"), loan.calculateFee(MemberType.STANDARD.feePolicy(), DUE.plusDays(10)));
    }

    @Test
    void studentPolicyHasGracePeriodAndLowerRate() {
        Loan loan = loanFor(MemberType.STUDENT);
        assertEquals(money("0.00"), loan.calculateFee(MemberType.STUDENT.feePolicy(), DUE.plusDays(2)));
        assertEquals(money("0.10"), loan.calculateFee(MemberType.STUDENT.feePolicy(), DUE.plusDays(3)));
        assertEquals(money("0.80"), loan.calculateFee(MemberType.STUDENT.feePolicy(), DUE.plusDays(10)));
    }

    @Test
    void feesAreCappedAtStandardMaximum() {
        Loan standard = loanFor(MemberType.STANDARD);
        Loan student = loanFor(MemberType.STUDENT);
        LocalDate muchLater = DUE.plusDays(400);
        assertEquals(money("10.00"), standard.calculateFee(MemberType.STANDARD.feePolicy(), muchLater));
        assertEquals(money("10.00"), student.calculateFee(MemberType.STUDENT.feePolicy(), muchLater));
    }

    @Test
    void returnedLoanFeeStopsAtReturnDate() {
        Loan loan = loanFor(MemberType.STANDARD);
        loan.close(DUE.plusDays(4), money("1.00"));
        assertEquals(money("1.00"), loan.calculateFee(MemberType.STANDARD.feePolicy(), DUE.plusDays(30)));
        assertFalse(loan.isOverdue(DUE.plusDays(30)));
        assertThrows(LibraryException.class, () -> loan.close(DUE, BigDecimal.ZERO));
    }

    @Test
    void renewalExtendsDueDateUpToTwice() {
        Loan loan = loanFor(MemberType.STANDARD);
        loan.renew();
        assertEquals(DUE.plusDays(21), loan.getDueDate());
        loan.renew();
        LibraryException e = assertThrows(LibraryException.class, loan::renew);
        assertEquals("This loan has already been renewed the maximum of 2 times.", e.getMessage());
        assertEquals(2, loan.getRenewals());
    }

    @Test
    void undoRenewRestoresPreviousDueDate() {
        Loan loan = loanFor(MemberType.STANDARD);
        loan.renew();
        loan.undoRenew(DUE);
        assertEquals(DUE, loan.getDueDate());
        assertEquals(0, loan.getRenewals());
    }

    @Test
    void reopenClearsReturn() {
        Loan loan = loanFor(MemberType.STANDARD);
        loan.close(DUE, BigDecimal.ZERO);
        loan.reopen();
        assertTrue(loan.isOpen());
    }
}
