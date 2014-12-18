
package com.cooeeui.brand.zenlauncher.scenes;

import java.util.ArrayList;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.cooeeui.brand.zenlauncher.Launcher;
import com.cooeeui.brand.zenlauncher.LauncherAppState;
import com.cooeeui.brand.zenlauncher.R;
import com.cooeeui.brand.zenlauncher.apps.ShortcutInfo;
import com.cooeeui.brand.zenlauncher.config.IconConfig;
import com.cooeeui.brand.zenlauncher.scenes.ui.BubbleView;
import com.cooeeui.brand.zenlauncher.scenes.utils.BitmapUtils;
import com.cooeeui.brand.zenlauncher.scenes.utils.DragController;
import com.cooeeui.brand.zenlauncher.scenes.utils.DragSource;
import com.cooeeui.brand.zenlauncher.scenes.utils.DropTarget;

public class Workspace extends FrameLayout implements DragSource, View.OnTouchListener {

    private Launcher mLauncher;
    private DragController mDragController;

    private static final int WORKSPACE_STATE_NORMAL = 0;
    private static final int WORKSPACE_STATE_DRAG = 1;
    private int mState = WORKSPACE_STATE_NORMAL;

    private int mIconSize;
    private float mPadding;
    private float[] mMidPoint = new float[2];

    private static final int BUBBLE_VIEW_CAPACITY = 9;
    private ArrayList<BubbleView> mBubbleViews = new ArrayList<BubbleView>(BUBBLE_VIEW_CAPACITY);

    public static final int EDIT_VIEW_ICON = 0;
    public static final int EDIT_VIEW_CHANGE = 1;
    public static final int EDIT_VIEW_DELETE = 2;
    private static final int EDIT_VIEW_CAPACITY = 3;
    private ArrayList<BubbleView> mEditViews = new ArrayList<BubbleView>(EDIT_VIEW_CAPACITY);

    private BubbleView mSelect;
    private ImageView mBottomView;

    private Bitmap mDefaultIcon;

