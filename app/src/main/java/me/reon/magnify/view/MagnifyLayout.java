package me.reon.magnify.view;


import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Vibrator;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import me.reon.magnify.R;

/**
 * Created by Reon on 15/5/17.
 * 使用方法：
 * 1.使用该layout，注意带上资源magnify_bg，这是一个放大镜背景框
 * 2.将需要能被选择单词的TextView通过addListenerForChildView注册进来
 * 3.可以体验效果了。应该传入callback，被用于长按结束的回调。
 * TODO 1.长按的震动效果
 * TODO 2.一些细节参数的调整，使得取词以及显示更为自然
 */
public class MagnifyLayout extends RelativeLayout {

    private static final String TAG = "MagnifyLayout";

    /**
     * 状态
     * STATUS_DEFAULT：初始状态
     * STATUS_MAGNIFY：处于放大状态
     */
    private int mStatus;
    private static final int STATUS_DEFAULT = 0;
    private static final int STATUS_MAGNIFY = 1;


    /**
     * draw相关
     */
    private ArrayList<TextView> mChildViews;
    private TextView mChildView;
    private String mText;
    private SpannableString mSpText;
    private String mWord;

    //放大镜区域的大小,单位dp
    private static int MAGNIFY_WIDTH = 200;   //放大镜的宽度
    private static int MAGNIFY_HEIGHT = 60;   //放大镜的高度
    private static int MAGNIFY_MARGIN_TOP = 30; //手指距离放大镜下边缘的margin距离
    private static int MAGNIFY_TOP_OFFSET = 10;  //裁减的放大区域的offset，需要往下调一点

    private static final float SCALE_PARAM = 1.2f; //放大镜放大倍数
    private static int CAPTURE_WIDTH; //放大后可以塞入放大镜的截屏的宽度
    private static int CAPTURE_HEIGHT;//放大后可以塞入放大镜的截屏的高度

    private ImageView mMagnifyView; //放大镜
    private Bitmap mScreenShoot; //TODO 截图 应该是根据区域截，截全屏幕太恶心了
    private Matrix mScaleMatrix; //放大截图bitmap的矩阵

    public void addListenerForChildView(TextView childView) {
        mChildViews.add(childView);
    }

    /**
     * @param x 点击事件x
     * @param y 点击事件y
     * @return 事件位置的TextView
     */
    private int[] mLocation = new int[2];

    private TextView findTheView(float x, float y) {
        int childCount = mChildViews.size();
        TextView child;
        Rect childRect = new Rect();
        for (int i = 0; i < childCount; i++) {
            child = mChildViews.get(i);
            child.getGlobalVisibleRect(childRect);
            child.getLocationOnScreen(mLocation);
            if (mLocation[0] <= x && mLocation[1] <= y
                    && (child.getWidth() + mLocation[0]) >= x
                    && (child.getHeight() + mLocation[1]) >= y) {
                return child;
            }
        }
        return null;
    }

    public MagnifyLayout(Context context) {
        this(context, null);
    }

    public MagnifyLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MagnifyLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setClickable(true);

        mStatus = STATUS_DEFAULT;

        mChildViews = new ArrayList<>();

