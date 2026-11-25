package com.example.cinemamap.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sinh dữ liệu giả lập cho phòng chiếu. Dùng seed cố định để dữ liệu luôn giống nhau
 * giữa các lần chạy (và sau khi tiến trình bị hệ điều hành thu hồi, mã ghế vẫn khớp).
 *
 * <p>Bố cục: màn hình ở trên, 3 khối ghế (trái 4 – giữa 10 – phải 4) ngăn bởi 2 lối đi dọc,
 * 3 phân khu (Thường A–D, VIP E–J, Ghế đôi K–L) ngăn bởi lối đi ngang có nhãn.
 * Tổng: 4×18 + 6×18 + 2×9 = 198 ghế.</p>
 */
public final class MockCinemaRepository {

    public static final float SEAT_SIZE = 32f;
    public static final float SEAT_GAP = 6f;
    public static final float ROW_PITCH = SEAT_SIZE + SEAT_GAP;
    public static final float AISLE_WIDTH = 44f;
    public static final float SIDE_MARGIN = 56f;
    public static final float ZONE_HEADER_HEIGHT = 46f;
    public static final float ZONE_PADDING = 10f;
    public static final float ZONE_GAP = 18f;

    /** Số ghế (đơn) trong mỗi khối: trái, giữa, phải. */
    static final int[] BLOCK_SEATS = {4, 10, 4};

    private static final long SEED = 20241126L;
    private static final float LOCKED_RATE = 0.05f;

    private MockCinemaRepository() {
    }

    public static CinemaLayout createLayout() {
        Random random = new Random(SEED);

        float[] blockX = new float[BLOCK_SEATS.length];
        float x = SIDE_MARGIN;
        for (int b = 0; b < BLOCK_SEATS.length; b++) {
            blockX[b] = x;
            x += blockWidth(BLOCK_SEATS[b]);
            if (b < BLOCK_SEATS.length - 1) {
                x += AISLE_WIDTH;
            }
        }
        float seatsLeft = blockX[0];
        float seatsRight = x;
        float worldWidth = seatsRight + SIDE_MARGIN;

        WorldRect screen = new WorldRect(seatsLeft + 60f, 24f, seatsRight - 60f, 64f);

        List<Seat> seats = new ArrayList<>();
        List<Zone> zones = new ArrayList<>();
        List<RowLabel> rowLabels = new ArrayList<>();

        float y = 104f;
        float firstZoneTop = y;
        y = addZone(SeatType.STANDARD, "KHU GHẾ THƯỜNG", new String[]{"A", "B", "C", "D"},
                y, blockX, seatsLeft, seatsRight, worldWidth, random, 0.22f, seats, zones, rowLabels);
        y = addZone(SeatType.VIP, "KHU GHẾ VIP", new String[]{"E", "F", "G", "H", "I", "J"},
                y, blockX, seatsLeft, seatsRight, worldWidth, random, 0.35f, seats, zones, rowLabels);
        y = addZone(SeatType.COUPLE, "KHU GHẾ ĐÔI", new String[]{"K", "L"},
                y, blockX, seatsLeft, seatsRight, worldWidth, random, 0.25f, seats, zones, rowLabels);
        float lastZoneBottom = y - ZONE_GAP;

        // Lối đi dọc nằm giữa các khối ghế, kéo dài từ phân khu đầu đến lối vào phía cuối phòng.
        List<WorldRect> aisles = new ArrayList<>();
        for (int b = 0; b < BLOCK_SEATS.length - 1; b++) {
            float left = blockX[b] + blockWidth(BLOCK_SEATS[b]);
            aisles.add(new WorldRect(left, firstZoneTop, left + AISLE_WIDTH, lastZoneBottom + 40f));
        }

        float worldHeight = lastZoneBottom + 64f;
        return new CinemaLayout("Phòng chiếu 03", "Chuyến Tàu Cuối Năm (Mock)", "19:30 · 26/11/2026",
                worldWidth, worldHeight, screen, seats, zones, aisles, rowLabels);
    }

    private static float addZone(SeatType type, String title, String[] rows, float top,
                                 float[] blockX, float seatsLeft, float seatsRight, float worldWidth,
                                 Random random, float bookedRate,
                                 List<Seat> seats, List<Zone> zones, List<RowLabel> rowLabels) {
        // Nhãn phân khu nằm phía trên khối ghế giữa, không đè lên lối đi dọc.
        float headerLeft = blockX[1];
        float headerRight = blockX[1] + blockWidth(BLOCK_SEATS[1]);
        WorldRect header = new WorldRect(headerLeft, top, headerRight, top + ZONE_HEADER_HEIGHT);
        float rowTop = top + ZONE_HEADER_HEIGHT;
        for (String row : rows) {
            int number = 1;
            for (int b = 0; b < BLOCK_SEATS.length; b++) {
                float seatX = blockX[b];
                int slots = BLOCK_SEATS[b];
                // Ghế đôi chiếm 2 vị trí ghế đơn (kể cả khe hở ở giữa).
                int slotsPerSeat = type == SeatType.COUPLE ? 2 : 1;
                float seatWidth = slotsPerSeat * SEAT_SIZE + (slotsPerSeat - 1) * SEAT_GAP;
                for (int s = 0; s < slots; s += slotsPerSeat) {
                    WorldRect bounds = new WorldRect(seatX, rowTop, seatX + seatWidth, rowTop + SEAT_SIZE);
                    seats.add(new Seat(seats.size(), row, number++, type, randomStatus(random, bookedRate), bounds));
                    seatX += seatWidth + SEAT_GAP;
                }
            }
            float centerY = rowTop + SEAT_SIZE * 0.5f;
            rowLabels.add(new RowLabel(row, SIDE_MARGIN * 0.45f, centerY));
            rowLabels.add(new RowLabel(row, worldWidth - SIDE_MARGIN * 0.45f, centerY));
            rowTop += ROW_PITCH;
        }
        float bottom = rowTop - SEAT_GAP + ZONE_PADDING;
        String label = title + " · " + PriceFormatter.format(type.getPrice());
        zones.add(new Zone(type, label,
                new WorldRect(seatsLeft - ZONE_PADDING, top, seatsRight + ZONE_PADDING, bottom), header));
        return bottom + ZONE_GAP;
    }

    private static SeatStatus randomStatus(Random random, float bookedRate) {
        float r = random.nextFloat();
        if (r < LOCKED_RATE) {
            return SeatStatus.LOCKED;
        }
        if (r < LOCKED_RATE + bookedRate) {
            return SeatStatus.BOOKED;
        }
        return SeatStatus.AVAILABLE;
    }

    static float blockWidth(int seatCount) {
        return seatCount * SEAT_SIZE + (seatCount - 1) * SEAT_GAP;
    }
}