    public Workspace(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public Workspace(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Workspace(Context context) {
        super(context);
    }

    public void setup(Launcher launcher, DragController controller) {
        mLauncher = launcher;
        mDragController = controller;

        mBottomView = (ImageView) findViewById(R.id.searchbar);
        init();

        BitmapUtils.setIconSize(mIconSize);
    }

    public void startBind() {
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            View v = getChildAt(i);
            if (v instanceof BubbleView) {
                mDragController.removeDropTarget((DropTarget) v);
                BubbleView view = (BubbleView) v;
                view.clearBitmap();
                removeView(view);
            }
        }
        mBubbleViews.clear();
    }

    public void finishBind() {
        update();
    }

    private void addBubbleView(ShortcutInfo info, Bitmap b) {
        BubbleView v = new BubbleView(mLauncher, b);
        v.setTag(info);
        addView(v);
        mBubbleViews.add(v);
        v.setOnClickListener(mLauncher);
        v.setOnLongClickListener(mLauncher);

        v.setOnTouchListener(this);

        mDragController.addDropTarget(v);
    }

    public void addBubbleViewFromBind(ShortcutInfo info) {
        Bitmap b = info.mIcon;
        if (b == null) {
            b = getDefaultIcon();
            info.mRecycle = false;
        }

        addBubbleView(info, b);
    }

    public void addBubbleView(ShortcutInfo info) {
        Bitmap b = info.mIcon;
        int position = mBubbleViews.size();

        if (b.getWidth() == mIconSize) {
            info.mRecycle = false;
        } else {
            b = BitmapUtils.resizeBitmap(b, mIconSize, false);
            info.mRecycle = true;
        }

        addBubbleView(info, b);
        mSelect = mBubbleViews.get(position);

        LauncherModel.addItemToDatabase(mLauncher, info, position);
    }

    private Bitmap getDefaultIcon() {
        if (mDefaultIcon == null) {
            mDefaultIcon = BitmapUtils.getIcon(Resources.getSystem(),
                    android.R.mipmap.sym_def_app_icon);
        }
        return mDefaultIcon;
    }

    public void changeIcon(int iconId) {
        ShortcutInfo i = (ShortcutInfo) mSelect.getTag();

        if (i.mIconId == iconId) { // same icon
            return;
        }
        if (i.mRecycle) {
            mSelect.clearBitmap();
        }

        Bitmap b = BitmapUtils.getIcon(mLauncher.getResources(), iconId);
        i.mRecycle = true;
        i.mIconId = iconId;
        if (b == null) {
            b = getDefaultIcon();
            i.mRecycle = false;
            i.mIconId = -1;
        }

        mSelect.changeBitmap(b);
    }

    public void changeBubbleView(ShortcutInfo info) {
        ShortcutInfo i = (ShortcutInfo) mSelect.getTag();
        if (i.mIcon == info.mIcon) { // same icon
            return;
        }
        if (i.mRecycle) {
            mSelect.clearBitmap();
        }
        i.intent = info.intent;
        i.mIcon = info.mIcon;
        i.mIconId = -1;

        Bitmap b = info.mIcon;
        if (b.getWidth() == mIconSize) {
            i.mRecycle = false;
        } else {
            b = BitmapUtils.resizeBitmap(b);
            i.mRecycle = true;
        }
        mSelect.changeBitmap(b);

    }

    public void removeBubbleView() {
        ShortcutInfo i = (ShortcutInfo) mSelect.getTag();
        int index = mBubbleViews.indexOf(mSelect);

        mBubbleViews.remove(mSelect);
        removeView(mSelect);
        mDragController.removeDropTarget(mSelect);
        update();

        if (i.mRecycle) {
            mSelect.clearBitmap();
        }

        if (mBubbleViews.size() <= 0) {
            stopDrag();
            return;
        }
        if (index == mBubbleViews.size()) {
            mSelect = mBubbleViews.get(index - 1);
        } else {
            mSelect = mBubbleViews.get(index);
        }
    }

    private void moveBubbleView(int position, float x, float y) {
        BubbleView v = mBubbleViews.get(position);
        if (v != null) {
            v.move(x, y);
        }
    }

    void init() {
        DisplayMetrics metrics = new DisplayMetrics();
        mLauncher.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        mIconSize = metrics.widthPixels * 96 / 720;
        IconConfig.setIconSize(mIconSize);
        mPadding = metrics.widthPixels * 36 / 720;
        mMidPoint[0] = metrics.widthPixels * 180 / 720;
        mMidPoint[1] = metrics.heightPixels - mIconSize * 4 - mPadding * 2;
    }

    public void update() {
        int count = mBubbleViews.size();

        float startX = mMidPoint[0];
        float startY = mMidPoint[1];
        if (count > 6) {
            startY += (mIconSize + mPadding);
        }
        float iconX, iconY;
        int rNum = count % 3;

        if (rNum == 1) {
            iconX = startX + mIconSize + mPadding;
            iconY = startY - (mIconSize + mPadding) * (count / 3);
            moveBubbleView(count - 1, iconX, iconY);
        } else if (rNum == 2) {
            iconX = startX + (mIconSize + mPadding) / 2;
            iconY = startY - (mIconSize + mPadding) * (count / 3);
            moveBubbleView(count - 2, iconX, iconY);
            moveBubbleView(count - 1, iconX + mIconSize + mPadding, iconY);
        }

        rNum = count - rNum;
        iconX = startX;
        iconY = startY;
        for (int i = 0; i < rNum; i++) {
            moveBubbleView(i, iconX, iconY);
            iconX += (mIconSize + mPadding);
            if ((i + 1) % 3 == 0) {
                iconX = startX;
                iconY -= (mIconSize + mPadding);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        measureChildren(widthMeasureSpec, heightMeasureSpec);

        setMeasuredDimension(widthSize, heightSize);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int action = event.getAction();
        if (mState == WORKSPACE_STATE_NORMAL) {
            return false;
        }

        if (action == MotionEvent.ACTION_DOWN && v instanceof BubbleView) {
            BubbleView bv = (BubbleView) v;
            startDrag(bv);
            return true;
        }
        return false;
    }

    private void showEditViews() {
        mBottomView.setVisibility(View.INVISIBLE);
        if (mEditViews.size() < EDIT_VIEW_CAPACITY) {
            mEditViews.clear();
            Bitmap icon = null;
            int[] iconId = {
                    R.drawable.icon1, R.drawable.icon2, R.drawable.icon3
            };
            for (int i = 0; i < EDIT_VIEW_CAPACITY; i++) {
                icon = BitmapUtils.getIcon(mLauncher.getResources(), iconId[i]);
                BubbleView v = new BubbleView(mLauncher, icon);
                v.setTag(i);
                addView(v);
                mEditViews.add(v);
                v.setOnClickListener(mLauncher);

                // TODO: use dynamic layout to fit the device.
                DisplayMetrics metrics = new DisplayMetrics();
                mLauncher.getWindowManager().getDefaultDisplay().getMetrics(metrics);
                float scale = metrics.widthPixels / 720.0f;
                v.move((90 + i * 220) * scale, 1080 * scale);
            }
            return;
        }
        for (int i = 0; i < EDIT_VIEW_CAPACITY; i++) {
            mEditViews.get(i).setVisibility(View.VISIBLE);
        }
    }

    private void hideEditViews() {
        for (int i = 0; i < EDIT_VIEW_CAPACITY; i++) {
            mEditViews.get(i).setVisibility(View.INVISIBLE);
        }
        mBottomView.setVisibility(View.VISIBLE);
    }

    public void startDrag(BubbleView view) {
        if (mState != WORKSPACE_STATE_DRAG) {
            mState = WORKSPACE_STATE_DRAG;
            showEditViews();
        }
        mSelect = view;
        removeView(view);
        mDragController.removeDropTarget(view);
        mDragController.startDrag(this, view);
    }

    public void stopDrag() {
        if (mState != WORKSPACE_STATE_NORMAL) {
            mState = WORKSPACE_STATE_NORMAL;
            hideEditViews();
        }
    }

    public boolean isFull() {
        return mBubbleViews.size() >= BUBBLE_VIEW_CAPACITY;
    }

    @Override
    public void onDropCompleted(BubbleView target) {
        if (target != null) {
            int tIndex = mBubbleViews.indexOf(target);
            int sIndex = mBubbleViews.indexOf(mSelect);
            mBubbleViews.set(tIndex, mSelect);
            mBubbleViews.set(sIndex, target);
        }
        mLauncher.getDragLayer().removeView(mSelect);
        mDragController.addDropTarget(mSelect);
        addView(mSelect);
        update();
    }
}
