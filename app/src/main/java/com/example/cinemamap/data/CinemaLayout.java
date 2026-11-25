package com.example.cinemamap.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Toàn bộ dữ liệu hình học của một phòng chiếu. Hoàn toàn độc lập với lớp vẽ:
 * lớp View chỉ đọc dữ liệu này, không sở hữu hay sửa đổi nó.
 */
public final class CinemaLayout {
    private final String roomName;
    private final String movieTitle;
    private final String showtime;
    private final float width;
    private final float height;
    private final WorldRect screenBounds;
    private final List<Seat> seats;
    private final List<Zone> zones;
    private final List<WorldRect> aisles;
    private final List<RowLabel> rowLabels;
    private final Map<String, Seat> seatsById;

    public CinemaLayout(String roomName, String movieTitle, String showtime,
                        float width, float height, WorldRect screenBounds,
                        List<Seat> seats, List<Zone> zones, List<WorldRect> aisles,
                        List<RowLabel> rowLabels) {
        this.roomName = roomName;
        this.movieTitle = movieTitle;
        this.showtime = showtime;
        this.width = width;
        this.height = height;
        this.screenBounds = screenBounds;
        this.seats = Collections.unmodifiableList(seats);
        this.zones = Collections.unmodifiableList(zones);
        this.aisles = Collections.unmodifiableList(aisles);
        this.rowLabels = Collections.unmodifiableList(rowLabels);
        Map<String, Seat> byId = new HashMap<>();
        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);
            if (seat.getIndex() != i) {
                throw new IllegalArgumentException("Seat index mismatch at " + i);
            }
            if (byId.put(seat.getId(), seat) != null) {
                throw new IllegalArgumentException("Duplicate seat id " + seat.getId());
            }
        }
        this.seatsById = Collections.unmodifiableMap(byId);
    }

    public String getRoomName() {
        return roomName;
    }

    public String getMovieTitle() {
        return movieTitle;
    }

    public String getShowtime() {
        return showtime;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public WorldRect getScreenBounds() {
        return screenBounds;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public List<Zone> getZones() {
        return zones;
    }

    public List<WorldRect> getAisles() {
        return aisles;
    }

    public List<RowLabel> getRowLabels() {
        return rowLabels;
    }

    /** @return ghế có mã {@code id}, hoặc null nếu không tồn tại. */
    public Seat findSeat(String id) {
        return id == null ? null : seatsById.get(id);
    }
}
