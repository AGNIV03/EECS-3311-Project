package libraryagent.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import libraryagent.domain.Loan;
import libraryagent.domain.Member;
import libraryagent.domain.ReadingList;

/** Everything shown on a member's account page, computed as of {@code today}. */
public record AccountSummary(Member member, List<Loan> loans, List<HoldStatus> holds, BigDecimal feesOwed,
                             List<ReadingList> readingLists, LocalDate today) {
}
