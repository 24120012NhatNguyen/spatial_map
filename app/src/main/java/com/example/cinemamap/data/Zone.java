package com.example.cinemamap.data;

/** Một phân khu (khu ghế thường, VIP, ghế đôi) cùng vùng tiêu đề để vẽ nhãn. */
public final class Zone {
    private final SeatType type;
    private final String label;
    private final WorldRect bounds;
    private final WorldRect headerBounds;

    public Zone(SeatType type, String label, WorldRect bounds, WorldRect headerBounds) {
        this.type = type;
        this.label = label;
        this.bounds = bounds;
        this.headerBounds = headerBounds;
    }

    public SeatType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public WorldRect getBounds() {
        return bounds;
    }

    public WorldRect getHeaderBounds() {
        return headerBounds;
    }
}
