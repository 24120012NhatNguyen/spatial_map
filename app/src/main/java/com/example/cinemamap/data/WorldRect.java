package com.example.cinemamap.data;

/**
 * Hình chữ nhật bất biến trong hệ tọa độ bản đồ (world coordinates).
 * Không phụ thuộc Android SDK nên có thể kiểm thử bằng JUnit thuần.
 */
public final class WorldRect {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;

    public WorldRect(float left, float top, float right, float bottom) {
        if (right < left || bottom < top) {
            throw new IllegalArgumentException("Invalid rect: " + left + "," + top + "," + right + "," + bottom);
        }
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public float width() {
        return right - left;
    }

    public float height() {
        return bottom - top;
    }

    public float centerX() {
        return (left + right) * 0.5f;
    }

    public float centerY() {
        return (top + bottom) * 0.5f;
    }

    public boolean contains(float x, float y) {
        return x >= left && x <= right && y >= top && y <= bottom;
    }

    public boolean intersects(float l, float t, float r, float b) {
        return left < r && l < right && top < b && t < bottom;
    }

    /** Khoảng cách Euclid từ điểm (x, y) đến biên hình chữ nhật; bằng 0 nếu điểm nằm bên trong. */
    public float distanceTo(float x, float y) {
        float dx = Math.max(Math.max(left - x, 0f), x - right);
        float dy = Math.max(Math.max(top - y, 0f), y - bottom);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
