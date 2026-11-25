package com.example.cinemamap.state;

import com.example.cinemamap.data.CinemaLayout;
import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.SeatStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Logic chọn/bỏ chọn ghế (Java thuần, không phụ thuộc Android).
 * Chỉ lưu mã ghế đang chọn; trạng thái gốc của ghế không bao giờ bị sửa,
 * nên bỏ chọn luôn trả ghế về đúng trạng thái trước đó.
 */
public final class SeatSelection {

    public enum Result {
        SELECTED,
        DESELECTED,
        BOOKED,
        LOCKED,
        LIMIT_REACHED,
        NOT_FOUND
    }

    private final CinemaLayout layout;
    private final int maxSelection;
    /** Giữ thứ tự chọn để danh sách hiển thị ổn định; Set đảm bảo không trùng lặp. */
    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();

    public SeatSelection(CinemaLayout layout, int maxSelection) {
        if (maxSelection <= 0) {
            throw new IllegalArgumentException("maxSelection must be > 0");
        }
        this.layout = layout;
        this.maxSelection = maxSelection;
    }

    public Result toggle(String seatId) {
        Seat seat = layout.findSeat(seatId);
        if (seat == null) {
            return Result.NOT_FOUND;
        }
        if (selectedIds.remove(seatId)) {
            return Result.DESELECTED;
        }
        if (seat.getStatus() == SeatStatus.BOOKED) {
            return Result.BOOKED;
        }
        if (seat.getStatus() == SeatStatus.LOCKED) {
            return Result.LOCKED;
        }
        if (selectedIds.size() >= maxSelection) {
            return Result.LIMIT_REACHED;
        }
        selectedIds.add(seatId);
        return Result.SELECTED;
    }

    /** Bỏ chọn một ghế (dùng cho thao tác xóa trên danh sách). */
    public boolean remove(String seatId) {
        return selectedIds.remove(seatId);
    }

    public void clear() {
        selectedIds.clear();
    }

    public boolean isSelected(String seatId) {
        return selectedIds.contains(seatId);
    }

    public int size() {
        return selectedIds.size();
    }

    public int getMaxSelection() {
        return maxSelection;
    }

    public List<Seat> getSelectedSeats() {
        List<Seat> seats = new ArrayList<>(selectedIds.size());
        for (String id : selectedIds) {
            seats.add(layout.findSeat(id));
        }
        return seats;
    }

    public long getTotalPrice() {
        long total = 0;
        for (String id : selectedIds) {
            total += layout.findSeat(id).getPrice();
        }
        return total;
    }

    /** Bản sao danh sách mã ghế để lưu vào SavedStateHandle / Bundle. */
    public ArrayList<String> snapshotIds() {
        return new ArrayList<>(selectedIds);
    }

    /**
     * Khôi phục lựa chọn đã lưu. Bỏ qua mã không tồn tại, ghế không còn khả dụng,
     * mã trùng lặp và phần vượt quá giới hạn – tránh hiển thị thông tin không còn hợp lệ.
     */
    public void restore(Collection<String> ids) {
        selectedIds.clear();
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            Seat seat = layout.findSeat(id);
            if (seat != null && seat.isSelectable() && selectedIds.size() < maxSelection) {
                selectedIds.add(id);
            }
        }
    }
}
