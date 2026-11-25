package com.example.cinemamap.ui.map;

/**
 * Quản lý phép biến đổi giữa tọa độ màn hình (screen, pixel) và tọa độ bản đồ (world).
 *
 * <pre>
 *   screenX = worldX * scale + translateX
 *   screenY = worldY * scale + translateY
 *
 *   worldX  = (screenX - translateX) / scale
 *   worldY  = (screenY - translateY) / scale
 * </pre>
 *
 * Lớp này không phụ thuộc Android SDK để có thể kiểm thử bằng JUnit thuần.
 * Mọi phép Pan/Zoom đều đi qua đây nên giới hạn tỷ lệ và giới hạn biên luôn được áp dụng.
 */
public final class Viewport {

    /** Tỷ lệ nhỏ nhất so với tỷ lệ "vừa khít màn hình". */
    public static final float MIN_ZOOM = 0.75f;
    /** Tỷ lệ lớn nhất so với tỷ lệ "vừa khít màn hình". */
    public static final float MAX_ZOOM = 4f;

    private final float fitPadding;
    private final float panMargin;

    private float contentWidth;
    private float contentHeight;
    private float viewWidth;
    private float viewHeight;
    private float insetLeft;
    private float insetTop;
    private float insetRight;
    private float insetBottom;

    private float fitScale = 1f;
    private float minScale = MIN_ZOOM;
    private float maxScale = MAX_ZOOM;

    private float scale = 1f;
    private float translateX;
    private float translateY;

    /**
     * @param fitPadding khoảng đệm (px) quanh sơ đồ ở góc nhìn mặc định
     * @param panMargin  khoảng trống tối đa (px) được phép kéo lộ ra ở mép sơ đồ
     */
    public Viewport(float fitPadding, float panMargin) {
        this.fitPadding = fitPadding;
        this.panMargin = panMargin;
    }

    public boolean isReady() {
        return viewWidth > 0 && viewHeight > 0 && contentWidth > 0 && contentHeight > 0;
    }

    public void setContentSize(float width, float height) {
        contentWidth = width;
        contentHeight = height;
        recomputeScaleLimits();
        if (isReady()) {
            resetToDefault();
        }
    }

    /**
     * Cập nhật kích thước View. Nếu đã có góc nhìn trước đó, giữ nguyên mức zoom tương đối
     * và điểm bản đồ ở tâm (ví dụ khi đổi kích thước cửa sổ).
     */
    public void setViewSize(float width, float height) {
        boolean hadState = isReady();
        float zoom = 1f;
        float centerX = 0f;
        float centerY = 0f;
        if (hadState) {
            zoom = getZoomLevel();
            centerX = getWorldCenterX();
            centerY = getWorldCenterY();
        }
        viewWidth = width;
        viewHeight = height;
        recomputeScaleLimits();
        if (!isReady()) {
            return;
        }
        if (hadState) {
            restore(zoom, centerX, centerY);
        } else {
            resetToDefault();
        }
    }

    /** Vùng bị che (thanh công cụ, bottom sheet...) – không tính vào vùng hiển thị sơ đồ. */
    public void setInsets(float left, float top, float right, float bottom) {
        insetLeft = Math.max(0f, left);
        insetTop = Math.max(0f, top);
        insetRight = Math.max(0f, right);
        insetBottom = Math.max(0f, bottom);
        recomputeScaleLimits();
        if (isReady()) {
            scale = clamp(scale, minScale, maxScale);
            clampTranslation();
        }
    }

    /** Góc nhìn mặc định: toàn bộ sơ đồ vừa khít và nằm giữa vùng hiển thị. */
    public void resetToDefault() {
        scale = fitScale;
        translateX = availableCenterX() - contentWidth * scale * 0.5f;
        translateY = availableCenterY() - contentHeight * scale * 0.5f;
        clampTranslation();
    }

    /**
     * Đặt góc nhìn theo mức zoom tương đối và điểm bản đồ cần nằm ở tâm vùng hiển thị.
     * Dùng để khôi phục góc nhìn sau khi xoay màn hình.
     */
    public void restore(float zoomLevel, float worldCenterX, float worldCenterY) {
        scale = clamp(zoomLevel * fitScale, minScale, maxScale);
        translateX = availableCenterX() - worldCenterX * scale;
        translateY = availableCenterY() - worldCenterY * scale;
        clampTranslation();
    }

