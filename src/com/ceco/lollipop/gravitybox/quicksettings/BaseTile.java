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

package com.ceco.lollipop.gravitybox.quicksettings;

import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.ceco.lollipop.gravitybox.GravityBox;
import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.ModQsTiles;
import com.ceco.lollipop.gravitybox.ModStatusBar.StatusBarState;
import com.ceco.lollipop.gravitybox.quicksettings.QsTileEventDistributor.QsEventListener;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class BaseTile implements QsEventListener {
    protected static String TAG = "GB:BaseTile";
    protected static final boolean DEBUG = ModQsTiles.DEBUG;

    public static final String TILE_KEY_NAME = "gbTileKey";
    public static final String CLASS_BASE_TILE = "com.android.systemui.qs.QSTile";
    public static final String CLASS_TILE_STATE = "com.android.systemui.qs.QSTile.State";
    public static final String CLASS_TILE_VIEW = "com.android.systemui.qs.QSTileView";

    protected static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    protected String mKey;
    protected Object mHost;
    protected Object mTile;
    protected QsTileEventDistributor mEventDistributor;
    protected XSharedPreferences mPrefs;
    protected Context mContext;
    protected Context mGbContext;
    protected boolean mEnabled;
    protected boolean mSecured;
    protected int mStatusBarState;
    protected boolean mNormalized;
    protected boolean mHideOnChange;
    protected int mNumColumns;
    protected float mScalingFactor = 1f;

    public BaseTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        mHost = host;
        mKey = key;
        mPrefs = prefs;
        mEventDistributor = eventDistributor;

        mContext = (Context) XposedHelpers.callMethod(mHost, "getContext");
        mGbContext = mContext.createPackageContext(GravityBox.PACKAGE_NAME,
                Context.CONTEXT_IGNORE_SECURITY);

        mEventDistributor.registerListener(this);
        initPreferences();
    }

    protected void initPreferences() {
        String enabledTiles = mPrefs.getString(TileOrderActivity.PREF_KEY_TILE_ORDER, null);
        mEnabled = enabledTiles != null && enabledTiles.contains(mKey);

        Set<String> securedTiles = mPrefs.getStringSet(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_SECURED_TILES,
                new HashSet<String>());
        mSecured = securedTiles.contains(mKey);

        mNormalized = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_NORMALIZE, false);
        mHideOnChange = mPrefs.getBoolean(GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_HIDE_ON_CHANGE, false);
    }

    @Override
    public void handleClick() {
        if (mHideOnChange && supportsHideOnChange()) {
            collapsePanels();
        }
    }

    public abstract boolean handleLongClick(View view);
    public abstract void handleUpdateState(Object state, Object arg);

    @Override
    public void setListening(boolean listening) { }

    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public Object getTile() {
        return mTile;
    }

    @Override
    public void handleDestroy() {
        mEventDistributor.unregisterListener(this);
        mEventDistributor = null;
        mTile = null;
        mHost = null;
        mPrefs = null;
        mContext = null;
        mGbContext = null;
    }

    @Override
    public void onCreateTileView(View tileView) throws Throwable {
        tileView.setLongClickable(true);
        tileView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return handleLongClick(v);
            }
        });
        XposedHelpers.setAdditionalInstanceField(tileView, TILE_KEY_NAME, mKey);

        mScalingFactor = QsPanel.getScalingFactor(Integer.valueOf(mPrefs.getString(
                GravityBoxSettings.PREF_KEY_QUICK_SETTINGS_TILES_PER_ROW, "0")));
        if (mScalingFactor != 1f) {
            int iconSizePx = XposedHelpers.getIntField(tileView, "mIconSizePx");
            XposedHelpers.setIntField(tileView, "mIconSizePx", Math.round(iconSizePx*mScalingFactor));
            int tileSpacingPx = XposedHelpers.getIntField(tileView, "mTileSpacingPx");
            XposedHelpers.setIntField(tileView, "mTileSpacingPx", Math.round(tileSpacingPx*mScalingFactor));
            int tilePaddingBelowIconPx = XposedHelpers.getIntField(tileView, "mTilePaddingBelowIconPx");
            XposedHelpers.setIntField(tileView, "mTilePaddingBelowIconPx",
                    Math.round(tilePaddingBelowIconPx*mScalingFactor));
            int dualTileVerticalPaddingPx = XposedHelpers.getIntField(tileView, "mDualTileVerticalPaddingPx");
            XposedHelpers.setIntField(tileView, "mDualTileVerticalPaddingPx", 
                    Math.round(dualTileVerticalPaddingPx*mScalingFactor));
    
            updateLabelLayout(tileView);
            updatePaddingTop(tileView);
        }
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) { }

    @Override
    public void onEnabledChanged(boolean enabled) {
        mEnabled = enabled;
        if (DEBUG) log(mKey + ": onEnabledChanged(" + enabled + ")");
    }

    @Override
    public void onSecuredChanged(boolean secured) {
        mSecured = secured;
        if (DEBUG) log(mKey + ": onSecuredChanged(" + secured + ")");
    }

    @Override
    public void onStatusBarStateChanged(int state) {
        final int oldState = mStatusBarState;
        mStatusBarState = state;
        if (mSecured && mStatusBarState != oldState &&
                mStatusBarState != StatusBarState.SHADE) {
            refreshState();
        }
        if (DEBUG) log(mKey + ": onStatusBarStateChanged(" + state + ")");
    }

    @Override
    public boolean supportsHideOnChange() {
        return true;
    }

    @Override
    public void onHideOnChangeChanged(boolean hideOnChange) {
        mHideOnChange = hideOnChange;
    }

    @Override
    public void onViewConfigurationChanged(View tileView, Configuration config) {
        if (mScalingFactor != 1f) {
            updateLabelLayout(tileView);
            updatePaddingTop(tileView);
            tileView.requestLayout();
        }
    }

    @Override
    public void onRecreateLabel(View tileView) {
        if (mScalingFactor != 1f) {
            updateLabelLayout(tileView);
            tileView.requestLayout();
        }
    }

    private void updatePaddingTop(View tileView) {
        int tilePaddingTopPx = XposedHelpers.getIntField(tileView, "mTilePaddingTopPx");
        XposedHelpers.setIntField(tileView, "mTilePaddingTopPx",
                Math.round(tilePaddingTopPx*mScalingFactor));
    }

    private void updateLabelLayout(View tileView) {
        TextView label = (TextView) XposedHelpers.getObjectField(tileView, "mLabel");
        if (label != null) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    label.getTextSize()*mScalingFactor);
        }
        Object dualLabel = XposedHelpers.getObjectField(tileView, "mDualLabel");
        if (dualLabel != null) {
            TextView first = (TextView) XposedHelpers.getObjectField(dualLabel, "mFirstLine");
            first.setTextSize(TypedValue.COMPLEX_UNIT_PX, first.getTextSize()*mScalingFactor);
            TextView second = (TextView) XposedHelpers.getObjectField(dualLabel, "mSecondLine");
            second.setTextSize(TypedValue.COMPLEX_UNIT_PX, second.getTextSize()*mScalingFactor);
        }
    }

    public void refreshState() {
        try {
            XposedHelpers.callMethod(mTile, "refreshState");
            if (DEBUG) log(mKey + ": refreshState called");
        } catch (Throwable t) {
            log("Error refreshing tile state: ");
            XposedBridge.log(t);
        }
    }

    public void startSettingsActivity(Intent intent) {
        try {
            XposedHelpers.callMethod(mHost, "startSettingsActivity", intent);
        } catch (Throwable t) {
            log("Error in startSettingsActivity: ");
            XposedBridge.log(t);
        }
    }

    public void startSettingsActivity(String action) {
        startSettingsActivity(new Intent(action));
    }

    public void collapsePanels() {
        try {
            XposedHelpers.callMethod(mHost, "collapsePanels");
        } catch (Throwable t) {
            log("Error in collapsePanels: ");
            XposedBridge.log(t);
        }
    }
}
