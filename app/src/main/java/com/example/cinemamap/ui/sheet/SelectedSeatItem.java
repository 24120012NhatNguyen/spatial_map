package com.example.cinemamap.ui.sheet;

import androidx.annotation.NonNull;

import com.example.cinemamap.data.Seat;

/** Một dòng trong danh sách ghế đã chọn: ghế + ghế đó có khớp bộ lọc hiện tại hay không. */
final class SelectedSeatItem {
    final Seat seat;
    final boolean matchesFilter;

    SelectedSeatItem(@NonNull Seat seat, boolean matchesFilter) {
        this.seat = seat;
        this.matchesFilter = matchesFilter;
    }
}