    /**
     * Dịch chuyển sơ đồ theo pixel màn hình.
     *
     * @return true nếu góc nhìn thực sự thay đổi (false khi đã chạm biên)
     */
    public boolean panBy(float dx, float dy) {
        float oldX = translateX;
        float oldY = translateY;
        translateX += dx;
        translateY += dy;
        clampTranslation();
        return oldX != translateX || oldY != translateY;
    }

    /**
     * Phóng to/thu nhỏ quanh tiêu điểm (focusX, focusY) trên màn hình.
     * Điểm bản đồ nằm dưới tiêu điểm được giữ cố định:
     * <pre>
     *   s'  = clamp(s * factor)
     *   k   = s' / s
     *   t'  = focus - (focus - t) * k
     * </pre>
     */
    public void zoomAround(float focusX, float focusY, float factor) {
        if (factor <= 0f || Float.isNaN(factor) || Float.isInfinite(factor)) {
            return;
        }
        float newScale = clamp(scale * factor, minScale, maxScale);
        float k = newScale / scale;
        translateX = focusX - (focusX - translateX) * k;
        translateY = focusY - (focusY - translateY) * k;
        scale = newScale;
        clampTranslation();
    }

    public float screenToWorldX(float screenX) {
        return (screenX - translateX) / scale;
    }

    public float screenToWorldY(float screenY) {
        return (screenY - translateY) / scale;
    }

    public float worldToScreenX(float worldX) {
        return worldX * scale + translateX;
    }

    public float worldToScreenY(float worldY) {
        return worldY * scale + translateY;
    }

    /** Ghi vùng bản đồ đang nhìn thấy vào {@code out} = [left, top, right, bottom] (world). */
    public void getVisibleWorldRect(float[] out) {
        out[0] = screenToWorldX(0f);
        out[1] = screenToWorldY(0f);
        out[2] = screenToWorldX(viewWidth);
        out[3] = screenToWorldY(viewHeight);
    }

    public float getScale() {
        return scale;
    }

    public float getTranslateX() {
        return translateX;
    }

    public float getTranslateY() {
        return translateY;
    }

    public float getMinScale() {
        return minScale;
    }

    public float getMaxScale() {
        return maxScale;
    }

    public float getFitScale() {
        return fitScale;
    }

    /** Mức zoom tương đối: 1 = vừa khít màn hình. */
    public float getZoomLevel() {
        return scale / fitScale;
    }

    public float getWorldCenterX() {
        return screenToWorldX(availableCenterX());
    }

    public float getWorldCenterY() {
        return screenToWorldY(availableCenterY());
    }

    private void recomputeScaleLimits() {
        float availableWidth = viewWidth - insetLeft - insetRight - 2f * fitPadding;
        float availableHeight = viewHeight - insetTop - insetBottom - 2f * fitPadding;
        if (availableWidth <= 0f || availableHeight <= 0f || contentWidth <= 0f || contentHeight <= 0f) {
            return;
        }
        fitScale = Math.min(availableWidth / contentWidth, availableHeight / contentHeight);
        minScale = fitScale * MIN_ZOOM;
        maxScale = fitScale * MAX_ZOOM;
    }

    /**
     * Giới hạn Pan: sơ đồ không bao giờ bị kéo ra khỏi vùng hiển thị.
     * Mép sơ đồ chỉ được lùi vào trong tối đa {@code panMargin} px; nếu sơ đồ nhỏ hơn vùng
     * hiển thị thì toàn bộ sơ đồ phải nằm trong vùng đó.
     */
    private void clampTranslation() {
        translateX = clampAxis(translateX, contentWidth * scale, insetLeft, viewWidth - insetRight);
        translateY = clampAxis(translateY, contentHeight * scale, insetTop, viewHeight - insetBottom);
    }

    private float clampAxis(float translate, float scaledContent, float start, float end) {
        float a = end - panMargin - scaledContent; // mép phải/dưới của sơ đồ chạm (end - margin)
        float b = start + panMargin;               // mép trái/trên của sơ đồ chạm (start + margin)
        return clamp(translate, Math.min(a, b), Math.max(a, b));
    }

    private float availableCenterX() {
        return (insetLeft + viewWidth - insetRight) * 0.5f;
    }

    private float availableCenterY() {
        return (insetTop + viewHeight - insetBottom) * 0.5f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
