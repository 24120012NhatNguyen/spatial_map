package com.example.cinemamap.data;

/**
 * Trạng thái gốc của ghế (lấy từ dữ liệu). Trạng thái "đang chọn" KHÔNG nằm ở đây:
 * nó là trạng thái phiên làm việc của người dùng, được quản lý trong lớp state.
 * Nhờ vậy khi bỏ chọn, ghế luôn quay về đúng trạng thái gốc.
 */
public enum SeatStatus {
    AVAILABLE("Còn trống"),
    BOOKED("Đã đặt"),
    LOCKED("Bị khóa / bảo trì");

    private final String displayName;

    SeatStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
