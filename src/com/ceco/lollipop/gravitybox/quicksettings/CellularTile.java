package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.ConnectivityServiceWrapper;
import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.R;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class CellularTile extends AospTile {
    public static final String AOSP_KEY = "cell";
    public static final String MSIM_KEY1 = "cell1";
    public static final String MSIM_KEY2 = "cell2";

    private String mAospKey;
    private Unhook mCreateTileViewHook;
    private ImageView mDataOffView;
    private TelephonyManager mTm;
    private boolean mDataTypeIconVisible;
    private boolean mIsSignalNull;
    private boolean mDataOffIconEnabled;

    protected CellularTile(Object host, String aospKey, String key, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, key, tile, prefs, eventDistributor);

        mAospKey = aospKey;

        if (isPrimary()) {
            mTm = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        }

        createHooks();
    }

    private boolean isPrimary() {
        return AOSP_KEY.equals(mAospKey) ||
                MSIM_KEY1.equals(mAospKey);
    }

    private boolean isDataTypeIconVisible(Object state) {
        try {
            return (XposedHelpers.getIntField(state, "overlayIconId") != 0);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isSignalNull(Object info) {
        try {
            // TODO: check Moto MSIM
            boolean noSim = XposedHelpers.getBooleanField(info, "noSim");
            boolean enabled = XposedHelpers.getBooleanField(info, "enabled");
            boolean airplane = XposedHelpers.getBooleanField(info, "airplaneModeEnabled");
            int iconId = XposedHelpers.getIntField(info, "mobileSignalIconId");
            return (noSim || !enabled || airplane || iconId <= 0);
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean isMobileDataEnabled() {
        try {
            return (Boolean) XposedHelpers.callMethod(mTm, "getDataEnabled");
        } catch (Throwable t) {
            return false;
        }
    }

    private boolean canShowDataOffIcon() {
        return (mDataOffIconEnabled && !mIsSignalNull && !mDataTypeIconVisible);
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();

        mDataOffIconEnabled = mPrefs.getBoolean(
                GravityBoxSettings.PREF_KEY_CELL_TILE_DATA_OFF_ICON, false);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        super.onBroadcastReceived(context, intent);

        if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_CELL_TILE_DATA_OFF_ICON)) {
                mDataOffIconEnabled = intent.getBooleanExtra(
                        GravityBoxSettings.EXTRA_CELL_TILE_DATA_OFF_ICON, false);
            }
        }
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.CellularTile";
    }

    @Override
    public String getAospKey() {
        return mAospKey;
    }

    @Override
    public void onCreateTileView(View tileView) throws Throwable {
        super.onCreateTileView(tileView);

        if (isPrimary()) {
            mDataOffView = new ImageView(mContext);
            mDataOffView.setImageDrawable(mGbContext.getDrawable(R.drawable.ic_mobile_data_off));
            mDataOffView.setVisibility(View.GONE);
            FrameLayout iconFrame = (FrameLayout) XposedHelpers.getObjectField(tileView, "mIconFrame");
            iconFrame.addView(mDataOffView, FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            if (mScalingFactor != 1f) {
                mDataOffView.setScaleX(mScalingFactor);
                mDataOffView.setScaleY(mScalingFactor);
            }
        }
    }

    @Override
    public void handleUpdateState(Object state, Object arg) {
        super.handleUpdateState(state, arg);

        if (isPrimary()) {
            mDataTypeIconVisible = isDataTypeIconVisible(state);
            mIsSignalNull = isSignalNull(arg);
            if ((Boolean) XposedHelpers.getBooleanField(state, "visible")) {
                final boolean shouldShow = canShowDataOffIcon() && !isMobileDataEnabled();
                mDataOffView.post(new Runnable() {
                    @Override
                    public void run() {
                        mDataOffView.setVisibility(shouldShow ?
                                View.VISIBLE : View.GONE);
                    }
                });
            }
        }
    }

    @Override
    public boolean handleLongClick(View view) {
        if (!isPrimary()) return false;

        view.setPressed(false);

        // change icon visibility immediately for fast feedback
        final boolean visible = mDataOffView.getVisibility() == View.VISIBLE;
        mDataOffView.setVisibility(!visible && canShowDataOffIcon() ? 
                View.VISIBLE : View.GONE);

        // toggle mobile data
        Intent intent = new Intent(ConnectivityServiceWrapper.ACTION_TOGGLE_MOBILE_DATA);
        mContext.sendBroadcast(intent);
        return true;
    }

    @Override
    public boolean supportsHideOnChange() {
        return false;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
        mTm = null;
        mDataOffView = null;
    }

    private void createHooks() {
        try {
            mCreateTileViewHook = XposedHelpers.findAndHookMethod(getClassName(), mContext.getClassLoader(),
                    "createTileView", Context.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mKey.equals(XposedHelpers.getAdditionalInstanceField(
                            param.thisObject, BaseTile.TILE_KEY_NAME))) {
                        onCreateTileView((View)param.getResult());
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private void destroyHooks() {
        if (mCreateTileViewHook != null) {
            mCreateTileViewHook.unhook();
            mCreateTileViewHook = null;
        }
    }
}
