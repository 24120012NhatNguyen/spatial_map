package com.example.cinemamap.data;

/** Loại ghế trong phòng chiếu. Giá vé là dữ liệu giả lập (VNĐ). */
public enum SeatType {
    STANDARD("Ghế thường", 75_000),
    VIP("Ghế VIP", 95_000),
    COUPLE("Ghế đôi", 180_000);

    private final String displayName;
    private final long price;

    SeatType(String displayName, long price) {
        this.displayName = displayName;
        this.price = price;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getPrice() {
        return price;
    }
}
