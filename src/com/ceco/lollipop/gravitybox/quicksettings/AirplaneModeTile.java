package com.ceco.lollipop.gravitybox.quicksettings;

import android.view.View;
import de.robv.android.xposed.XSharedPreferences;

public class AirplaneModeTile extends AospTile {
    public static final String AOSP_KEY = "airplane";

    protected AirplaneModeTile(Object host, Object tile, XSharedPreferences prefs, 
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, tile, prefs, eventDistributor);
    }

    @Override
    public String getKey() {
        return "aosp_tile_airplane_mode";
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.AirplaneModeTile";
    }

    @Override
    protected String getAospKey() {
        return AOSP_KEY;
    }

    @Override
    public boolean handleLongClick(View view) {
        return false;
    }
}
