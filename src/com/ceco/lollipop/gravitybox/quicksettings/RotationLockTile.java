package com.ceco.lollipop.gravitybox.quicksettings;

import de.robv.android.xposed.XSharedPreferences;
import android.view.View;

public class RotationLockTile extends AospTile {
    public static final String AOSP_KEY = "rotation";

    protected RotationLockTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, "aosp_tile_rotation", tile, prefs, eventDistributor);
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.RotationLockTile";
    }

    @Override
    public String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick(View view) {
        return false;
    }

}
