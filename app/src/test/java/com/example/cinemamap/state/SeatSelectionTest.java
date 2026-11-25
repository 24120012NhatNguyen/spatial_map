package com.example.cinemamap.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.MockCinemaRepository;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.SeatStatus;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SeatSelectionTest {

    private CinemaLayout layout;
    private SeatSelection selection;
    private List<Seat> available;

    @Before
    public void setUp() {
        layout = MockCinemaRepository.createLayout();
        selection = new SeatSelection(layout, 3);
        available = new ArrayList<>();
        for (Seat seat : layout.getSeats()) {
            if (seat.getStatus() == SeatStatus.AVAILABLE) {
                available.add(seat);
            }
        }
    }

    @Test
    public void toggleSelectsThenDeselects() {
        String id = available.get(0).getId();
        assertEquals(SeatSelection.Result.SELECTED, selection.toggle(id));
        assertTrue(selection.isSelected(id));
        assertEquals(SeatSelection.Result.DESELECTED, selection.toggle(id));
        assertFalse(selection.isSelected(id));
        // Trạng thái gốc không bị thay đổi.
        assertEquals(SeatStatus.AVAILABLE, layout.findSeat(id).getStatus());
    }

    @Test
    public void deselectingOneSeatDoesNotAffectOthers() {
        String a = available.get(0).getId();
        String b = available.get(1).getId();
        selection.toggle(a);
        selection.toggle(b);
        selection.toggle(a);
        assertFalse(selection.isSelected(a));
        assertTrue(selection.isSelected(b));
        assertEquals(1, selection.size());
    }

    @Test
    public void unavailableSeatsCannotBeSelected() {
        for (Seat seat : layout.getSeats()) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                assertEquals(SeatSelection.Result.BOOKED, selection.toggle(seat.getId()));
            } else if (seat.getStatus() == SeatStatus.LOCKED) {
                assertEquals(SeatSelection.Result.LOCKED, selection.toggle(seat.getId()));
            }
        }
        assertEquals(0, selection.size());
        assertEquals(SeatSelection.Result.NOT_FOUND, selection.toggle("Z99"));
    }

    @Test
    public void selectionLimitIsEnforced() {
        for (int i = 0; i < 3; i++) {
            assertEquals(SeatSelection.Result.SELECTED, selection.toggle(available.get(i).getId()));
        }
        assertEquals(SeatSelection.Result.LIMIT_REACHED, selection.toggle(available.get(3).getId()));
        assertEquals(3, selection.size());
    }

    @Test
    public void totalPriceAndOrderFollowSelection() {
        Seat a = available.get(5);
        Seat b = available.get(1);
        selection.toggle(a.getId());
        selection.toggle(b.getId());
        assertEquals(a.getPrice() + b.getPrice(), selection.getTotalPrice());
        assertEquals(Arrays.asList(a.getId(), b.getId()), selection.snapshotIds());
        assertEquals(Arrays.asList(a, b), selection.getSelectedSeats());
    }

    @Test
    public void removeAndClear() {
        String a = available.get(0).getId();
        selection.toggle(a);
        assertTrue(selection.remove(a));
        assertFalse(selection.remove(a));
        selection.toggle(available.get(1).getId());
        selection.clear();
        assertEquals(0, selection.size());
    }

    @Test
    public void restoreDropsInvalidDuplicateAndOverflowIds() {
        String booked = null;
        for (Seat seat : layout.getSeats()) {
            if (seat.getStatus() == SeatStatus.BOOKED) {
                booked = seat.getId();
                break;
            }
        }
        String a = available.get(0).getId();
        String b = available.get(1).getId();
        String c = available.get(2).getId();
        String d = available.get(3).getId();
        selection.restore(Arrays.asList(a, "NOPE", a, booked, b, c, d));
        assertEquals(Arrays.asList(a, b, c), selection.snapshotIds());

        selection.restore(null);
        assertEquals(0, selection.size());
    }
}
