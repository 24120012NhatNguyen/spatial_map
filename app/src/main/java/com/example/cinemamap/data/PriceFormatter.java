package com.example.cinemamap.data;

/** Định dạng giá kiểu Việt Nam (75000 → "75.000đ"), không phụ thuộc Locale của thiết bị. */
public final class PriceFormatter {

    private PriceFormatter() {
    }

    public static String format(long price) {
        boolean negative = price < 0;
        String digits = Long.toString(Math.abs(price));
        StringBuilder sb = new StringBuilder(digits.length() + 6);
        if (negative) {
            sb.append('-');
        }
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                sb.append('.');
            }
            sb.append(digits.charAt(i));
        }
        return sb.append('đ').toString();
    }
}