        mMagnifyView = new ImageView(this.getContext());
        mMagnifyView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mMagnifyView.setBackgroundResource(R.drawable.magnify_bg);
        mMagnifyView.setVisibility(View.INVISIBLE);
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            ((FrameLayout) activity.getWindow().getDecorView()).addView(mMagnifyView);
        }
        mScaleMatrix = new Matrix();
        mScaleMatrix.postScale(SCALE_PARAM, SCALE_PARAM);
        MAGNIFY_WIDTH = dpToPx(MAGNIFY_WIDTH);
        MAGNIFY_HEIGHT = dpToPx(MAGNIFY_HEIGHT);
        MAGNIFY_MARGIN_TOP = dpToPx(MAGNIFY_MARGIN_TOP);
        MAGNIFY_TOP_OFFSET = dpToPx(MAGNIFY_TOP_OFFSET);
        CAPTURE_WIDTH = (int) (MAGNIFY_WIDTH / SCALE_PARAM);
        CAPTURE_HEIGHT = (int) (MAGNIFY_HEIGHT / SCALE_PARAM);

        mVibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);

        setDrawingCacheEnabled(true);
        buildDrawingCache();
    }

    /**
     * 长按事件判定有关
     */
    private float mOldX; //DOWN事件X位置
    private float mOldY; //DOWN事件Y位置
    private static final float SLOP = 20; //TODO 要根据屏幕密度来算 长按误差范围

    private boolean mHasPerformedLongPress;
    private CheckLongPress mCheckLongPress = new CheckLongPress();
    private Vibrator mVibrator;

    private class CheckLongPress implements Runnable {

        @Override
        public void run() {
            performLongClickReon();
        }
    }

    /**
     * 判断MOVE事件的位置是否在DOWN事件位置上（有误差允许的情况下）
     */
    private boolean isPointInPos(float x, float y) {
        return Math.abs(mOldX - x) < SLOP && Math.abs(mOldY - y) < SLOP;
    }

    /**
     * 触发长按事件
     */
    private void performLongClickReon() {
        Log.i(TAG, "dz[onLongClick]");
        vibrate();
        mStatus = STATUS_MAGNIFY;
        magnifyMoveLogic(mOldX, mOldY);
        mMagnifyView.setVisibility(View.VISIBLE);
        refresh(mOldX, mOldY);
    }

    /**
     * @param event
     * @return 如果在放大镜的状态，拦截所有事件
     */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        Log.d(TAG, "onInterceptTouchEvent");
        if (mStatus == STATUS_MAGNIFY) { //如果在放大状态，拦截事件
            return true;
        }
        return super.onInterceptTouchEvent(event);
    }

    /**
     * @param event
     * @return 主要在这里去判定长按
     * 开启处理move的逻辑
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Log.d(TAG, "dispatchTouchEvent");
        //init data
        int action = event.getAction();
        final float x = event.getX();
        final float y = event.getY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                //开始long click的监听
                mOldX = x;
                mOldY = y;
                mHasPerformedLongPress = false;

                postDelayed(mCheckLongPress, ViewConfiguration.getLongPressTimeout());
                break;
            case MotionEvent.ACTION_UP:
                //start logic
                if (mStatus == STATUS_MAGNIFY) {
//                    Log.i(TAG, "Start the magnify down up logic");
                    mStatus = STATUS_DEFAULT;
                    magnifyUpLogic();
                    refresh(x, y);
                }
                if (!mHasPerformedLongPress) {
                    removeCallbacks(mCheckLongPress);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                //record status
                if (mStatus == STATUS_MAGNIFY) {
//                    Log.i(TAG, "Start the magnify down move logic");
                    magnifyMoveLogic(x, y);
                    refresh(x, y);
                }
                if (!mHasPerformedLongPress && !isPointInPos(x, y)) {
                    removeCallbacks(mCheckLongPress);
                }
                break;

        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(TAG, "onTouchEvent");
        return super.onTouchEvent(event);
    }

    /**
     * 注意！！！
     * 废掉该方法,不允许设置长按事件
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        //can not set.
    }

    /**
     * 注意！！！
     * 废掉该方法,不允许设置点击事件
     */
    @Override
    public void setOnClickListener(OnClickListener l) {

    }

    /**
     * @param x
     * @param y 放大镜视图的刷新
     *          1.set position
     *          2.clip bitmap
     *          3.draw bitmap if necessary
     *          4.set the bitmap
     */
    private void refresh(float x, float y) {
        switch (mStatus) {
            case STATUS_DEFAULT:
                break;
            case STATUS_MAGNIFY:
                mScreenShoot = getDrawingCache();
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mMagnifyView.getLayoutParams();
                lp.setMargins((int) x - MAGNIFY_WIDTH / 2, (int) y - MAGNIFY_HEIGHT - MAGNIFY_MARGIN_TOP, 0, 0);
                mMagnifyView.setLayoutParams(lp);
                int bitmapX, bitmapY, bitmapW, bitmapH;
                bitmapX = (int) (x - CAPTURE_WIDTH / 2);
                bitmapY = (int) (y - (CAPTURE_HEIGHT - MAGNIFY_TOP_OFFSET));
                bitmapW = CAPTURE_WIDTH;
                bitmapH = CAPTURE_HEIGHT;

                //小图在大图的位置
                int smallX = 0, smallY = 0;

                if (bitmapX < 0) {
                    bitmapX = 0;
                    bitmapW = (int) (CAPTURE_WIDTH / 2 + x);
                    smallX = (int) (CAPTURE_WIDTH / 2 - x);
                } else if (bitmapX + bitmapW > mScreenShoot.getWidth()) {
                    bitmapW = mScreenShoot.getWidth() - bitmapX;
                }

                if (bitmapY < 0) {
                    bitmapY = 0;
                    bitmapH = (int) ((CAPTURE_HEIGHT - MAGNIFY_TOP_OFFSET) + y);
                    smallY = (int) ((CAPTURE_HEIGHT - MAGNIFY_TOP_OFFSET) - y);
                } else if (bitmapY + bitmapH > mScreenShoot.getHeight()) {
                    bitmapY = mScreenShoot.getHeight() - bitmapY;
                }

                if ((smallX & smallY) > 0) {
                    Bitmap smallBitmap = Bitmap.createBitmap(mScreenShoot, bitmapX, bitmapY, bitmapW, bitmapH, mScaleMatrix, false);
                    Bitmap bitmap = Bitmap.createBitmap(CAPTURE_WIDTH, CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    canvas.drawColor(Color.WHITE);
                    canvas.drawBitmap(smallBitmap, smallX, smallY, null);
                    mMagnifyView.setImageBitmap(bitmap);

                } else {
                    Bitmap bitmap = Bitmap.createBitmap(mScreenShoot, bitmapX, bitmapY, bitmapW, bitmapH, mScaleMatrix, false);
                    mMagnifyView.setImageBitmap(bitmap);
                }
                break;
        }
    }


    /**
     * 长按结束（抬起手指）的逻辑处理
     */
    private void magnifyUpLogic() {
        mMagnifyView.setVisibility(View.INVISIBLE);
        if (mWord != null) {
            Toast.makeText(this.getContext(), mWord, Toast.LENGTH_LONG).show();
        }
        clearTheOldData();
    }


    /**
     * 清理选中的TextView，text等数据
     */
    private void clearTheOldData() {
        clearTheHighLight();
        mChildView = null;
        mText = null;
        mWord = null;
        mSpText = null;
    }

    /**
     * @param x
     * @param y 1.根据点击事件的XY，获得对应的子view
     *          2.如果是text view，获取到手指所在位置的英文 (最理想状态，使用textView的某个方法，直接select某个位置的单词)
     */
    private void magnifyMoveLogic(float x, float y) {
        TextView childView = findTheView(x, y);
        if (childView == null) {
            clearTheOldData();
            return;
        }
        if (childView != mChildView) {
            mChildView = childView;
            mText = mChildView.getText().toString();
            if (mText == null || mText.length() == 0) {
                clearTheOldData();
                return;
            }
            mSpText = new SpannableString(mText);
        }

        Layout layout = mChildView.getLayout();
        if (layout == null) {
            clearTheOldData();
            return;
        }

        int line = layout.getLineForVertical((int) y - (int) mChildView.getY());
        int off = layout.getOffsetForHorizontal(line, x - (int) mChildView.getX()); //TODO 实际上这里可以缓存
        //TODO 如果是最后一行，且点击的X在最后一个单词很末尾的地方，应该取消
//        if (line == layout.getLineCount()) {
//
//        }
        mWord = getTheSelectedWord(off);


    }

    /**
     * 根据getOffsetForHorizontal返回的off去寻找被选中的单词
     * TODO
     * 应该建立缓存，输入off，就得到start和end
     */
    private String getTheSelectedWord(int off) {
        int start = 0, end = mText.length();
        int i;
        for (i = off; i < mText.length() - 1; i++) {
            if (mText.charAt(i) == ' ') {
                end = i;
                break;
            }
        }

        for (i = off - 1; i >= 0; i--) {
            if (mText.charAt(i) == ' ') {
                start = i;
                break;
            }
        }

        if (start != 0) {
            start++;
        }
        highLightTheWord(start, end);
        return mText.substring(start, end);
    }

    /**
     * @param start
     * @param end   high light 被选中的单词
     */
    private void highLightTheWord(int start, int end) {
        BackgroundColorSpan[] spans = mSpText.getSpans(0, mText.length(), BackgroundColorSpan.class);
        for (int i = 0; i < spans.length; i++) {
            mSpText.removeSpan(spans[i]);
        }
        mSpText.setSpan(new BackgroundColorSpan(Color.BLUE), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        mChildView.setText(mSpText);
    }

    /**
     * 取消单词的high light
     */
    private void clearTheHighLight() {
        if (mText == null || mText.length() == 0 || mSpText == null) {
            return;
        }
        BackgroundColorSpan[] spans = mSpText.getSpans(0, mText.length(), BackgroundColorSpan.class);
        for (int i = 0; i < spans.length; i++) {
            mSpText.removeSpan(spans[i]);
        }
        mChildView.setText(mSpText);
    }

    //dp to px tool
    private int dpToPx(final int dp) {
        Resources r = getResources();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
    }


    private static final int VIBRATE_TIME = 200;

    /**
     * vibration
     */
    private void vibrate() {
        mVibrator.vibrate(VIBRATE_TIME);
    }
}
