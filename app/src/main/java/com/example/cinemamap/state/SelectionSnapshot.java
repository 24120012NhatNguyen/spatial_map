package com.example.cinemamap.state;

import com.example.cinemamap.data.Seat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ảnh chụp bất biến của lựa chọn hiện tại – được phát cho mọi thành phần UI qua LiveData. */
public final class SelectionSnapshot {
    private final List<Seat> seats;
    private final Set<String> ids;
    private final long totalPrice;

    public SelectionSnapshot(List<Seat> seats, long totalPrice) {
        this.seats = Collections.unmodifiableList(seats);
        Set<String> idSet = new LinkedHashSet<>();
        for (Seat seat : seats) {
            idSet.add(seat.getId());
        }
        this.ids = Collections.unmodifiableSet(idSet);
        this.totalPrice = totalPrice;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public Set<String> getIds() {
        return ids;
    }

    public int getCount() {
        return seats.size();
    }

    public long getTotalPrice() {
        return totalPrice;
    }

    public boolean isEmpty() {
        return seats.isEmpty();
    }
}
