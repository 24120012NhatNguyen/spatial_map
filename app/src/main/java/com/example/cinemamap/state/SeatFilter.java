package com.example.cinemamap.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.cinemamap.data.Seat;
import com.example.cinemamap.data.SeatStatus;
import com.example.cinemamap.data.SeatType;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Điều kiện lọc/tìm kiếm ghế (bất biến, Java thuần).
 *
 * <ul>
 *   <li>Loại ghế (cũng là khu vực): tập rỗng nghĩa là không lọc theo loại.</li>
 *   <li>Chỉ ghế còn trống.</li>
 *   <li>Từ khóa: "E9" → đúng ghế E9, "E" → cả hàng E, "9" → ghế số 9 của mọi hàng.</li>
 * </ul>
 * Các điều kiện kết hợp bằng AND.
 */
public final class SeatFilter {

    // Phải khai báo trước NONE: hằng static được khởi tạo theo thứ tự xuất hiện.
    private static final Pattern QUERY_PATTERN = Pattern.compile("^([A-Z]*)(\\d*)$");

    /** Từ mở đầu được bỏ qua: "hàng E" → "E", "ghế E9" → "E9", "số 9" → "9". */
    private static final List<String> LEADING_WORDS = Arrays.asList("HÀNG", "HANG", "GHẾ", "GHE", "SỐ", "SO");

    public static final SeatFilter NONE = new SeatFilter(EnumSet.noneOf(SeatType.class), false, "");

    private final Set<SeatType> types;
    private final boolean availableOnly;
    /** Từ khóa đã chuẩn hóa: chữ hoa, bỏ khoảng trắng/dấu gạch và từ mở đầu "hàng", "ghế", "số". */
    private final String query;
    @Nullable
    private final String queryRow;
    /** -1 khi từ khóa không có phần số. */
    private final int queryNumber;
    /** Từ khóa sai cú pháp (ví dụ "E9A") thì không ghế nào khớp. */
    private final boolean queryValid;

    private SeatFilter(Set<SeatType> types, boolean availableOnly, String query) {
        EnumSet<SeatType> copy = EnumSet.noneOf(SeatType.class);
        copy.addAll(types);
        this.types = Collections.unmodifiableSet(copy);
        this.availableOnly = availableOnly;
        this.query = query;

        Matcher matcher = QUERY_PATTERN.matcher(query);
        if (query.isEmpty()) {
            queryValid = true;
            queryRow = null;
            queryNumber = -1;
        } else if (matcher.matches()) {
            String letters = matcher.group(1);
            String digits = matcher.group(2);
            queryRow = letters.isEmpty() ? null : letters;
            queryNumber = parseNumber(digits);
            // Phần số quá dài (tràn int) coi như không hợp lệ.
            queryValid = digits.isEmpty() || queryNumber >= 0;
        } else {
            queryValid = false;
            queryRow = null;
            queryNumber = -1;
        }
    }

    @NonNull
    public static SeatFilter of(@NonNull Set<SeatType> types, boolean availableOnly, @Nullable String rawQuery) {
        return new SeatFilter(types, availableOnly, normalizeQuery(rawQuery));
    }

    /** "  e-9 " → "E9", "hàng e" → "E". */
    @NonNull
    public static String normalizeQuery(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String upper = Normalizer.normalize(raw, Normalizer.Form.NFC).toUpperCase(Locale.ROOT).trim();
        String[] tokens = upper.split("[\\s_-]+");
        int start = 0;
        // Bỏ từ mở đầu chỉ khi phía sau còn nội dung ("hàng" một mình vẫn là từ khóa → không khớp).
        if (tokens.length > 1 && LEADING_WORDS.contains(tokens[0])) {
            start = 1;
        }
        StringBuilder sb = new StringBuilder(upper.length());
        for (int i = start; i < tokens.length; i++) {
            sb.append(tokens[i]);
        }
        return sb.toString();
    }

    public boolean isActive() {
        return !types.isEmpty() || availableOnly || !query.isEmpty();
    }

    public boolean hasQuery() {
        return !query.isEmpty();
    }

    public boolean matches(@NonNull Seat seat) {
        if (!types.isEmpty() && !types.contains(seat.getType())) {
            return false;
        }
        if (availableOnly && seat.getStatus() != SeatStatus.AVAILABLE) {
            return false;
        }
        if (query.isEmpty()) {
            return true;
        }
        if (!queryValid) {
            return false;
        }
        if (queryRow != null && !queryRow.equals(seat.getRow())) {
            return false;
        }
        return queryNumber < 0 || queryNumber == seat.getNumber();
    }

    @NonNull
    public SeatFilter withType(@NonNull SeatType type, boolean enabled) {
        EnumSet<SeatType> next = EnumSet.noneOf(SeatType.class);
        next.addAll(types);
        if (enabled) {
            next.add(type);
        } else {
            next.remove(type);
        }
        return new SeatFilter(next, availableOnly, query);
    }

    @NonNull
    public SeatFilter withAvailableOnly(boolean value) {
        return new SeatFilter(types, value, query);
    }

    @NonNull
    public SeatFilter withQuery(@Nullable String rawQuery) {
        return new SeatFilter(types, availableOnly, normalizeQuery(rawQuery));
    }

    @NonNull
    public Set<SeatType> getTypes() {
        return types;
    }

    public boolean isAvailableOnly() {
        return availableOnly;
    }

    @NonNull
    public String getQuery() {
        return query;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SeatFilter)) {
            return false;
        }
        SeatFilter other = (SeatFilter) o;
        return availableOnly == other.availableOnly && types.equals(other.types) && query.equals(other.query);
    }

    @Override
    public int hashCode() {
        return (types.hashCode() * 31 + (availableOnly ? 1 : 0)) * 31 + query.hashCode();
    }

    private static int parseNumber(String digits) {
        if (digits.isEmpty() || digits.length() > 9) {
            return -1;
        }
        return Integer.parseInt(digits);
    }
}
