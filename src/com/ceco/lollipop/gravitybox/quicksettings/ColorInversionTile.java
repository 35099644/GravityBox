package com.ceco.lollipop.gravitybox.quicksettings;

import com.ceco.lollipop.gravitybox.GravityBox;

import android.os.Build;

import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;

public class ColorInversionTile extends AospTile {
    public static final String AOSP_KEY = "inversion";

    private Unhook mLongClickHook;

    protected ColorInversionTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_inversion", tile, prefs, eventDistributor);

        createHooks();
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick() {
        // noop
        return true;
    }

    @Override
    public void handleDestroy() {
        super.handleDestroy();
        destroyHooks();
    }

    private void createHooks() {
        try {
            if (Build.VERSION.SDK_INT >= 22) {
                mLongClickHook = XposedHelpers.findAndHookMethod(mTile.getClass().getName(), 
                        mContext.getClassLoader(), "handleLongClick", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (handleLongClick()) {
                            param.setResult(null);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            GravityBox.log(TAG, t);
        }
    }

    private void destroyHooks() {
        if (mLongClickHook != null) {
            mLongClickHook.unhook();
            mLongClickHook = null;
        }
    }
}
