package fit.nsu.com.ratewidgetsample;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.AccelerateInterpolator;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.PublishSubject;

import static java.lang.Math.abs;
import static java.lang.Math.log;

/**
 * Created by hekpo on 13.12.2017.
 */

public class RateWidget extends View {

    private final static int INIT_STATE = -1;
    private final static String CURRENT_STATE_TAG = "current_state_tag";
    private final static int DEFAULT_COLOR_SELECTED = 0xfffea002;

    private Paint mPaint;
    private int mLineStrokeWidth;
    private int mInnerPointRadius;
    private int mOuterPointRadius;
    private float mDeltedOuterPointRadius;
    private int mSelectedPointRadius;
    private float mLineCoordinateY;

    private int mPointNumber;
    private List<Integer> mPointsCenters = new ArrayList<>();

    private Bitmap mBitmap;
    private Integer mBackgroundColor;
    private Integer mSelectedColor;
    private Integer mSimpleColor;

    private float mSelectedTextSize = 40f;
    private float mSimpleTextSize = 30f;
    private float mSelectedTextY;
    private float mSimpleTextY;
    private int mSimpleTextColor = 0x61000000;

    private boolean mIsAnimationRunning = false;

    //we need it because world is imperfect and java's float computations too
    float mDelta = 0.01f;
    float mRadiusDelta = 1f;

    private int currentRate = INIT_STATE;

    private PublishSubject<Integer> mRatedSubject = PublishSubject.create();
    private PublishSubject<PointF> mClicksSubject = PublishSubject.create();
    private Disposable mClicksSubscription;

    private RateWidget instant = this;

    public RateWidget(Context context) {
        super(context);
    }

    public RateWidget(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initValues(attrs);
    }

    public RateWidget(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initValues(attrs);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state){
        if(!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState savedState = (SavedState)state;
        super.onRestoreInstanceState(savedState.getSuperState());

        currentRate = savedState.stateToSave;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mClicksSubscription = subscribeToClicks(mClicksSubject.hide());
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mClicksSubscription.dispose();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState savedState = new SavedState(superState);

        savedState.stateToSave = currentRate;
        return savedState;
    }

    private void initValues(AttributeSet attrs){
        if (attrs != null){
            String color = attrs.getAttributeValue(null, CustomAttributes.SELECTED_COLOR);
            if (color != null) {
                mSelectedColor = (int) Long.parseLong(color.substring(1), 16);
            }

            color = attrs.getAttributeValue(null, CustomAttributes.SIMPLE_COLOR);
            mSimpleColor = color != null ? (int) Long.parseLong(color.substring(1), 16) : 0xFFD8D8D8;

            color = attrs.getAttributeValue(null, CustomAttributes.BACKGROUND_COLOR);
            mBackgroundColor = color != null ? (int) Long.parseLong(color.substring(1), 16) : 0xFFFFFFFF;

            mPointNumber = attrs.getAttributeIntValue(null, CustomAttributes.MAX_VALUE, 10) + 1;
        }
        initValues();
    }

    Canvas mCanvas;
    private void initValues() {
        mInnerPointRadius = 8;
        initElementsValues();
        mPointNumber = 5;

        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);
    }

    private void initElementsValues(){
        mLineStrokeWidth = mInnerPointRadius;
        mOuterPointRadius = (mInnerPointRadius << 1);
        mDeltedOuterPointRadius = mOuterPointRadius + mRadiusDelta;
        mSelectedPointRadius = (mOuterPointRadius << 1);
    }


