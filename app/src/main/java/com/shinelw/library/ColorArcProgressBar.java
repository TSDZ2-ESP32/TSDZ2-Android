package com.shinelw.library;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import spider65.ebike.tsdz2_esp32.R;

/**
 * colorful arc progress bar
 * Created by shinelw on 12/4/15.
 */
public class ColorArcProgressBar extends View{

    private static final String TAG = "ColorArcProgressBar";
    private int diameter = 500;  //直径
    private float centerX;  //圆心X坐标
    private float centerY;  //圆心Y坐标

    private Paint allArcPaint;
    private Paint progressPaint;
    private Paint vTextPaint;
    private Paint hintPaint;
    private Paint degreePaint;
    private Paint curSpeedPaint;

    private RectF bgRect;

    private ValueAnimator progressAnimator;
    private PaintFlagsDrawFilter mDrawFilter;
    private SweepGradient sweepGradient;
    private Matrix rotateMatrix;

    private final float startAngle = 135;
    private float sweepAngle = 270;
    private float currentAngle = 0;
    private float lastAngle;
    private int[] colors = new int[]{Color.GREEN, Color.YELLOW, Color.RED, Color.RED};
    private float maxValues = 60;
    private float current_value = 0;
    private float bgArcWidth = dipToPx(2);
    private float progressWidth = dipToPx(10);
    private float textSize = dipToPx(60);
    private float hintSize = dipToPx(15);
    private final float curSpeedSize = dipToPx(13);
    private final int aniSpeed = 1000;
    private final float longdegree = dipToPx(13);
    private final float shortdegree = dipToPx(5);
    private final int DEGREE_PROGRESS_DISTANCE = dipToPx(8);

    private final String hintColor = "#676767";
    private final String longDegreeColor = "#111111";
    private final String shortDegreeColor = "#111111";
    private final String bgArcColor = "#111111";
    private String titleString;
    private String hintString;

    private boolean isNeedTitle;
    private boolean isNeedUnit;
    private boolean isNeedDial;
    private boolean isNeedContent;

    // sweepAngle / maxValues 的值
    private float k;

    public ColorArcProgressBar(Context context) {
        super(context, null);
        initView();
    }

