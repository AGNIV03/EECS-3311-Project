package libraryagent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void borrowLimitComesFromMemberType() {
        Member standard = new Member("M1", "A", "a@x.com", MemberType.STANDARD, false, new ReadingPreferences());
        Member student = new Member("M2", "B", "b@x.com", MemberType.STUDENT, false, new ReadingPreferences());
        assertEquals(5, standard.getBorrowLimit());
        assertEquals(8, student.getBorrowLimit());
    }

    @Test
    void parsingTypesAndRatingsIsCaseInsensitive() {
        assertEquals(MemberType.STUDENT, MemberType.parse(" Student "));
        assertEquals(Rating.DISLIKED, Rating.parse("disliked"));
        assertThrows(LibraryException.class, () -> MemberType.parse("vip"));
        assertThrows(LibraryException.class, () -> Rating.parse("meh"));
    }
}
