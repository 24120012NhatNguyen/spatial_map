package com.example.cinemamap.data;

/** Nhãn tên hàng ghế (A, B, C...) đặt ở hai bên sơ đồ. */
public final class RowLabel {
    private final String text;
    private final float x;
    private final float centerY;

    public RowLabel(String text, float x, float centerY) {
        this.text = text;
        this.x = x;
        this.centerY = centerY;
    }

    public String getText() {
        return text;
    }

    public float getX() {
        return x;
    }

    public float getCenterY() {
        return centerY;
    }
}
