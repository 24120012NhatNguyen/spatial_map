package com.example.cinemamap.data;

/** Một ghế trên sơ đồ. Bất biến; vị trí tính bằng tọa độ bản đồ. */
public final class Seat {
    private final int index;
    private final String id;
    private final String row;
    private final int number;
    private final String numberLabel;
    private final SeatType type;
    private final SeatStatus status;
    private final WorldRect bounds;

    public Seat(int index, String row, int number, SeatType type, SeatStatus status, WorldRect bounds) {
        this.index = index;
        this.row = row;
        this.number = number;
        this.id = row + number;
        // Tạo sẵn chuỗi nhãn để onDraw() không phải cấp phát String mỗi khung hình.
        this.numberLabel = String.valueOf(number);
        this.type = type;
        this.status = status;
        this.bounds = bounds;
    }

    /** Vị trí của ghế trong danh sách {@link CinemaLayout#getSeats()}. */
    public int getIndex() {
        return index;
    }

    public String getId() {
        return id;
    }

    public String getRow() {
        return row;
    }

    public int getNumber() {
        return number;
    }

    public String getNumberLabel() {
        return numberLabel;
    }

    public SeatType getType() {
        return type;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public WorldRect getBounds() {
        return bounds;
    }

    public long getPrice() {
        return type.getPrice();
    }

    public boolean isSelectable() {
        return status == SeatStatus.AVAILABLE;
    }
}
