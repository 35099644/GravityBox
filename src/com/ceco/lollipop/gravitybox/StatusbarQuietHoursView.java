/*
 * Copyright (C) 2015 Peter Gregus for GravityBox Project (C3C076@xda)
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
package com.ceco.lollipop.gravitybox;

import com.ceco.lollipop.gravitybox.ModStatusBar.ContainerType;
import com.ceco.lollipop.gravitybox.R;
import com.ceco.lollipop.gravitybox.ledcontrol.QuietHours;
import com.ceco.lollipop.gravitybox.managers.StatusBarIconManager;
import com.ceco.lollipop.gravitybox.managers.SysUiManagers;
import com.ceco.lollipop.gravitybox.managers.StatusBarIconManager.ColorInfo;
import com.ceco.lollipop.gravitybox.managers.StatusBarIconManager.IconManagerListener;
import com.ceco.lollipop.gravitybox.managers.StatusbarQuietHoursManager.QuietHoursListener;

import de.robv.android.xposed.XposedBridge;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarQuietHoursView extends ImageView implements  IconManagerListener, QuietHoursListener {

    private ViewGroup mContainer;
    private QuietHours mQuietHours;

    public StatusbarQuietHoursView(ContainerType containerType, ViewGroup container, Context context) {
        super(context);

        mContainer = container;
        Resources res = context.getResources();
        int iconSizeResId = res.getIdentifier("status_bar_icon_size", "dimen", "android");
        int iconSize = iconSizeResId != 0 ? res.getDimensionPixelSize(iconSizeResId) :
            (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24, res.getDisplayMetrics());

        LinearLayout.LayoutParams lParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        setLayoutParams(lParams);
        setScaleType(ImageView.ScaleType.CENTER);
        ViewGroup systemIcons = (ViewGroup) mContainer.findViewById(
                context.getResources().getIdentifier("system_icons", "id", ModStatusBar.PACKAGE_NAME));
        systemIcons.addView(this, 0);

        mQuietHours = SysUiManagers.QuietHoursManager.getQuietHours();

        Context gbContext = SysUiManagers.GbContext;
        setImageDrawable(gbContext.getResources().getDrawable(R.drawable.stat_sys_quiet_hours));

        updateVisibility();
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
        if ((flags & StatusBarIconManager.FLAG_ICON_COLOR_CHANGED) != 0) {
            if (colorInfo.coloringEnabled) {
                setColorFilter(colorInfo.iconColor[0], PorterDuff.Mode.SRC_IN);
            } else {
                clearColorFilter();
            }
        }
        if ((flags & StatusBarIconManager.FLAG_ICON_ALPHA_CHANGED) != 0) {
            setAlpha(colorInfo.alphaSignalCluster);
        }
    }

    private void updateVisibility() {
        if (mQuietHours != null) {
            setVisibility(mQuietHours.showStatusbarIcon && mQuietHours.quietHoursActive() ?
                    View.VISIBLE : View.GONE);
        } else {
            setVisibility(View.GONE);
        }
    }
}
