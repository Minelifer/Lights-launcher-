package com.example.lightlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Тонкая скруглённая полоска заполнения, рисуется вручную, чтобы не тянуть лишние ресурсы. */
public class MemoryBar extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float fraction;

    public MemoryBar(Context context) {
        super(context);
        trackPaint.setColor(0x33FFFFFF);
        fillPaint.setColor(0xFF4CAF50);
    }

    /** fraction от 0 до 1, color цвет заполненной части. */
    public void setFraction(float fraction, int color) {
        this.fraction = Math.max(0f, Math.min(1f, fraction));
        fillPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float width = getWidth();
        float height = getHeight();
        float radius = height / 2f;

        rect.set(0, 0, width, height);
        canvas.drawRoundRect(rect, radius, radius, trackPaint);

        if (fraction > 0f) {
            rect.set(0, 0, Math.max(height, width * fraction), height);
            canvas.drawRoundRect(rect, radius, radius, fillPaint);
        }
    }
}
