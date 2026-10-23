package libraryagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import libraryagent.domain.LibraryException;
import libraryagent.domain.Member;
import libraryagent.domain.MemberType;
import libraryagent.domain.ReadingPreferences;
import libraryagent.persistence.DataStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberServiceTest {

    private DataStore store;
    private MemberService members;

    @BeforeEach
    void setUp() {
        store = ServiceTestSupport.seededStore(ServiceTestSupport.clock());
        members = new MemberService(store.members(), store.genres());
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void registerCreatesMemberWithNextId() {
        Member jo = members.register("  Jo Lee ", "jo@example.com", MemberType.STUDENT);
        assertEquals("M003", jo.getMemberId());
        assertEquals("Jo Lee", jo.getName());
        assertEquals(8, jo.getBorrowLimit());
        assertTrue(jo.getPreferences().isTrackingEnabled());
        assertEquals(jo, members.requireMember("m003"));
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() {
        LibraryException e = assertThrows(LibraryException.class,
                () -> members.register("Alex", "Alex.Morgan@Example.com", MemberType.STANDARD));
        assertEquals("An account with the email Alex.Morgan@Example.com already exists.", e.getMessage());
    }

    @Test
    void registerValidatesNameAndEmail() {
        assertThrows(LibraryException.class, () -> members.register(" ", "a@b.com", MemberType.STANDARD));
        assertThrows(LibraryException.class, () -> members.register("A", "not-an-email", MemberType.STANDARD));
        assertThrows(LibraryException.class, () -> members.register("A", "a@b.com", null));
    }

    @Test
    void updatePreferencesCanonicalisesGenreNames() {
        Member alex = members.requireMember("M001");
        members.updatePreferences(alex, new ReadingPreferences(
                List.of("fantasy", "FANTASY", "Nonfiction"), List.of(" Le Guin "), 350, true));
        assertEquals(List.of("Fantasy", "Nonfiction"), alex.getPreferences().getFavoriteGenres());
        assertEquals(List.of("Le Guin"), alex.getPreferences().getFavoriteAuthors());
        assertEquals(350, alex.getPreferences().getMaxPages());
    }

    @Test
    void updatePreferencesRejectsUnknownGenreWithoutChangingAnything() {
        Member sam = members.requireMember("M002");
        assertThrows(LibraryException.class, () -> members.updatePreferences(sam,
                new ReadingPreferences(List.of("Cyberpunk"), List.of(), 0, true)));
        assertEquals(List.of("Fantasy", "YA Fantasy"), sam.getPreferences().getFavoriteGenres());
    }

    @Test
    void unknownMemberIsReported() {
        assertFalse(members.findById("M999").isPresent());
        assertThrows(LibraryException.class, () -> members.requireMember("M999"));
    }
}