    /**
     * This method calculate correct text Y coordinates so as to
     * selected and simple text are on one line by horizontal line
     */
    private void calculateTextCoordinates(){
        Rect bounds = new Rect();
        float selectedMedian, simpleMedian;
        mPaint.setTextSize(mSimpleTextSize);
        mPaint.getTextBounds("0", 0, 1, bounds);
        simpleMedian = bounds.exactCenterY();
        mPaint.setTextSize(mSelectedTextSize);
        mPaint.getTextBounds("0", 0, 1, bounds);
        selectedMedian = bounds.exactCenterY();
        mSimpleTextY = mSelectedTextY - abs(simpleMedian - selectedMedian);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mBitmap == null){
            mBitmap = getInitialBitmap();
        }
        canvas.drawBitmap(mBitmap, 0, 0, mPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
            mClicksSubject.onNext(new PointF(event.getX(), event.getY()));
        }
        return super.onTouchEvent(event);
    }


    /**
     * Create default line and init some values
     * @return default line
     */
    private Bitmap getInitialBitmap(){
        mLineCoordinateY = ((float)getHeight() - (mSelectedPointRadius << 1) + ((mSelectedPointRadius >> 1) >> 1));
        //Do u not like bits operations? U just don't know how to cook it!
        mSelectedTextY = mLineCoordinateY - (mSelectedPointRadius << 1) + (mSelectedPointRadius >> 1);
        calculateTextCoordinates();

        Bitmap bitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
        if (mSelectedColor == null){
            mSelectedColor = getDefaultColorSelected();
        }

        mCanvas = new Canvas(bitmap);
        mPaint.setColor(mSimpleColor);
        mPaint.setStrokeWidth(mLineStrokeWidth);
        int offset = mSelectedPointRadius;
        mCanvas.drawLine(
                (float) offset,
                mLineCoordinateY,
                (float) (getWidth() - offset),
                mLineCoordinateY,
                mPaint);

        float step = (getWidth() - (offset << 1)) / (mPointNumber - 1);
        int currentX = offset;

        for (int i = 0; i < mPointNumber; ++i, currentX += step){
            mPointsCenters.add(currentX);
            mPaint.setColor(mSimpleColor);
            mCanvas.drawCircle((float)currentX, mLineCoordinateY, mOuterPointRadius, mPaint);
            mPaint.setColor(mBackgroundColor);
            mCanvas.drawCircle((float)currentX, mLineCoordinateY, mInnerPointRadius, mPaint);
        }

        drawRateText(0, false, mCanvas);
        drawRateText(mPointNumber - 1, false, mCanvas);

        if (isRated()){
            restoreBitmapState();
        }

        return bitmap;
    }

    private void restoreBitmapState(){
        int offset = mSelectedPointRadius;
        mPaint.setColor(mSelectedColor);
        mCanvas.drawLine(
                (float) offset,
                mLineCoordinateY,
                (float) (mPointsCenters.get(currentRate)),
                mLineCoordinateY,
                mPaint);
        for (int i = 0; i < currentRate; ++i){
            mCanvas.drawCircle((float)(mPointsCenters.get(i)), mLineCoordinateY, mOuterPointRadius, mPaint);
        }
        mCanvas.drawCircle((float)(mPointsCenters.get(currentRate)), mLineCoordinateY, mSelectedPointRadius, mPaint);
        clearPointTextArea(currentRate);
        drawRateText(currentRate, true, mCanvas);
    }

    private void initStartPoint(){
        clearPointTextArea(0);
        float currentPointCoordinate = mPointsCenters.get(0);
        mPaint.setColor(mSelectedColor);
        mCanvas.drawCircle(currentPointCoordinate, mLineCoordinateY, mSelectedPointRadius, mPaint);

        drawRateText(0, true, mCanvas);
        this.invalidate();
    }

    private void clearPointTextArea(int pointNumber){
        mPaint.setTextSize(mSelectedTextSize);
        mPaint.getTextBounds(Integer.toString(pointNumber), 0, Integer.toString(pointNumber).length(), mBounds);
        mPaint.setColor(mBackgroundColor);
        float x = mPointsCenters.get(pointNumber);
        mCanvas.drawRect(x - mBounds.exactCenterX() - mRadiusDelta,
                mSelectedTextY - mBounds.height() - mRadiusDelta,
                x + mBounds.exactCenterX() + mRadiusDelta,
                mSelectedTextY + mRadiusDelta, mPaint);
    }

    private void drawRateText(int rate, boolean selected, Canvas canvas){
        String text = Integer.toString(rate + 1);
        mPaint.setColor(selected ? mSelectedColor : mSimpleTextColor);
        mPaint.setTextSize(selected ? mSelectedTextSize : mSimpleTextSize);
        mPaint.setTypeface(Typeface.create(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL));
        canvas.drawText(text, getCenteredTextCoordinateX(text, mPaint, mPointsCenters.get(rate)), selected ? mSelectedTextY : mSimpleTextY, mPaint);
    }

    public void drawRate(int startRate, int targetRate){
        if (startRate == targetRate){
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration((long)((log(abs(targetRate - startRate)) + 4) * 80));
        animator.setInterpolator(new AccelerateInterpolator());
        animator.addUpdateListener(new CustomValueAnimatorListener(startRate, targetRate));
        animator.start();
        mIsAnimationRunning = true;

        animator.addListener(new AnimatorListenerStub() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mIsAnimationRunning = false;
                // If user has picked another rate while animator was working
                // we should run animation once again
                if (currentRate != targetRate){
                    drawRate(targetRate, currentRate);
                }
            }
        });
    }

    /**
     * Try to take view background, view's parent background and system window background consistently.
     *
     * @return background color
     */
    private int getWidgetBackgroundColor() {
        Drawable local = this.getBackground();
        if (local != null) {
            return ((ColorDrawable)local).getColor();
        }
        ViewParent parent = this.getParent();
        if (parent != null && ((View) parent).getBackground() != null &&
                (((View) parent).getBackground() instanceof ColorDrawable)){
            return ((ColorDrawable)((View) parent).getBackground()).getColor();
        }

        return getBackgroundWindowColor();
    }

    public PublishSubject<Integer> getRatedSubject(){
        return mRatedSubject;
    }

    private int getBackgroundWindowColor(){
        return getColorFromDefaultStyle(android.R.attr.windowBackground);
    }

    private int getDefaultColorSelected(){

        return android.os.Build.VERSION.SDK_INT >= 21 ?
                getColorFromDefaultStyle(android.R.attr.colorPrimary) :
                DEFAULT_COLOR_SELECTED;
    }

    private int getColorFromDefaultStyle(int attr) {
        int[] attrs = {attr};
        TypedArray arr = getContext().obtainStyledAttributes(R.style.AppTheme, attrs);
        int color = arr.getColor(0, 0xFF000000);
        arr.recycle();
        return color;
    }


    private Rect mBounds = new Rect();

    /**
     * Method center X coordinate of text by center
     *
     * @param text target text
     * @param paint set paint
     * @param center target center
     * @return centered coordinate
     */
    private float getCenteredTextCoordinateX(String text, Paint paint, float center){
        paint.getTextBounds(text, 0, text.length(), mBounds);
        return center - mBounds.exactCenterX();
    }

    public int getCurrentRate() {
        return currentRate + 1;
    }

    public boolean isRated(){
        return currentRate != INIT_STATE;
    }

    public void init(){
        mBitmap = null;
        currentRate = INIT_STATE;
        invalidate();
    }

    public class CustomAttributes{
        public final static String SELECTED_COLOR = "selected_color";
        public final static String SIMPLE_COLOR = "simple_color";
        public final static String BACKGROUND_COLOR = "background_color";
        public final static String MAX_VALUE = "max_value";
    }

    private class CustomValueAnimatorListener implements ValueAnimator.AnimatorUpdateListener{
        // 1 -- forward | -1 -- backward
        int directionCoef;
        // coordinates of start and end points
        int startX, endX;
        int wayLength;

        //prev animation value
        float prevValue = 0f;
        int currentPointDrawed = 0;
        //just buffer variable
        float currentPointCoordinate = 0f;

        int mainColor;

        //just buffer variable
        float currentStart;

        String targetRateString;

        int startRate;
        int targetRate;

        public CustomValueAnimatorListener(int startRate, int targetRate){
            this.startRate = startRate;
            this.targetRate = targetRate;
            startX = mPointsCenters.get(startRate);
            endX = mPointsCenters.get(targetRate);
            wayLength = abs(endX - startX);
            directionCoef = (endX - startX) >= 0 ? 1 : -1;
            mainColor = directionCoef == 1 ? mSelectedColor : mSimpleColor;
            mPaint.setColor(mainColor);
            targetRateString = Integer.toString(targetRate);

            clearCurrentSelectedPoint();
        }

        private void clearCurrentSelectedPoint(){
            currentPointCoordinate = mPointsCenters.get(startRate);
            mPaint.setColor(mBackgroundColor);
            mCanvas.drawCircle(currentPointCoordinate, mLineCoordinateY, mSelectedPointRadius + mRadiusDelta, mPaint);
            mPaint.setColor(mainColor);
            mCanvas.drawLine(
                    currentPointCoordinate + directionCoef * (mSelectedPointRadius + mRadiusDelta),
                    mLineCoordinateY,
                    currentPointCoordinate + (-1) * directionCoef * (startRate == 0 || startRate == (mPointNumber - 1) ? mOuterPointRadius : mSelectedPointRadius + mRadiusDelta),
                    mLineCoordinateY, mPaint
            );


            clearPointTextArea(startRate);
            if (startRate == 0 || startRate == mPointNumber - 1){
                drawRateText(startRate, false, mCanvas);
            }

            mPaint.setColor(mainColor);
        }

        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            float currentValue = (float) (animation.getAnimatedValue());

            currentStart = directionCoef * (prevValue - mDelta) * wayLength + startX;
            mCanvas.drawLine(
                    currentStart,
                    mLineCoordinateY,
                    directionCoef * (currentValue) * wayLength + startX ,
                    mLineCoordinateY,
                    mPaint
            );

            currentPointCoordinate = mPointsCenters.get(startRate + directionCoef * currentPointDrawed);
            if (abs(currentStart - startX) - abs(currentPointCoordinate - startX) >= mSelectedPointRadius){
                clearPointArea();
                if (directionCoef == 1) {
                    mCanvas.drawCircle(currentPointCoordinate, mLineCoordinateY, mDeltedOuterPointRadius, mPaint);
                } else {
                    mCanvas.drawCircle(currentPointCoordinate, mLineCoordinateY, mDeltedOuterPointRadius, mPaint);
                    mPaint.setColor(mBackgroundColor);
                    mCanvas.drawCircle(currentPointCoordinate, mLineCoordinateY, mInnerPointRadius, mPaint);
                    mPaint.setColor(mainColor);
                }
                currentPointDrawed++;
            }

            if ((1.0f - currentValue) < 0.01){
                if (targetRate == 0 || targetRate == mPointNumber - 1){
                    clearPointTextArea(targetRate);
                }
                mPaint.setColor(mSelectedColor);
                mCanvas.drawCircle(currentPointCoordinate, mLineCoordinateY, mSelectedPointRadius, mPaint);
                drawRateText(targetRate, true, mCanvas);
            }
            prevValue = currentValue;

            instant.invalidate();
        }

        private void clearPointArea(){
            mPaint.setColor(mBackgroundColor);
            mCanvas.drawRect(
                    new Rect(
                            (int)currentPointCoordinate - mOuterPointRadius,
                            (int)mLineCoordinateY + mOuterPointRadius + (int)mRadiusDelta * 2,
                            (int)currentPointCoordinate + mOuterPointRadius,
                            (int)mLineCoordinateY - mOuterPointRadius - (int)mRadiusDelta * 2
                    ),
                    mPaint
            );
            mPaint.setColor(mainColor);
        }
    }

    //stuff for saving state when activity recreated
    static class SavedState extends BaseSavedState {
        int stateToSave;

        SavedState(Parcelable superState) {
            super(superState);
        }

        private SavedState(Parcel in) {
            super(in);
            this.stateToSave = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(this.stateToSave);
        }

        //required field that makes Parcelables from a Parcel
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
                    public SavedState createFromParcel(Parcel in) {
                        return new SavedState(in);
                    }
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                };
    }

    private Disposable subscribeToClicks(Observable<PointF> clicksObservable){
        return clicksObservable
                .flatMap(pointF -> {
                    for (int i = 0; i < mPointsCenters.size(); ++i) {
                        if (abs(pointF.x - (float)mPointsCenters.get(i)) < mSelectedPointRadius) {
                            return Observable.just(i);
                        }
                    }
                    return Observable.empty();
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        i -> {
                            if (currentRate == RateWidget.INIT_STATE){
                                currentRate = 0;
                                initStartPoint();
                            }
                            // We run animation only if previous has been done
                            // and just change variable otherwise
                            if (!mIsAnimationRunning) {
                                drawRate(currentRate, i);
                            }
                            currentRate = i;
                            mRatedSubject.onNext(i);
                        }
                );
    }

    private abstract class AnimatorListenerStub implements Animator.AnimatorListener{

        @Override
        public void onAnimationStart(Animator animation) { }

        @Override
        public void onAnimationCancel(Animator animation) { }

        @Override
        public void onAnimationRepeat(Animator animation) { }
    }
}
