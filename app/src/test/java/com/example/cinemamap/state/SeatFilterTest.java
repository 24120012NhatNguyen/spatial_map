package com.example.cinemamap.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.MockCinemaRepository;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.SeatStatus;
import com.example.cinemamap.data.SeatType;
import com.example.cinemamap.data.WorldRect;

import org.junit.Test;

import java.util.EnumSet;

public class SeatFilterTest {

    private final CinemaLayout layout = MockCinemaRepository.createLayout();

    @Test
    public void noneMatchesEverySeat() {
        assertFalse(SeatFilter.NONE.isActive());
        FilterState state = FilterState.compute(layout, SeatFilter.NONE);
        assertEquals(layout.getSeats().size(), state.getMatchCount());
        for (Seat seat : layout.getSeats()) {
            assertTrue(state.matches(seat.getIndex()));
        }
    }

    @Test
    public void typeFilterKeepsOnlyThatZone() {
        SeatFilter filter = SeatFilter.NONE.withType(SeatType.VIP, true);
        for (Seat seat : layout.getSeats()) {
            assertEquals(seat.getType() == SeatType.VIP, filter.matches(seat));
        }
    }

    @Test
    public void conditionsCombineWithAnd() {
        SeatFilter filter = SeatFilter.of(EnumSet.of(SeatType.STANDARD, SeatType.COUPLE), true, null);
        for (Seat seat : layout.getSeats()) {
            boolean expected = seat.getType() != SeatType.VIP && seat.getStatus() == SeatStatus.AVAILABLE;
            assertEquals(seat.getId(), expected, filter.matches(seat));
        }
    }

    @Test
    public void queryIsNormalized() {
        assertEquals("E9", SeatFilter.normalizeQuery("  e - 9 "));
        assertEquals("", SeatFilter.normalizeQuery(null));
        assertEquals("", SeatFilter.normalizeQuery("   "));
    }

    @Test
    public void vietnameseLeadingWordsAreIgnored() {
        assertEquals("E", SeatFilter.normalizeQuery("hàng E"));
        assertEquals("E", SeatFilter.normalizeQuery("hang e"));
        assertEquals("E9", SeatFilter.normalizeQuery("Ghế E9"));
        assertEquals("9", SeatFilter.normalizeQuery("số 9"));
        // Gõ dở "hàng" thì vẫn là một từ khóa (không khớp ghế nào) chứ không biến thành "không lọc".
        assertEquals("HÀNG", SeatFilter.normalizeQuery("hàng"));
        FilterState row = FilterState.compute(layout, SeatFilter.NONE.withQuery("hàng E"));
        assertEquals(FilterState.compute(layout, SeatFilter.NONE.withQuery("E")).getMatchCount(), row.getMatchCount());
        assertTrue(FilterState.compute(layout, SeatFilter.NONE.withQuery("hàng")).hasNoResults());
    }

    @Test
    public void exactSeatQueryMatchesOneSeat() {
        FilterState state = FilterState.compute(layout, SeatFilter.NONE.withQuery("e9"));
        assertEquals(1, state.getMatchCount());
        assertNotNull(state.getSingleMatch());
        assertEquals("E9", state.getSingleMatch().getId());
        // Số 0 ở đầu không làm sai kết quả.
        assertEquals("E9", FilterState.compute(layout, SeatFilter.NONE.withQuery("E09")).getSingleMatch().getId());
    }

    @Test
    public void rowQueryMatchesWholeRow() {
        FilterState state = FilterState.compute(layout, SeatFilter.NONE.withQuery("E"));
        int expected = 0;
        for (Seat seat : layout.getSeats()) {
            if (seat.getRow().equals("E")) {
                expected++;
                assertTrue(state.matches(seat.getIndex()));
            } else {
                assertFalse(state.matches(seat.getIndex()));
            }
        }
        assertEquals(expected, state.getMatchCount());
        WorldRect bounds = state.getMatchBounds();
        assertNotNull(bounds);
        assertNull(state.getSingleMatch());
    }

    @Test
    public void invalidOrUnknownQueryGivesNoResults() {
        for (String query : new String[]{"Z", "E9A", "E99", "#", "12345678901"}) {
            FilterState state = FilterState.compute(layout, SeatFilter.NONE.withQuery(query));
            assertTrue(query, state.hasNoResults());
            assertNull(query, state.getMatchBounds());
        }
    }

    @Test
    public void filterStateMatchesAreDefensiveCopies() {
        FilterState state = FilterState.compute(layout, SeatFilter.NONE.withType(SeatType.VIP, true));
        boolean[] copy = state.copyMatches();
        int firstStandard = 0;
        copy[firstStandard] = true;
        assertFalse(state.matches(firstStandard));
    }

    @Test
    public void equalFiltersAreEqual() {
        SeatFilter a = SeatFilter.NONE.withType(SeatType.VIP, true).withQuery("e");
        SeatFilter b = SeatFilter.of(EnumSet.of(SeatType.VIP), false, " E ");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(SeatFilter.NONE, a.withType(SeatType.VIP, false).withQuery(""));
    }
}
