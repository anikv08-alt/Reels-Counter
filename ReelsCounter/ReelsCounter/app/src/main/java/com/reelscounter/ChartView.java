package com.reelscounter;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Simple bar chart with one highlighted bar (gold). */
public class ChartView extends View {

    private int[] values = new int[0];
    private String[] labels = new String[0];
    private boolean showValues = true;
    private int highlight = -1;
    private float progress = 1f;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint todayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    public ChartView(Context context) {
        super(context);
        barPaint.setColor(0xFF8C2341);   // burgundy
        todayPaint.setColor(0xFFD4AF7A); // gold = today
        emptyPaint.setColor(0xFF38363E);
        textPaint.setColor(0xFF9E9CA6);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(px(TypedValue.COMPLEX_UNIT_SP, 11));
    }

    void setData(int[] values, String[] labels, boolean showValues, int highlight) {
        this.highlight = highlight;
        this.values = values;
        this.labels = labels;
        this.showValues = showValues;
        // bars grow up from the bottom
        ValueAnimator grow = ValueAnimator.ofFloat(0f, 1f);
        grow.setDuration(900);
        grow.setStartDelay(200);
        grow.setInterpolator(new DecelerateInterpolator(2f));
        grow.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        progress = 0f;
        grow.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (int) px(TypedValue.COMPLEX_UNIT_DIP, 150));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        if (n == 0) return;
        float w = getWidth();
        float h = getHeight();
        float top = px(TypedValue.COMPLEX_UNIT_DIP, 18);
        float bottom = h - px(TypedValue.COMPLEX_UNIT_DIP, 20);
        float radius = px(TypedValue.COMPLEX_UNIT_DIP, 3);

        int max = 1;
        for (int v : values) max = Math.max(max, v);
        float slot = w / n;
        float barWidth = slot * 0.62f;

        for (int i = 0; i < n; i++) {
            float cx = slot * i + slot / 2f;
            float barHeight = (bottom - top) * values[i] / (float) max * progress;
            if (values[i] == 0) {
                rect.set(cx - barWidth / 2f, bottom - px(TypedValue.COMPLEX_UNIT_DIP, 2), cx + barWidth / 2f, bottom);
                canvas.drawRoundRect(rect, radius, radius, emptyPaint);
            } else {
                barHeight = Math.max(barHeight, px(TypedValue.COMPLEX_UNIT_DIP, 3));
                rect.set(cx - barWidth / 2f, bottom - barHeight, cx + barWidth / 2f, bottom);
                canvas.drawRoundRect(rect, radius, radius, i == highlight ? todayPaint : barPaint);
                if (showValues && progress > 0.95f) {
                    canvas.drawText(String.valueOf(values[i]), cx, bottom - barHeight - px(TypedValue.COMPLEX_UNIT_DIP, 4), textPaint);
                }
            }
            if (i < labels.length && labels[i] != null && labels[i].length() > 0) {
                canvas.drawText(labels[i], cx, h - px(TypedValue.COMPLEX_UNIT_DIP, 4), textPaint);
            }
        }
    }

    private float px(int unit, float value) {
        return TypedValue.applyDimension(unit, value, getResources().getDisplayMetrics());
    }
}
