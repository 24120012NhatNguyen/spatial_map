package com.example.cinemamap.ui.map;

import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.WorldRect;

import java.util.List;

/**
 * Tìm ghế tại một điểm trên bản đồ. Điểm chạm phải được đổi sang tọa độ world trước
 * (qua {@link Viewport}), vì vậy kết quả luôn đúng dù sơ đồ đã được Pan hay Zoom.
 */
public final class HitTester {

    public static final int NO_HIT = -1;

    private HitTester() {
    }

    /**
     * 1. Nếu điểm nằm trong một ghế → trả về ghế đó (các ghế không chồng lấn nên là duy nhất).
     * 2. Nếu không, chọn ghế có biên gần điểm nhất, miễn khoảng cách ≤ {@code tolerance}.
     *    Bước này giúp chạm gần mép ghế (hoặc vào khe hở hẹp) vẫn chọn đúng ghế gần nhất.
     *
     * @param worldX    tọa độ world của điểm chạm
     * @param worldY    tọa độ world của điểm chạm
     * @param tolerance khoảng cách tối đa (đơn vị world) để chấp nhận chạm gần biên
     * @return chỉ số ghế trong {@code seats}, hoặc {@link #NO_HIT}
     */
    public static int findSeatIndex(List<Seat> seats, float worldX, float worldY, float tolerance) {
        return findSeatIndex(seats, worldX, worldY, tolerance,
                -Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE);
    }

    /**
     * Như {@link #findSeatIndex(List, float, float, float)} nhưng chỉ xét các ghế giao với vùng
     * nhìn thấy [visibleLeft, visibleTop, visibleRight, visibleBottom] (world), để chạm sát mép
     * màn hình không chọn nhầm một ghế đã nằm hẳn ngoài màn hình.
     * (Với điểm chạm nằm trong vùng nhìn thấy, khoảng cách tới ghế bị cắt một phần bằng đúng
     * khoảng cách tới phần ghế còn nhìn thấy, nên chỉ cần loại các ghế khuất hoàn toàn.)
     */
    public static int findSeatIndex(List<Seat> seats, float worldX, float worldY, float tolerance,
                                    float visibleLeft, float visibleTop, float visibleRight, float visibleBottom) {
        int best = NO_HIT;
        float bestDistance = Float.MAX_VALUE;
        for (int i = 0, n = seats.size(); i < n; i++) {
            WorldRect bounds = seats.get(i).getBounds();
            if (!bounds.intersects(visibleLeft, visibleTop, visibleRight, visibleBottom)) {
                continue;
            }
            float distance = bounds.distanceTo(worldX, worldY);
            if (distance == 0f) {
                return i;
            }
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return bestDistance <= tolerance ? best : NO_HIT;
    }
}
