package com.example.cinemamap.ui.map;

import static org.junit.Assert.assertEquals;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.MockCinemaRepository;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.WorldRect;

import org.junit.Test;

import java.util.List;

public class HitTesterTest {

    private final CinemaLayout layout = MockCinemaRepository.createLayout();
    private final List<Seat> seats = layout.getSeats();

    @Test
    public void hitsSeatContainingPoint() {
        for (Seat seat : seats) {
            WorldRect b = seat.getBounds();
            assertEquals(seat.getIndex(), HitTester.findSeatIndex(seats, b.centerX(), b.centerY(), 0f));
            assertEquals(seat.getIndex(), HitTester.findSeatIndex(seats, b.left, b.top, 0f));
            assertEquals(seat.getIndex(), HitTester.findSeatIndex(seats, b.right, b.bottom, 0f));
        }
    }

    @Test
    public void nearEdgeTouchPicksClosestSeat() {
        Seat a1 = layout.findSeat("A1");
        Seat a2 = layout.findSeat("A2");
        // Điểm trong khe hở giữa A1 và A2, lệch về phía A2.
        float x = a2.getBounds().left - 1f;
        float y = a2.getBounds().centerY();
        assertEquals(a2.getIndex(), HitTester.findSeatIndex(seats, x, y, 5f));
        x = a1.getBounds().right + 1f;
        assertEquals(a1.getIndex(), HitTester.findSeatIndex(seats, x, y, 5f));
    }

    @Test
    public void touchFarFromSeatsMisses() {
        assertEquals(HitTester.NO_HIT, HitTester.findSeatIndex(seats, 1f, 1f, 5f));
        WorldRect aisle = layout.getAisles().get(0);
        assertEquals(HitTester.NO_HIT,
                HitTester.findSeatIndex(seats, aisle.centerX(), layout.findSeat("A1").getBounds().centerY(), 5f));
    }

    @Test
    public void nearEdgeTouchIgnoresSeatsOutsideVisibleArea() {
        Seat f10 = layout.findSeat("F10");
        Seat g10 = layout.findSeat("G10");
        // Vùng nhìn thấy bắt đầu ngay dưới F10 (F10 đã khuất hoàn toàn phía trên).
        float visibleTop = f10.getBounds().bottom + 1f;
        float x = f10.getBounds().centerX();
        float y = visibleTop + 0.5f; // chạm sát mép trên của vùng nhìn thấy
        assertEquals(f10.getIndex(), HitTester.findSeatIndex(seats, x, y, 4f));
        assertEquals(HitTester.NO_HIT, HitTester.findSeatIndex(seats, x, y, 4f,
                0f, visibleTop, layout.getWidth(), layout.getHeight()));
        float yInG10 = g10.getBounds().top + 1f;
        assertEquals(g10.getIndex(), HitTester.findSeatIndex(seats, x, yInG10, 4f,
                0f, visibleTop, layout.getWidth(), layout.getHeight()));
    }

    @Test
    public void hitTestingIsCorrectAfterPanAndZoom() {
        Viewport viewport = new Viewport(16f);
        viewport.setContentSize(layout.getWidth(), layout.getHeight());
        viewport.setViewSize(1080f, 1920f);
        viewport.zoomAround(700f, 800f, 3.2f);
        viewport.panBy(-250f, 130f);

        for (Seat seat : seats) {
            WorldRect b = seat.getBounds();
            // Chạm vào tâm ghế (tính trên màn hình) rồi đổi ngược về world để hit-test.
            float sx = viewport.worldToScreenX(b.centerX());
            float sy = viewport.worldToScreenY(b.centerY());
            int hit = HitTester.findSeatIndex(seats, viewport.screenToWorldX(sx), viewport.screenToWorldY(sy), 0f);
            assertEquals(seat.getId(), seat.getIndex(), hit);

            // Chạm sát mép trong (1px màn hình) vẫn trúng đúng ghế.
            float edgeX = viewport.worldToScreenX(b.right) - 1f;
            hit = HitTester.findSeatIndex(seats, viewport.screenToWorldX(edgeX), viewport.screenToWorldY(sy), 0f);
            assertEquals(seat.getId(), seat.getIndex(), hit);
        }
    }
}
