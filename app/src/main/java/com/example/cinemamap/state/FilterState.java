package com.example.cinemamap.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.WorldRect;

import java.util.List;

/**
 * Kết quả áp dụng {@link SeatFilter} lên sơ đồ (bất biến). Tính một lần mỗi khi bộ lọc đổi,
 * nên Canvas chỉ cần đọc cờ theo chỉ số ghế khi vẽ.
 */
public final class FilterState {

    private final SeatFilter filter;
    private final boolean[] matches;
    private final int matchCount;
    @Nullable
    private final WorldRect matchBounds;
    @Nullable
    private final Seat singleMatch;

    private FilterState(SeatFilter filter, boolean[] matches, int matchCount,
                        @Nullable WorldRect matchBounds, @Nullable Seat singleMatch) {
        this.filter = filter;
        this.matches = matches;
        this.matchCount = matchCount;
        this.matchBounds = matchBounds;
        this.singleMatch = singleMatch;
    }

    @NonNull
    public static FilterState compute(@NonNull CinemaLayout layout, @NonNull SeatFilter filter) {
        List<Seat> seats = layout.getSeats();
        boolean[] matches = new boolean[seats.size()];
        int count = 0;
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        Seat last = null;
        for (int i = 0; i < matches.length; i++) {
            Seat seat = seats.get(i);
            if (!filter.matches(seat)) {
                continue;
            }
            matches[i] = true;
            count++;
            last = seat;
            WorldRect b = seat.getBounds();
            left = Math.min(left, b.left);
            top = Math.min(top, b.top);
            right = Math.max(right, b.right);
            bottom = Math.max(bottom, b.bottom);
        }
        WorldRect bounds = count > 0 ? new WorldRect(left, top, right, bottom) : null;
        return new FilterState(filter, matches, count, bounds, count == 1 ? last : null);
    }

    @NonNull
    public SeatFilter getFilter() {
        return filter;
    }

    public boolean isActive() {
        return filter.isActive();
    }

    public boolean matches(int seatIndex) {
        return !filter.isActive() || matches[seatIndex];
    }

    /** Bản sao cờ khớp theo chỉ số ghế (để View không sửa được trạng thái gốc). */
    @NonNull
    public boolean[] copyMatches() {
        return matches.clone();
    }

    public int getMatchCount() {
        return matchCount;
    }

    public boolean hasNoResults() {
        return filter.isActive() && matchCount == 0;
    }

    /** Khung bao các ghế khớp (world) – dùng để đưa camera tới kết quả tìm kiếm. */
    @Nullable
    public WorldRect getMatchBounds() {
        return matchBounds;
    }

    /** Ghế duy nhất khớp (ví dụ tìm "E9"), hoặc null. */
    @Nullable
    public Seat getSingleMatch() {
        return singleMatch;
    }
}
