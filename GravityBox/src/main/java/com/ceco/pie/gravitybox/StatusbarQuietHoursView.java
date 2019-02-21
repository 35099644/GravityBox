/*
 * Copyright (C) 2019 Peter Gregus for GravityBox Project (C3C076@xda)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ceco.pie.gravitybox;

import com.ceco.pie.gravitybox.ModStatusBar.ContainerType;
import com.ceco.pie.gravitybox.ledcontrol.QuietHours;
import com.ceco.pie.gravitybox.managers.StatusBarIconManager;
import com.ceco.pie.gravitybox.managers.SysUiManagers;
import com.ceco.pie.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.pie.gravitybox.managers.StatusBarIconManager.IconManagerListener;
import com.ceco.pie.gravitybox.managers.StatusbarQuietHoursManager.QuietHoursListener;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

@SuppressLint({"AppCompatCustomView", "ViewConstructor"})
public class StatusbarQuietHoursView extends ImageView implements  IconManagerListener, QuietHoursListener {

    private ViewGroup mContainer;
    private QuietHours mQuietHours;
    private Drawable mDrawable;
    private Drawable mDrawableWear;
    private int mCurrentDrawableId = -1; // -1=unset; 0=default; 1=wear
    private int mIconHeightPx;
    private ViewGroup mSystemIcons;

    public StatusbarQuietHoursView(ContainerType containerType, ViewGroup container, Context context) {
        super(context);

        mContainer = container;
        Resources res = context.getResources();
        mIconHeightPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, res.getDisplayMetrics());

        mSystemIcons = mContainer.findViewById(
                context.getResources().getIdentifier("system_icons", "id", ModStatusBar.PACKAGE_NAME));

        int position = 0;
        int marginEnd = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, res.getDisplayMetrics());
        int statusIconsResId = context.getResources().getIdentifier(
                "statusIcons", "id", ModStatusBar.PACKAGE_NAME);
        if (statusIconsResId != 0) {
            View statusIcons = mSystemIcons.findViewById(statusIconsResId);
            if (statusIcons != null) {
                position = mSystemIcons.indexOfChild(statusIcons) + 1;
                marginEnd = statusIcons.getPaddingEnd();
            }
        }
        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(mIconHeightPx, mIconHeightPx);
        lParams.setMarginEnd(marginEnd);
        setLayoutParams(lParams);

        mSystemIcons.addView(this, position);

        mQuietHours = SysUiManagers.QuietHoursManager.getQuietHours();

        updateVisibility();
    }

    public void destroy() {
        mSystemIcons.removeView(this);
        mSystemIcons = null;
        mQuietHours = null;
        mDrawable = null;
        mDrawableWear = null;
        mContainer = null;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.registerListener(this);
        }
        SysUiManagers.QuietHoursManager.registerListener(this);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (SysUiManagers.IconManager != null) {
            SysUiManagers.IconManager.unregisterListener(this);
        }
        SysUiManagers.QuietHoursManager.unregisterListener(this);
    }

    @Override
    public void onQuietHoursChanged() {
        mQuietHours = SysUiManagers.QuietHoursManager.getQuietHours();
        updateVisibility();
    }

    @Override
    public void onTimeTick() {
        updateVisibility();
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        if ((flags & StatusBarIconManager.FLAG_ICON_TINT_CHANGED) != 0) {
            setImageTintList(ColorStateList.valueOf(colorInfo.iconTint));
        }
        if ((flags & StatusBarIconManager.FLAG_ICON_ALPHA_CHANGED) != 0) {
            setAlpha(colorInfo.alphaSignalCluster);
        }
    }

    private Drawable getDefaultDrawable() {
        if (mDrawable == null) {
            try {
                mDrawable = Utils.getGbContext(getContext())
                    .getDrawable(R.drawable.stat_sys_quiet_hours);
            } catch (Throwable e) { /* ignore */ }
        }
        return mDrawable;
    }

    private Drawable getWearDrawable() {
        if (mDrawableWear == null) {
            try {
                mDrawableWear = Utils.getGbContext(getContext())
                    .getDrawable(R.drawable.stat_sys_quiet_hours_wear);
            } catch (Throwable e) { /* ignore */ }
        }
        return mDrawableWear;
    }

    private void setDrawableByMode() {
        final int oldDrawableId = mCurrentDrawableId;
        if (mQuietHours.mode == QuietHours.Mode.WEAR) {
            if (mCurrentDrawableId != 1) {
                setImageDrawable(getWearDrawable());
                mCurrentDrawableId = 1;
            }
        } else if (mCurrentDrawableId != 0) {
            setImageDrawable(getDefaultDrawable());
            mCurrentDrawableId = 0;
        }
        if (oldDrawableId != mCurrentDrawableId) {
            updateLayout();
        }
    }

    private void updateLayout() {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) getLayoutParams();
        lp.width = mCurrentDrawableId == 1 ?
                Math.round((float)mIconHeightPx * 0.85f) : mIconHeightPx;
        setLayoutParams(lp);
    }

    private void updateVisibility() {
        if (mQuietHours != null) {
            setDrawableByMode();
            setVisibility(mQuietHours.showStatusbarIcon && mQuietHours.quietHoursActive() ?
                    View.VISIBLE : View.GONE);
        } else {
            setVisibility(View.GONE);
        }
    }
}
