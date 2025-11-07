package com.example.imagepicker;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public class BoxOverlayView extends View {

    public static class Detection {
        public final float x1, y1, x2, y2;
        public final String label;
        public final float score;
        public Detection(float x1, float y1, float x2, float y2, String label, float score) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2; this.label = label; this.score = score;
        }
    }

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect  textBounds = new Rect();

    private final List<Detection> detections = new ArrayList<>();
    private int origW = 0, origH = 0;

    public BoxOverlayView(Context c) { this(c, null); }
    public BoxOverlayView(Context c, AttributeSet a) {
        super(c, a);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        boxPaint.setColor(Color.RED);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);

        bgPaint.setColor(0x80000000); // translucent black
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void setDetections(List<Detection> list, int originalWidth, int originalHeight) {
        detections.clear();
        if (list != null) detections.addAll(list);
        this.origW = originalWidth;
        this.origH = originalHeight;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (origW <= 0 || origH <= 0 || detections.isEmpty()) return;

        int vw = getWidth(), vh = getHeight();

        // match ImageView's FIT_CENTER mapping
        float scale = Math.min(vw / (float)origW, vh / (float)origH);
        float dx = (vw - origW * scale) / 2f;
        float dy = (vh - origH * scale) / 2f;

        for (Detection d : detections) {
            float left   = dx + d.x1 * scale;
            float top    = dy + d.y1 * scale;
            float right  = dx + d.x2 * scale;
            float bottom = dy + d.y2 * scale;

            // clamp to view
            left = Math.max(0, left); top = Math.max(0, top);
            right = Math.min(vw - 1, right); bottom = Math.min(vh - 1, bottom);

            // draw rect
            canvas.drawRect(left, top, right, bottom, boxPaint);

            String text = d.label + String.format(" %.2f", d.score);
            textPaint.getTextBounds(text, 0, text.length(), textBounds);
            float bgLeft = left;
            float bgTop  = Math.max(0, top - textBounds.height() - 8);
            canvas.drawRect(bgLeft, bgTop, bgLeft + textBounds.width() + 16, bgTop + textBounds.height() + 12, bgPaint);
            canvas.drawText(text, bgLeft + 8, bgTop + textBounds.height() + 6, textPaint);
        }
    }
}