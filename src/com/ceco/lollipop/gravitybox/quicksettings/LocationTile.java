package com.ceco.lollipop.gravitybox.quicksettings;

import de.robv.android.xposed.XSharedPreferences;
import android.view.View;

public class LocationTile extends AospTile {
    public static final String AOSP_KEY = "location";

    protected LocationTile(Object host, Object tile, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        super(host, tile, prefs, eventDistributor);
    }

    @Override
    public String getKey() {
        return "aosp_tile_location";
    }

    @Override
    protected String getClassName() {
        return "com.android.systemui.qs.tiles.LocationTile";
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
