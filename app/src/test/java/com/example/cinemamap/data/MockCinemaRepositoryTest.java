package com.example.cinemamap.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MockCinemaRepositoryTest {

    private final CinemaLayout layout = MockCinemaRepository.createLayout();

    @Test
    public void seatCountIsWithinRequiredRange() {
        int count = layout.getSeats().size();
        assertTrue("seat count = " + count, count >= 150 && count <= 250);
    }

    @Test
    public void seatIdsAreUniqueAndIndexed() {
        Set<String> ids = new HashSet<>();
        List<Seat> seats = layout.getSeats();
        for (int i = 0; i < seats.size(); i++) {
            assertEquals(i, seats.get(i).getIndex());
            assertTrue(ids.add(seats.get(i).getId()));
            assertEquals(seats.get(i), layout.findSeat(seats.get(i).getId()));
        }
    }

    @Test
    public void seatsDoNotOverlapAndStayInsideWorld() {
        List<Seat> seats = layout.getSeats();
        for (int i = 0; i < seats.size(); i++) {
            WorldRect a = seats.get(i).getBounds();
            assertTrue(a.left >= 0 && a.top >= 0 && a.right <= layout.getWidth() && a.bottom <= layout.getHeight());
            for (int j = i + 1; j < seats.size(); j++) {
                WorldRect b = seats.get(j).getBounds();
                assertFalse(seats.get(i).getId() + " overlaps " + seats.get(j).getId(),
                        a.intersects(b.left, b.top, b.right, b.bottom));
            }
        }
    }

    @Test
    public void containsAllSeatTypesAndStatuses() {
        EnumSet<SeatType> types = EnumSet.noneOf(SeatType.class);
        EnumSet<SeatStatus> statuses = EnumSet.noneOf(SeatStatus.class);
        for (Seat seat : layout.getSeats()) {
            types.add(seat.getType());
            statuses.add(seat.getStatus());
        }
        assertEquals(EnumSet.allOf(SeatType.class), types);
        assertEquals(EnumSet.allOf(SeatStatus.class), statuses);
        assertEquals(3, layout.getZones().size());
        assertEquals(2, layout.getAisles().size());
    }

    @Test
    public void layoutIsDeterministic() {
        CinemaLayout other = MockCinemaRepository.createLayout();
        for (int i = 0; i < layout.getSeats().size(); i++) {
            assertEquals(layout.getSeats().get(i).getId(), other.getSeats().get(i).getId());
            assertEquals(layout.getSeats().get(i).getStatus(), other.getSeats().get(i).getStatus());
        }
    }

    @Test
    public void priceFormatterUsesVietnameseGrouping() {
        assertEquals("0đ", PriceFormatter.format(0));
        assertEquals("75.000đ", PriceFormatter.format(75_000));
        assertEquals("1.250.000đ", PriceFormatter.format(1_250_000));
    }
}
