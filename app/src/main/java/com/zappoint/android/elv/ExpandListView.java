package com.zappoint.android.elv;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.OverScroller;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExpandListView extends AdapterView<Adapter> {
    final static int MESSAGE_DELAY = 20;
    final static float DEFAULT_CARD_RATIO = 1.75f;
    final static int DEFAULT_MIN_HEIGHT = 300;
    private final static int LIST_END_EPSILON = 10;
    private final static int MIN_VELOCITY = 50;
    private final static int MIN_TRAVEL_DISTANCE = 10;
    private int mMaxHeight;
    private int mMinHeight;
    private VelocityTracker mVelocityTracker;
    private Adapter mAdapter;
    private OverScroller mScroller;
    private Handler mScrollHandler;
    private float mCardRatio;
    private List<View> mInflatedViews = new ArrayList<View>();
    private Map<Integer, View> mChildView = new HashMap<Integer, View>();
    private List<View> mRecycleBin = new ArrayList<View>();
    private List<Integer> mDeleteList = new ArrayList<Integer>();
    private float mTouchDownY;
    private int mContentHeight;
    private int mMaxOffset;
    private com.zappoint.android.elv.ListMovementListener mListMovementListener;
    private int mTravelDistance;

    public ExpandListView(Context context) {
        super(context);
        init(context, null, 0);
    }

    public ExpandListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public ExpandListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs, defStyle);
    }

    private void init(Context context, AttributeSet attrs, int defStyle) {
        mMinHeight = DEFAULT_MIN_HEIGHT;
        mCardRatio = DEFAULT_CARD_RATIO;
        if (attrs != null) {
            TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ExpandListView, defStyle, 0);
            mMinHeight = attributes.getDimensionPixelSize(R.styleable.ExpandListView_clvCardMinimunHeight, DEFAULT_MIN_HEIGHT);
            mCardRatio = attributes.getFloat(R.styleable.ExpandListView_clvCardDimensionRatio, DEFAULT_CARD_RATIO);
            attributes.recycle();
        }
    }

    @Override
    public View getSelectedView() {
        return null;
    }

    @Override
    public void setSelection(int position) {

    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        if (mAdapter == null) {
            mInflatedViews.clear();
            for (int i = 0; i < getChildCount(); i++) {
                mInflatedViews.add(getChildAt(i));
            }
        }
        removeAllViewsInLayout();
        mChildView.clear();
        mRecycleBin.clear();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        calculateDimensions(h);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(0, widthMeasureSpec);
        int height = resolveSize(0, heightMeasureSpec);
        mMaxHeight = (int) (width / mCardRatio);
        calculateDimensions(getMeasuredHeight());
        int y = clamp(getScrollY(), 0, mMaxOffset);
        int startIndex = y / mMinHeight;
        double tempHeight = (y + height) - (mMinHeight + mMaxHeight);
        int addedIndex = (int) Math.ceil(tempHeight / mMinHeight);
        addedIndex = addedIndex < 0 ? 0 : addedIndex;
        int endIndex = startIndex + addedIndex + 1;
        if (startIndex < 0) {
            startIndex = 0;
        }
        if (endIndex >= mAdapter.getCount()) {
            endIndex = mAdapter.getCount() - 1;
        }
        measureViewBetween(startIndex, endIndex);

        setMeasuredDimension(width, height);
    }

    private void measureViewBetween(int start, int end) {
        int y = getScrollY();
        int w = getWidth();
        int previousY = start * mMinHeight;
        float ratio;
        int h;
        for (int index = start; index <= end; index++) {
            ratio = 0;
            h = mMinHeight;
            if (index == start) {
                ratio = (y - previousY) / (float) mMinHeight;
                ratio = clampf(ratio, 0, 1);
                h = (int) (mMaxHeight * (1 - ratio) + mMinHeight * ratio);
            } else if (index == start + 1) {
                h = start * mMinHeight + mMaxHeight + mMinHeight - previousY;
            }
            View view = mChildView.get(index);
            if (view != null) {
                view.measure(w, h);
            }
            previousY = previousY + h;
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mAdapter == null) {
            return;
        }
        int y = clamp(getScrollY(), 0, mMaxOffset);
        int height = getHeight();
        int startIndex = y / mMinHeight;
        double tempHeight = (y + height) - (mMinHeight + mMaxHeight);
        int addedIndex = (int) Math.ceil(tempHeight / mMinHeight);
        addedIndex = addedIndex < 0 ? 0 : addedIndex;
        int endIndex = startIndex + addedIndex + 1;
        if (startIndex < 0) {
            startIndex = 0;
        }
        if (endIndex >= mAdapter.getCount()) {
            endIndex = mAdapter.getCount() - 1;
        }
        removeViewBelow(endIndex);
        removeViewAbove(startIndex);
        arrangeViewBetween(startIndex, endIndex);
    }

    private void removeViewAbove(int index) {
        for (Integer key : mChildView.keySet()) {
            if (key < index) {
                View child = mChildView.get(key);
                removeViewInLayout(child);
                mDeleteList.add(key);
                mRecycleBin.add(child);
            }
        }
        for (Integer deadIndex : mDeleteList) {
            mChildView.remove(deadIndex);
        }
        mDeleteList.clear();
    }

    private void removeViewBelow(int index) {
        for (Integer key : mChildView.keySet()) {
            if (key > index) {
                View child = mChildView.get(key);
                removeViewInLayout(child);
                mDeleteList.add(key);
                mRecycleBin.add(child);
            }
        }
        for (Integer deadIndex : mDeleteList) {
            mChildView.remove(deadIndex);
        }
        mDeleteList.clear();
    }

    private View getRecycledViewAt(int index) {
        if (mRecycleBin.size() > 0) {
            return mRecycleBin.remove(0);
        }
        return null;
    }

    private void arrangeViewBetween(int start, int end) {
        int y = getScrollY();
        int w = getWidth();
        int previousY = start * mMinHeight;
        float ratio;
        int h;
        for (int index = start; index <= end; index++) {
            ratio = 0;
            h = mMinHeight;
            View view = mChildView.get(index);
            if (view == null) {
                view = getRecycledViewAt(index);
                view = mAdapter.getView(index, view, this);
                if (index == start) {
                    ratio = (y - previousY) / (float) mMinHeight;
                    ratio = clampf(ratio, 0, 1);
                    h = (int) (mMaxHeight * (1 - ratio) + mMinHeight * ratio);
                    ratio = 1 - ratio;
                } else if (index == start + 1) {
                    h = start * mMinHeight + mMaxHeight + mMinHeight - previousY;
                    ratio = clampf((h - mMinHeight) / (float) (mMaxHeight - mMinHeight), 0, 1);
                }
                addViewInLayout(view, -1, new RelativeLayout.LayoutParams(w, h), false);
                view.layout(0, previousY, w, previousY + h);
                previousY = previousY + h;
                mChildView.put(index, view);
            } else {
                if (index == start) {
                    ratio = (y - previousY) / (float) mMinHeight;
                    ratio = clampf(ratio, 0, 1);
                    h = (int) (mMaxHeight * (1 - ratio) + mMinHeight * ratio);
                    ratio = 1 - ratio;
                } else if (index == start + 1) {
                    h = start * mMinHeight + mMaxHeight + mMinHeight - previousY;
                    ratio = clampf((h - mMinHeight) / (float) (mMaxHeight - mMinHeight), 0, 1);
                }
                view.layout(0, previousY, w, previousY + h);
                previousY = previousY + h;
            }
            if (mListMovementListener != null) {
                mListMovementListener.onItemRatioChanged(view, ratio);
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        mScroller = new OverScroller(ExpandListView.this.getContext(), new DecelerateInterpolator());
        mScroller.setFriction(0.1f);
        mScrollHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what != 0 && mScroller != null) {
                    mScroller.computeScrollOffset();
                    int y = mScroller.getCurrY();
                    setScrollY(y);
                    requestLayout();
                    if (!mScroller.isFinished()) {
                        sendEmptyMessageDelayed(msg.what, MESSAGE_DELAY);
                    }
                }
            }
        };
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mScroller.forceFinished(true);
        mScrollHandler.sendEmptyMessage(0);
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
        mScroller = null;
        mScrollHandler = null;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (mListMovementListener != null) {
            mListMovementListener.onItemIndexChanged(clamp(t, 0, mMaxOffset) / mMinHeight);
            if (mMaxOffset <= (t + LIST_END_EPSILON)) {
                mListMovementListener.onEndOfListReached();
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        boolean result = super.onTouchEvent(ev);
        int distance = mTravelDistance;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mScroller.forceFinished(true);
                mScrollHandler.sendEmptyMessage(0);
                mTouchDownY = ev.getRawY();
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                }
                mVelocityTracker.addMovement(ev);
                mTravelDistance = 0;
                return true;
            case MotionEvent.ACTION_UP:
                mTravelDistance = 0;
                if (determineClick(ev, distance)) {
                    return true;
                }
                if (mVelocityTracker != null) {
                    mVelocityTracker.addMovement(ev);
                    mVelocityTracker.computeCurrentVelocity(1500);
                    float velocity = mVelocityTracker.getYVelocity();
                    int targetIndex = 0;
                    mScroller.forceFinished(true);
                    mScroller.fling(0, getScrollY(), 0, -(int) velocity, 0, 0, 0, mMaxOffset);
                    int y = mScroller.getFinalY();
                    mScroller.forceFinished(true);
                    if (Math.abs(velocity) < 1500) {
                        targetIndex = Math.round(getScrollY() / (float) mMinHeight);
                        targetIndex = clamp(targetIndex, 0, mAdapter.getCount() - 1);
                        int targetY = targetIndex * mMinHeight;
                        int dy = targetY - getScrollY();
                        mScroller.startScroll(0, getScrollY(), 0, dy, Math.abs((int) (dy / (float) mMinHeight * 600)));
                    } else {
                        if (velocity < 0) {
                            targetIndex = (int) Math.floor(y / (float) mMinHeight);
                            targetIndex = clamp(targetIndex, 0, mAdapter.getCount());
                            mScroller.fling(0, getScrollY(), 0, -(int) velocity, 0, 0, 0, targetIndex * mMinHeight);
                        } else {
                            targetIndex = (int) Math.ceil(y / (float) mMinHeight);
                            targetIndex = clamp(targetIndex, 0, mAdapter.getCount());
                            mScroller.fling(0, getScrollY(), 0, -(int) velocity, 0, 0, targetIndex * mMinHeight, mMaxOffset);
                        }
                    }
                    mScrollHandler.sendEmptyMessage(getScrollY());
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                mScroller.forceFinished(true);
                mScrollHandler.sendEmptyMessage(0);
                if (mVelocityTracker == null) {
                    mVelocityTracker = VelocityTracker.obtain();
                }
                mVelocityTracker.addMovement(ev);
                float y = ev.getRawY();
                float diff = y - mTouchDownY;
                int scrollY = getScrollY() - (int) diff;
                if (mListMovementListener != null) {
                    if (diff > 0) {
                        mListMovementListener.onScrollUp();
                    } else if (diff < 0) {
                        mListMovementListener.onScrollDown();
                    }
                }
                mTravelDistance += Math.abs(diff);
                setScrollY(scrollY);
                mTouchDownY = y;
                requestLayout();
                return true;
            case MotionEvent.ACTION_CANCEL:
                mTravelDistance = 0;
                determineClick(ev, distance);
                return true;
            default:
                break;
        }
        return result;
    }

    private boolean determineClick(MotionEvent ev, int distance) {
        if (mVelocityTracker != null) {
            mVelocityTracker.computeCurrentVelocity(1500);
            float velocityy = mVelocityTracker.getYVelocity();
            float velocityx = mVelocityTracker.getXVelocity();
            if (distance <= 10 && Math.abs(velocityy) <= MIN_VELOCITY && Math.abs(velocityx) <= MIN_VELOCITY) {
                int base = getScrollY() / mMinHeight;
                int pos = 0;
                if ((int) ev.getY() > mMaxHeight) {
                    pos = (((int) ev.getY() - mMaxHeight) / mMinHeight) + 1;
                }
                int index = clamp(base + pos, 0, mAdapter.getCount() - 1);
                performItemClick(this, index, 0);
                return true;
            }
        }
        return false;
    }

    public void setListMovementLister(com.zappoint.android.elv.ListMovementListener listener) {
        mListMovementListener = listener;
    }

    public Adapter getAdapter() {
        return mAdapter;
    }

    public void setAdapter(Adapter adapter) {
        mAdapter = adapter;
        calculateDimensions(getHeight());
        requestLayout();
    }

    private void calculateDimensions(int h) {
        int contentHeight = ((mAdapter == null) ? 0 : mAdapter.getCount()) * mMinHeight;
        mContentHeight = (contentHeight > 0) ? (contentHeight - mMinHeight + h) : h;
        mMaxOffset = mContentHeight - h;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }

    private float clampf(float value, float min, float max) {
        return Math.min(Math.max(value, min), max);
    }
}
