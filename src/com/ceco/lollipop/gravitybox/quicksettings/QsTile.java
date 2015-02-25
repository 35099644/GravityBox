package com.ceco.lollipop.gravitybox.quicksettings;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;

import com.ceco.lollipop.gravitybox.GravityBox;
import com.ceco.lollipop.gravitybox.GravityBoxSettings;
import com.ceco.lollipop.gravitybox.ModQsTiles;
import com.ceco.lollipop.gravitybox.Utils;
import com.ceco.lollipop.gravitybox.managers.SysUiManagers;
import com.ceco.lollipop.gravitybox.quicksettings.QsTileEventDistributor.QsEventListenerGb;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public abstract class QsTile implements QsEventListenerGb {
    protected static String TAG = "GB:QsTile";

    protected static final boolean DEBUG = ModQsTiles.DEBUG;
    public static final String TILE_KEY_NAME = "gbTileKey";
    public static final String DUMMY_INTENT = "intent(dummy)";
    public static final String CLASS_INTENT_TILE = "com.android.systemui.qs.tiles.IntentTile";
    public static final String CLASS_BASE_TILE = "com.android.systemui.qs.QSTile";
    public static final String CLASS_TILE_STATE = "com.android.systemui.qs.QSTile.State";
    public static final String CLASS_TILE_VIEW = "com.android.systemui.qs.QSTileView";

    protected Object mHost;
    protected Object mTile;
    protected String mKey;
    protected State mState;
    protected Context mContext;
    protected Context mGbContext;
    protected XSharedPreferences mPrefs;
    protected QsTileEventDistributor mEventDistributor;
    protected boolean mSupportsHideOnChange = true;
    protected boolean mEnabled;

    protected static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static QsTile create(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {

        Context ctx = (Context) XposedHelpers.callMethod(host, "getContext");

        if (key.equals("gb_tile_gravitybox"))
            return new GravityBoxTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_network_mode") && !Utils.isWifiOnly(ctx))
            return new NetworkModeTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_expanded_desktop"))
            return new ExpandedDesktopTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_gps_alt") && Utils.hasGPS(ctx))
            return new GpsTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_gps_slimkat") && Utils.hasGPS(ctx))
            return new LocationTileSlimkat(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_lock_screen"))
            return new LockScreenTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_music"))
            return new MusicTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_nfc") && Utils.hasNfc(ctx))
            return new NfcTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_quickapp"))
            return new QuickAppTile(host, key, prefs, eventDistributor, 1);
        else if (key.equals("gb_tile_quickapp2"))
            return new QuickAppTile(host, key, prefs, eventDistributor, 2);
        else if (key.equals("gb_tile_quickrecord"))
            return new QuickRecordTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_quiet_hours") &&
                SysUiManagers.QuietHoursManager != null)
            return new QuietHoursTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_screenshot"))
            return new ScreenshotTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_sleep"))
            return new SleepTile(host, key, prefs, eventDistributor);
        else if (key.equals("gb_tile_smart_radio") && prefs.getBoolean(
                GravityBoxSettings.PREF_KEY_SMART_RADIO_ENABLE, false))
            return new SmartRadioTile(host, key, prefs, eventDistributor);

        return null;
    }

    protected QsTile(Object host, String key, XSharedPreferences prefs,
            QsTileEventDistributor eventDistributor) throws Throwable {
        mHost = host;
        mKey = key;
        mPrefs = prefs;
        mEventDistributor = eventDistributor;
        mState = new State();

        mContext = (Context) XposedHelpers.callMethod(mHost, "getContext");
        mGbContext = mContext.createPackageContext(GravityBox.PACKAGE_NAME,
                Context.CONTEXT_IGNORE_SECURITY);

        mTile = XposedHelpers.callStaticMethod(XposedHelpers.findClass(
                CLASS_INTENT_TILE, mContext.getClassLoader()),
                "create", mHost, DUMMY_INTENT);
        XposedHelpers.setAdditionalInstanceField(mTile, TILE_KEY_NAME, key);

        initPreferences();
        mEventDistributor.registerListener(this);
    }

    public abstract void handleUpdateState(Object state, Object arg);
    public abstract void handleClick();
    public abstract boolean handleLongClick(View view);

    public void initPreferences() {
        String enabledTiles = mPrefs.getString(TileOrderActivity.PREF_KEY_TILE_ORDER, null);
        mEnabled = enabledTiles != null && enabledTiles.contains(mKey);
        mState.visible = mEnabled;
    }

    public Object getTile() {
        return mTile;
    }

    @Override
    public String getKey() {
        return mKey;
    }

    @Override
    public boolean supportsDualTargets() {
        return false;
    }

    @Override
    public void handleSecondaryClick() {
        // optional
    }

    @Override
    public void setListening(boolean listening) {
        // optional
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
    }

    @Override
    public void handleDestroy() {
        if (DEBUG) log(mKey + ": handleDestroy called");
        mEventDistributor.unregisterListener(this);
    }

    @Override
    public void onBroadcastReceived(Context context, Intent intent) {
        if (DEBUG) log(mKey + ": onBrodcastReceived: " + intent);
        final String action = intent.getAction();
        if (action.equals(GravityBoxSettings.ACTION_PREF_QUICKSETTINGS_CHANGED)) {
            if (intent.hasExtra(GravityBoxSettings.EXTRA_QS_PREFS)) {
                String enabledTiles = intent.getStringExtra(GravityBoxSettings.EXTRA_QS_PREFS);
                mEnabled = enabledTiles != null && enabledTiles.contains(mKey);
                mState.visible = mEnabled;
            }
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

    public static class State {
        public boolean visible;
        public Drawable icon;
        public String label;
        public boolean autoMirrorDrawable = true;

        public void applyTo(Object state) {
            XposedHelpers.setBooleanField(state, "visible", visible);
            XposedHelpers.setObjectField(state, "icon", icon);
            XposedHelpers.setObjectField(state, "label", label);
            XposedHelpers.setBooleanField(state, "autoMirrorDrawable", autoMirrorDrawable);
        }
    }
}