    public ColorArcProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs, 0);
        initCofig(context, attrs);
        initView();
    }

    public ColorArcProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initCofig(context, attrs);
        initView();
    }

    private void initCofig(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ColorArcProgressBar);
        int color1 = a.getColor(R.styleable.ColorArcProgressBar_front_color1, Color.GREEN);
        int color2 = a.getColor(R.styleable.ColorArcProgressBar_front_color2, color1);
        int color3 = a.getColor(R.styleable.ColorArcProgressBar_front_color3, color1);
        colors = new int[]{color1, color2, color3, color3};

        sweepAngle = a.getInteger(R.styleable.ColorArcProgressBar_total_engle, 270);
        bgArcWidth = a.getDimension(R.styleable.ColorArcProgressBar_back_width, dipToPx(2));
        progressWidth = a.getDimension(R.styleable.ColorArcProgressBar_front_width, dipToPx(10));
        textSize = a.getDimension(R.styleable.ColorArcProgressBar_text_size, dipToPx(60));
        isNeedTitle = a.getBoolean(R.styleable.ColorArcProgressBar_is_need_title, false);
        isNeedContent = a.getBoolean(R.styleable.ColorArcProgressBar_is_need_content, false);
        isNeedUnit = a.getBoolean(R.styleable.ColorArcProgressBar_is_need_unit, false);
        isNeedDial = a.getBoolean(R.styleable.ColorArcProgressBar_is_need_dial, false);
        hintString = a.getString(R.styleable.ColorArcProgressBar_string_unit);
        titleString = a.getString(R.styleable.ColorArcProgressBar_string_title);
        current_value = a.getFloat(R.styleable.ColorArcProgressBar_current_value, 0);
        maxValues = a.getFloat(R.styleable.ColorArcProgressBar_max_value, 60);
        setCurrent_value(current_value);
        setMaxValues(maxValues);
        a.recycle();

    }
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight){
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        int min = (width < height ? width:height);
        diameter = min - (int)(2*longdegree + progressWidth + 2*DEGREE_PROGRESS_DISTANCE);
        bgRect.top = longdegree + progressWidth/2 + DEGREE_PROGRESS_DISTANCE + (height>width?(height-width)/2.f:0);
        bgRect.left = longdegree + progressWidth/2 + DEGREE_PROGRESS_DISTANCE + (width>height?(width-height)/2.f:0);
        bgRect.right = diameter + (longdegree + progressWidth/2 + DEGREE_PROGRESS_DISTANCE);
        bgRect.bottom = diameter + (longdegree + progressWidth/2 + DEGREE_PROGRESS_DISTANCE);
        centerX = width/2;
        centerY = height/2;
        sweepGradient = new SweepGradient(centerX, centerY, colors, null);

        Log.d(TAG, "onSizeChanged: width = " + width);
        Log.d(TAG, "onSizeChanged: height = " + height);
        Log.d(TAG, "onSizeChanged: longdegree = " + longdegree);
        Log.d(TAG, "onSizeChanged: progressWidth = " + progressWidth);
        Log.d(TAG, "onSizeChanged: DEGREE_PROGRESS_DISTANCE = " + DEGREE_PROGRESS_DISTANCE);
        Log.d(TAG, "onSizeChanged: diameter = " + diameter);
        Log.d(TAG, "onSizeChanged: centerX = " + centerX);
        Log.d(TAG, "onSizeChanged: centerY = " + centerY);
    }

    private void initView() {
        //弧形的矩阵区域
        bgRect = new RectF();

        //外部刻度线
        degreePaint = new Paint();
        degreePaint.setColor(Color.parseColor(longDegreeColor));

        //整个弧形
        allArcPaint = new Paint();
        allArcPaint.setAntiAlias(true);
        allArcPaint.setStyle(Paint.Style.STROKE);
        allArcPaint.setStrokeWidth(bgArcWidth);
        allArcPaint.setColor(Color.parseColor(bgArcColor));
        allArcPaint.setStrokeCap(Paint.Cap.ROUND);

        //当前进度的弧形
        progressPaint = new Paint();
        progressPaint.setAntiAlias(true);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setStrokeWidth(progressWidth);
        progressPaint.setColor(Color.GREEN);

        //内容显示文字
        vTextPaint = new Paint();
        vTextPaint.setTextSize(textSize);
        vTextPaint.setColor(Color.BLACK);
        vTextPaint.setTextAlign(Paint.Align.CENTER);

        //显示单位文字
        hintPaint = new Paint();
        hintPaint.setTextSize(hintSize);
        hintPaint.setColor(Color.parseColor(hintColor));
        hintPaint.setTextAlign(Paint.Align.CENTER);

        //显示标题文字
        curSpeedPaint = new Paint();
        curSpeedPaint.setTextSize(curSpeedSize);
        curSpeedPaint.setColor(Color.parseColor(hintColor));
        curSpeedPaint.setTextAlign(Paint.Align.CENTER);

        mDrawFilter = new PaintFlagsDrawFilter(0, Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        rotateMatrix = new Matrix();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        //抗锯齿
        canvas.setDrawFilter(mDrawFilter);

        if (isNeedDial) {
            //画刻度线
            for (int i = 0; i < 40; i++) {
                if (i > 15 && i < 25) {
                    canvas.rotate(9, centerX, centerY);
                    continue;
                }
                if (i % 5 == 0) {
                    degreePaint.setStrokeWidth(dipToPx(2));
                    degreePaint.setColor(Color.parseColor(longDegreeColor));
                    canvas.drawLine(centerX, centerY - diameter / 2.f - progressWidth / 2 - DEGREE_PROGRESS_DISTANCE,
                            centerX, centerY - diameter / 2.f - progressWidth / 2 - DEGREE_PROGRESS_DISTANCE - longdegree, degreePaint);
                } else {
                    degreePaint.setStrokeWidth(dipToPx(1.4f));
                    degreePaint.setColor(Color.parseColor(shortDegreeColor));
                    canvas.drawLine(centerX, centerY - diameter / 2.f - progressWidth / 2 - DEGREE_PROGRESS_DISTANCE - (longdegree - shortdegree) / 2,
                            centerX, centerY - diameter / 2.f - progressWidth / 2 - DEGREE_PROGRESS_DISTANCE - (longdegree - shortdegree) / 2 - shortdegree, degreePaint);
                }

                canvas.rotate(9, centerX, centerY);
            }
        }

        //整个弧
        canvas.drawArc(bgRect, startAngle, sweepAngle, false, allArcPaint);

        //设置渐变色
        rotateMatrix.setRotate(130, centerX, centerY);
        sweepGradient.setLocalMatrix(rotateMatrix);
        progressPaint.setShader(sweepGradient);

        //当前进度
        canvas.drawArc(bgRect, startAngle, currentAngle, false, progressPaint);

        if (isNeedContent) {
            canvas.drawText(String.format("%.0f", current_value), centerX, centerY + textSize / 3, vTextPaint);
        }
        if (isNeedUnit) {
            canvas.drawText(hintString, centerX, centerY + 3 * textSize / 3, hintPaint);
        }
        if (isNeedTitle) {
            canvas.drawText(titleString, centerX, centerY - 2 * textSize / 3, curSpeedPaint);
        }

        invalidate();

    }

    public void setMaxValues(float maxValues) {
        this.maxValues = maxValues;
        k = sweepAngle/maxValues;
    }

    public void setCurrent_value(float currentValues) {
        if (currentValues > maxValues) {
            currentValues = maxValues;
        }
        if (currentValues < 0) {
            currentValues = 0;
        }
        this.current_value = currentValues;
        lastAngle = currentAngle;
        setAnimation(lastAngle, currentValues * k, aniSpeed);
    }

    public void setBgArcWidth(int bgArcWidth) {
        this.bgArcWidth = bgArcWidth;
    }

    public void setProgressWidth(int progressWidth) {
        this.progressWidth = progressWidth;
    }

    public void setTextSize(int textSize) {
        this.textSize = textSize;
    }

    public void setHintSize(int hintSize) {
        this.hintSize = hintSize;
    }

    public void setUnit(String hintString) {
        this.hintString = hintString;
        invalidate();
    }

    public void setDiameter(int diameter) {
        this.diameter = dipToPx(diameter);
    }

    private void setTitle(String title){
        this.titleString = title;
    }

    private void setIsNeedTitle(boolean isNeedTitle) {
        this.isNeedTitle = isNeedTitle;
    }

    private void setIsNeedUnit(boolean isNeedUnit) {
        this.isNeedUnit = isNeedUnit;
    }

    private void setIsNeedDial(boolean isNeedDial) {
        this.isNeedDial = isNeedDial;
    }

    private void setAnimation(float last, float current, int length) {
        progressAnimator = ValueAnimator.ofFloat(last, current);
        progressAnimator.setDuration(length);
        progressAnimator.setTarget(currentAngle);
        progressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                currentAngle= (float) animation.getAnimatedValue();
                current_value = currentAngle/k;
            }
        });
        progressAnimator.start();
    }


    private int dipToPx(float dip) {
        float density = getContext().getResources().getDisplayMetrics().density;
        return (int)(dip * density + 0.5f * (dip >= 0 ? 1 : -1));
    }
}