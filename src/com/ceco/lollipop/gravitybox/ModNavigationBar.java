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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.XModuleResources;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ImageView.ScaleType;

import com.ceco.lollipop.gravitybox.R;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam;

public class ModNavigationBar {
    public static final String PACKAGE_NAME = "com.android.systemui";
    private static final String TAG = "GB:ModNavigationBar";
    private static final boolean DEBUG = false;

    private static final String CLASS_NAVBAR_VIEW = "com.android.systemui.statusbar.phone.NavigationBarView";
    private static final String CLASS_KEY_BUTTON_VIEW = "com.android.systemui.statusbar.policy.KeyButtonView";
    private static final String CLASS_KEY_BUTTON_RIPPLE = "com.android.systemui.statusbar.policy.KeyButtonRipple";
    private static final String CLASS_NAVBAR_TRANSITIONS = 
            "com.android.systemui.statusbar.phone.NavigationBarTransitions";
    private static final String CLASS_SEARCH_PANEL_VIEW_CIRCLE = "com.android.systemui.SearchPanelCircleView";
//    private static final String CLASS_GLOWPAD_TRIGGER_LISTENER = CLASS_SEARCH_PANEL_VIEW + "$GlowPadTriggerListener";
//    private static final String CLASS_GLOWPAD_VIEW = "com.android.internal.widget.multiwaveview.GlowPadView";
    private static final String CLASS_PHONE_STATUSBAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";
    private static final String CLASS_DEADZONE = "com.android.systemui.statusbar.policy.DeadZone";

    private static final int MODE_OPAQUE = 0;
    private static final int MODE_LIGHTS_OUT = 3;

    private static final int NAVIGATION_HINT_BACK_ALT = 1 << 0;
    private static final int STATUS_BAR_DISABLE_RECENT = 0x01000000;

    private static boolean mAlwaysShowMenukey;
    private static View mNavigationBarView;
    private static Object[] mRecentsKeys;
    private static HomeKeyInfo[] mHomeKeys;
    private static ModHwKeys.HwKeyAction mRecentsSingletapActionBck = new ModHwKeys.HwKeyAction(0, null);
    private static ModHwKeys.HwKeyAction mRecentsSingletapAction = new ModHwKeys.HwKeyAction(0, null);
    private static ModHwKeys.HwKeyAction mRecentsLongpressAction = new ModHwKeys.HwKeyAction(0, null);
    private static ModHwKeys.HwKeyAction mRecentsDoubletapAction = new ModHwKeys.HwKeyAction(0, null);
    private static int mHomeLongpressAction = 0;
    private static boolean mHwKeysEnabled;
    private static boolean mCursorControlEnabled;
    private static boolean mDpadKeysVisible;
    private static boolean mNavbarVertical;
    private static boolean mNavbarRingDisabled;
    private static boolean mNavbarLeftHanded;
    private static boolean mUseLargerIcons;
    private static boolean mHideImeSwitcher;

    // Custom key
    private static boolean mCustomKeyEnabled;
    private static Resources mResources;
    private static Context mGbContext;
    private static NavbarViewInfo[] mNavbarViewInfo = new NavbarViewInfo[2];
    private static boolean mCustomKeySwapEnabled;
    private static boolean mCustomKeyAltIcon;

    // Colors
    private static boolean mNavbarColorsEnabled;
    private static int mKeyDefaultColor = 0xe8ffffff;
    private static int mKeyDefaultGlowColor = 0x33ffffff;
    private static int mKeyColor;
    private static int mKeyGlowColor;

    // TODO: Ring targets
//    private static enum RingHapticFeedback { DEFAULT, ENABLED, DISABLED };
//    private static boolean mRingTargetsEnabled = false;
//    private static View mGlowPadView;
//    private static BgStyle mRingTargetsBgStyle;
//    private static RingHapticFeedback mRingHapticFeedback;
//    private static Integer mRingVibrateDurationOrig;
//    private static KeyguardManager mKeyguard;

    private static Drawable mRecentIcon, mRecentLandIcon;
    private static Drawable mRecentAltIcon, mRecentAltLandIcon;
    private static boolean mRecentAlt = false;
    private static ImageView mRecentBtn = null;

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    static class HomeKeyInfo {
        public ImageView homeKey;
        public boolean supportsLongPressDefault;
    }

    static class NavbarViewInfo {
        ViewGroup navButtons;
        View originalView;
        KeyButtonView customKey;
        KeyButtonView dpadLeft;
        KeyButtonView dpadRight;
        int customKeyPosition;
        boolean visible;
        boolean menuCustomSwapped;
        ViewGroup menuImeGroup;
    }

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) log("Broadcast received: " + intent.toString());
            if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_NAVBAR_CHANGED)) {
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_MENUKEY)) {
                    mAlwaysShowMenukey = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_MENUKEY, false);
                    if (DEBUG) log("mAlwaysShowMenukey = " + mAlwaysShowMenukey);
                    setMenuKeyVisibility();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_CUSTOM_KEY_ENABLE)) {
                    mCustomKeyEnabled = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_CUSTOM_KEY_ENABLE, false);
                    setCustomKeyVisibility();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_KEY_COLOR)) {
                    mKeyColor = intent.getIntExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_KEY_COLOR, mKeyDefaultColor);
                    setKeyColor();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_KEY_GLOW_COLOR)) {
                    mKeyGlowColor = intent.getIntExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_KEY_GLOW_COLOR, mKeyDefaultGlowColor);
                    setKeyColor();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_COLOR_ENABLE)) {
                    mNavbarColorsEnabled = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_COLOR_ENABLE, false);
                    setKeyColor();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_CURSOR_CONTROL)) {
                    mCursorControlEnabled = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_CURSOR_CONTROL, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_CUSTOM_KEY_SWAP)) {
                    mCustomKeySwapEnabled = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_CUSTOM_KEY_SWAP, false);
                    setCustomKeyVisibility();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_RING_DISABLE)) {
                    mNavbarRingDisabled = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_RING_DISABLE, false);
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_CUSTOM_KEY_ICON)) {
                    mCustomKeyAltIcon = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_CUSTOM_KEY_ICON, false);
                    updateCustomKeyIcon();
                }
                if (intent.hasExtra(GravityBoxSettings.EXTRA_NAVBAR_HIDE_IME)) {
                    mHideImeSwitcher = intent.getBooleanExtra(
                            GravityBoxSettings.EXTRA_NAVBAR_HIDE_IME, false);
                }
            } else if (intent.getAction().equals(
                    GravityBoxSettings.ACTION_PREF_HWKEY_CHANGED) && 
                    GravityBoxSettings.PREF_KEY_HWKEY_RECENTS_SINGLETAP.equals(intent.getStringExtra(
                            GravityBoxSettings.EXTRA_HWKEY_KEY))) {
                mRecentsSingletapAction.actionId = intent.getIntExtra(GravityBoxSettings.EXTRA_HWKEY_VALUE, 0);
                mRecentsSingletapAction.customApp = intent.getStringExtra(GravityBoxSettings.EXTRA_HWKEY_CUSTOM_APP);
                if (mRecentsSingletapAction.actionId != GravityBoxSettings.HWKEY_ACTION_CLEAR_ALL_RECENTS_SINGLETAP) {
                    mRecentsSingletapActionBck.actionId = mRecentsSingletapAction.actionId;
                    mRecentsSingletapActionBck.customApp = mRecentsSingletapAction.customApp;
                    if (DEBUG) log("mRecentsSingletapActionBck.actionId = " + mRecentsSingletapActionBck.actionId);
                }
                updateRecentsKeyCode();
            } else if (intent.getAction().equals(
                    GravityBoxSettings.ACTION_PREF_HWKEY_CHANGED) &&
                    GravityBoxSettings.PREF_KEY_HWKEY_RECENTS_LONGPRESS.equals(intent.getStringExtra(
                            GravityBoxSettings.EXTRA_HWKEY_KEY))) {
                mRecentsLongpressAction.actionId = intent.getIntExtra(GravityBoxSettings.EXTRA_HWKEY_VALUE, 0);
                mRecentsLongpressAction.customApp = intent.getStringExtra(GravityBoxSettings.EXTRA_HWKEY_CUSTOM_APP);
                updateRecentsKeyCode();
            } else if (intent.getAction().equals(
                    GravityBoxSettings.ACTION_PREF_HWKEY_CHANGED) &&
                    GravityBoxSettings.PREF_KEY_HWKEY_RECENTS_DOUBLETAP.equals(intent.getStringExtra(
                            GravityBoxSettings.EXTRA_HWKEY_KEY))) {
                mRecentsDoubletapAction.actionId = intent.getIntExtra(GravityBoxSettings.EXTRA_HWKEY_VALUE, 0);
                mRecentsDoubletapAction.customApp = intent.getStringExtra(GravityBoxSettings.EXTRA_HWKEY_CUSTOM_APP);
                updateRecentsKeyCode();
            } else if (intent.getAction().equals(
                    GravityBoxSettings.ACTION_PREF_HWKEY_CHANGED) &&
                    GravityBoxSettings.PREF_KEY_HWKEY_HOME_LONGPRESS.equals(intent.getStringExtra(
                            GravityBoxSettings.EXTRA_HWKEY_KEY))) {
                mHomeLongpressAction = intent.getIntExtra(GravityBoxSettings.EXTRA_HWKEY_VALUE, 0);
                updateHomeKeyLongpressSupport();
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_PIE_CHANGED) && 
                    intent.hasExtra(GravityBoxSettings.EXTRA_PIE_HWKEYS_DISABLE)) {
                mHwKeysEnabled = !intent.getBooleanExtra(GravityBoxSettings.EXTRA_PIE_HWKEYS_DISABLE, false);
                updateRecentsKeyCode();
            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_NAVBAR_SWAP_KEYS)) {
                swapBackAndRecents();
            }
            // TODO: Navbar ring targets
//            } else if (intent.getAction().equals(GravityBoxSettings.ACTION_PREF_NAVBAR_RING_TARGET_CHANGED)) {
//                if (intent.hasExtra(GravityBoxSettings.EXTRA_RING_TARGET_INDEX) &&
//                        intent.hasExtra(GravityBoxSettings.EXTRA_RING_TARGET_APP)) {
//                    updateRingTarget(intent.getIntExtra(GravityBoxSettings.EXTRA_RING_TARGET_INDEX, -1),
//                            intent.getStringExtra(GravityBoxSettings.EXTRA_RING_TARGET_APP));
//                }
//                if (intent.hasExtra(GravityBoxSettings.EXTRA_RING_TARGET_BG_STYLE)) {
//                    mRingTargetsBgStyle = BgStyle.valueOf(
//                            intent.getStringExtra(GravityBoxSettings.EXTRA_RING_TARGET_BG_STYLE));
//                    GlowPadHelper.clearAppInfoCache();
//                    setRingTargets();
//                }
//                if (intent.hasExtra(GravityBoxSettings.EXTRA_RING_HAPTIC_FEEDBACK)) {
//                    mRingHapticFeedback = RingHapticFeedback.valueOf(
//                            intent.getStringExtra(GravityBoxSettings.EXTRA_RING_HAPTIC_FEEDBACK));
//                    setRingHapticFeedback();
//                }
//            }
        }
    };

    public static void initResources(final XSharedPreferences prefs, final InitPackageResourcesParam resparam) {
        if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_LARGER_ICONS, false)) {
            Map<String, Integer> ic_map = new HashMap<String, Integer>();
            XModuleResources modRes = XModuleResources.createInstance(GravityBox.MODULE_PATH, resparam.res);

            ic_map.put("ic_sysbar_back_ime", R.drawable.ic_sysbar_back_ime_large);
            ic_map.put("ic_sysbar_back", R.drawable.ic_sysbar_back_large);
            ic_map.put("ic_sysbar_back_land", R.drawable.ic_sysbar_back_land_large);
            ic_map.put("ic_sysbar_menu", R.drawable.ic_sysbar_menu_large);
            ic_map.put("ic_sysbar_menu_land", R.drawable.ic_sysbar_menu_land_large);
            ic_map.put("ic_sysbar_recent", R.drawable.ic_sysbar_recent_large);
            ic_map.put("ic_sysbar_recent_land", R.drawable.ic_sysbar_recent_land_large);
            ic_map.put("ic_sysbar_home", R.drawable.ic_sysbar_home_large);
            ic_map.put("ic_sysbar_home_land", R.drawable.ic_sysbar_home_land_large);
            for (String key : ic_map.keySet()) {
                try {
                    resparam.res.setReplacement(PACKAGE_NAME, "drawable", key,
                            modRes.fwd(ic_map.get(key)));
                } catch (Throwable t) {
                    log("Drawable not found: " + key);
                }
            }
        }
    }

    public static void init(final XSharedPreferences prefs, final ClassLoader classLoader) {
        try {
            final Class<?> navbarViewClass = XposedHelpers.findClass(CLASS_NAVBAR_VIEW, classLoader);
            final Class<?> navbarTransitionsClass = XposedHelpers.findClass(CLASS_NAVBAR_TRANSITIONS, classLoader);
            final Class<?> phoneStatusbarClass = XposedHelpers.findClass(CLASS_PHONE_STATUSBAR, classLoader);

            mAlwaysShowMenukey = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_MENUKEY, false);
            // TODO: Navbar ring targets
            //mRingTargetsEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_RING_TARGETS_ENABLE, false);
            mNavbarLeftHanded = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_ENABLE, false) &&
                    prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_LEFT_HANDED, false);
            mUseLargerIcons = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_LARGER_ICONS, false);

            try {
                mRecentsSingletapAction = new ModHwKeys.HwKeyAction(Integer.valueOf(
                        prefs.getString(GravityBoxSettings.PREF_KEY_HWKEY_RECENTS_SINGLETAP, "0")),
                        prefs.getString(GravityBoxSettings.PREF_KEY_HWKEY_RECENTS_SINGLETAP+"_custom", null));
                mRecentsLongpressAction = new ModHwKeys.HwKeyAction(Integer.valueOf(
                        prefs.getString(GravityBoxSettings.PREF_KEY_HWKEY_RECENTS_LONGPRESS, "0")),
                        prefs.getString(GravityBoxSettings.PREF_KEY_HWKEY_RECENTS_LONGPRESS+"_custom", null));
                mRecentsSingletapActionBck.actionId = mRecentsSingletapAction.actionId;
                mRecentsSingletapActionBck.customApp = mRecentsSingletapAction.customApp;
                mHomeLongpressAction = Integer.valueOf(
                        prefs.getString(GravityBoxSettings.PREF_KEY_HWKEY_HOME_LONGPRESS, "0"));
            } catch (NumberFormatException nfe) {
                XposedBridge.log(nfe);
            }

            mCustomKeyEnabled = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_NAVBAR_CUSTOM_KEY_ENABLE, false);
            mHwKeysEnabled = !prefs.getBoolean(GravityBoxSettings.PREF_KEY_HWKEYS_DISABLE, false);
            mCursorControlEnabled = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_NAVBAR_CURSOR_CONTROL, false);
            mCustomKeySwapEnabled = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_NAVBAR_CUSTOM_KEY_SWAP, false);
            mNavbarRingDisabled = prefs.getBoolean(
                    GravityBoxSettings.PREF_KEY_NAVBAR_RING_DISABLE, false);
            mHideImeSwitcher = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_HIDE_IME, false);

            // for HTC GPE devices having capacitive keys
            if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_ENABLE, false)) {
                try {
                    Class<?> sbFlagClass = XposedHelpers.findClass(
                            "com.android.systemui.statusbar.StatusBarFlag", classLoader);
                    XposedHelpers.setStaticBooleanField(sbFlagClass, "supportHWNav", false);
                } catch (Throwable t) { }
            }

            XposedBridge.hookAllConstructors(navbarViewClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Context context = (Context) param.args[0];
                    if (context == null) return;

                    mResources = context.getResources();

                    mGbContext = context.createPackageContext(
                            GravityBox.PACKAGE_NAME, Context.CONTEXT_IGNORE_SECURITY);
                    final Resources res = mGbContext.getResources();
                    mNavbarColorsEnabled = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_COLOR_ENABLE, false);
                    mKeyDefaultColor = res.getColor(R.color.navbar_key_color);
                    mKeyColor = prefs.getInt(GravityBoxSettings.PREF_KEY_NAVBAR_KEY_COLOR, mKeyDefaultColor);
                    mKeyDefaultGlowColor = res.getColor(R.color.navbar_key_glow_color);
                    mKeyGlowColor = prefs.getInt(
                            GravityBoxSettings.PREF_KEY_NAVBAR_KEY_GLOW_COLOR, mKeyDefaultGlowColor);
                    mCustomKeyAltIcon = prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_CUSTOM_KEY_ICON, false);

                 // TODO: Navbar ring targets
//                    try {
//                        mKeyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
//                    } catch (Throwable t) { log("Error getting keyguard manager: " + t.getMessage()); }

                    mNavigationBarView = (View) param.thisObject;
                    IntentFilter intentFilter = new IntentFilter();
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_NAVBAR_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_HWKEY_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_PIE_CHANGED);
                    intentFilter.addAction(GravityBoxSettings.ACTION_PREF_NAVBAR_SWAP_KEYS);
                    // TODO: Navbar ring targets
//                    if (mRingTargetsEnabled) {
//                        intentFilter.addAction(GravityBoxSettings.ACTION_PREF_NAVBAR_RING_TARGET_CHANGED);
//                    }
                    context.registerReceiver(mBroadcastReceiver, intentFilter);
                    if (DEBUG) log("NavigationBarView constructed; Broadcast receiver registered");
                }
            });

            XposedHelpers.findAndHookMethod(navbarViewClass, "setMenuVisibility",
                    boolean.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    setMenuKeyVisibility();
                }
            });

            XposedHelpers.findAndHookMethod(navbarViewClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final Context context = ((View) param.thisObject).getContext();
                    final Resources gbRes = mGbContext.getResources();
                    final int backButtonResId = mResources.getIdentifier("back", "id", PACKAGE_NAME);
                    final int recentAppsResId = mResources.getIdentifier("recent_apps", "id", PACKAGE_NAME);
                    final int homeButtonResId = mResources.getIdentifier("home", "id", PACKAGE_NAME);
                    final View[] rotatedViews = 
                            (View[]) XposedHelpers.getObjectField(param.thisObject, "mRotatedViews");

                    if (rotatedViews != null) {
                        mRecentsKeys = new Object[rotatedViews.length];
                        mHomeKeys = new HomeKeyInfo[rotatedViews.length];
                        int index = 0;
                        for(View v : rotatedViews) {
                            if (backButtonResId != 0) { 
                                ImageView backButton = (ImageView) v.findViewById(backButtonResId);
                                if (backButton != null) {
                                    backButton.setScaleType(ScaleType.FIT_CENTER);
                                }
                            }
                            if (recentAppsResId != 0) {
                                ImageView recentAppsButton = (ImageView) v.findViewById(recentAppsResId);
                                mRecentsKeys[index] = recentAppsButton;
                            }
                            if (homeButtonResId != 0) { 
                                HomeKeyInfo hkInfo = new HomeKeyInfo();
                                hkInfo.homeKey = (ImageView) v.findViewById(homeButtonResId);
                                if (hkInfo.homeKey != null) {
                                    hkInfo.supportsLongPressDefault = 
                                        XposedHelpers.getBooleanField(hkInfo.homeKey, "mSupportsLongpress");
                                }
                                mHomeKeys[index] = hkInfo;
                            }
                            index++;
                        }
                    }

                    // prepare app, dpad left, dpad right keys
                    ViewGroup vRot, navButtons;

                    // prepare keys for rot0 view
                    vRot = (ViewGroup) ((ViewGroup) param.thisObject).findViewById(
                            mResources.getIdentifier("rot0", "id", PACKAGE_NAME));
                    if (vRot != null) {
                        KeyButtonView appKey = new KeyButtonView(context);
                        appKey.setScaleType(ScaleType.FIT_CENTER);
                        appKey.setClickable(true);
                        appKey.setImageDrawable(gbRes.getDrawable(getCustomKeyIconId()));
                        appKey.setKeyCode(KeyEvent.KEYCODE_SOFT_LEFT);

                        KeyButtonView dpadLeft = new KeyButtonView(context);
                        dpadLeft.setScaleType(ScaleType.FIT_CENTER);
                        dpadLeft.setClickable(true);
                        dpadLeft.setImageDrawable(gbRes.getDrawable(mUseLargerIcons ?
                                R.drawable.ic_sysbar_ime_left :
                                        R.drawable.ic_sysbar_ime_left_lollipop));
                        dpadLeft.setVisibility(View.GONE);
                        dpadLeft.setKeyCode(KeyEvent.KEYCODE_DPAD_LEFT);

                        KeyButtonView dpadRight = new KeyButtonView(context);
                        dpadRight.setScaleType(ScaleType.FIT_CENTER);
                        dpadRight.setClickable(true);
                        dpadRight.setImageDrawable(gbRes.getDrawable(mUseLargerIcons ?
                                R.drawable.ic_sysbar_ime_right :
                                        R.drawable.ic_sysbar_ime_right_lollipop));
                        dpadRight.setVisibility(View.GONE);
                        dpadRight.setKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT);

                        navButtons = (ViewGroup) vRot.findViewById(
                                mResources.getIdentifier("nav_buttons", "id", PACKAGE_NAME));
                        prepareNavbarViewInfo(navButtons, 0, appKey, dpadLeft, dpadRight);
                    }

                    // prepare keys for rot90 view
                    vRot = (ViewGroup) ((ViewGroup) param.thisObject).findViewById(
                            mResources.getIdentifier("rot90", "id", PACKAGE_NAME));
                    if (vRot != null) {
                        KeyButtonView appKey = new KeyButtonView(context);
                        appKey.setClickable(true);
                        appKey.setImageDrawable(gbRes.getDrawable(getCustomKeyIconId()));
                        appKey.setKeyCode(KeyEvent.KEYCODE_SOFT_LEFT);

                        KeyButtonView dpadLeft = new KeyButtonView(context);
                        dpadLeft.setClickable(true);
                        dpadLeft.setImageDrawable(gbRes.getDrawable(mUseLargerIcons ?
                                            R.drawable.ic_sysbar_ime_left :
                                                R.drawable.ic_sysbar_ime_left_lollipop));
                        dpadLeft.setVisibility(View.GONE);
                        dpadLeft.setKeyCode(KeyEvent.KEYCODE_DPAD_LEFT);

                        KeyButtonView dpadRight = new KeyButtonView(context);
                        dpadRight.setClickable(true);
                        dpadRight.setImageDrawable(gbRes.getDrawable(mUseLargerIcons ?
                                            R.drawable.ic_sysbar_ime_right :
                                                R.drawable.ic_sysbar_ime_right_lollipop));
                        dpadRight.setVisibility(View.GONE);
                        dpadRight.setKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT);

                        navButtons = (ViewGroup) vRot.findViewById(
                                mResources.getIdentifier("nav_buttons", "id", PACKAGE_NAME));
                        prepareNavbarViewInfo(navButtons, 1, appKey, dpadLeft, dpadRight);
                    }

                    updateRecentsKeyCode();
                    updateHomeKeyLongpressSupport();

                    if (prefs.getBoolean(GravityBoxSettings.PREF_KEY_NAVBAR_SWAP_KEYS, false)) {
                        swapBackAndRecents();
                    }
                }
            });

            XposedHelpers.findAndHookMethod(navbarViewClass, "setDisabledFlags",
                    int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    setCustomKeyVisibility();
                    setMenuKeyVisibility();
                }
            });

            XposedHelpers.findAndHookMethod(navbarViewClass, "getIcons", Resources.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    mRecentIcon = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mRecentIcon");
                    mRecentLandIcon = (Drawable) XposedHelpers.getObjectField(param.thisObject, "mRecentLandIcon");

                    if (mGbContext != null) {
                        final Resources gbRes = mGbContext.getResources();
                        mRecentAltIcon = gbRes.getDrawable(mUseLargerIcons ?
                                R.drawable.ic_sysbar_recent_clear: R.drawable.ic_sysbar_recent_clear_lollipop );
                        mRecentAltLandIcon = gbRes.getDrawable(mUseLargerIcons ?
                                R.drawable.ic_sysbar_recent_clear_land : R.drawable.ic_sysbar_recent_clear_land_lollipop);

                        XposedHelpers.setObjectField(param.thisObject, "mBackAltLandIcon",
                                    gbRes.getDrawable(mUseLargerIcons ?
                                            R.drawable.ic_sysbar_back_ime_land_large :
                                                R.drawable.ic_sysbar_back_ime_land));
                    }
                }
            });

            XposedHelpers.findAndHookMethod(navbarViewClass, "setNavigationIconHints",
                    int.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mNavbarColorsEnabled) {
                        final int navigationIconHints = XposedHelpers.getIntField(
                                param.thisObject, "mNavigationIconHints");
                        if ((Integer) param.args[0] != navigationIconHints || (Boolean)param.args[1]) {
                            setKeyColor();
                        }
                    }
                    if (mHideImeSwitcher) {
                        hideImeSwitcher();
                    }
                    setDpadKeyVisibility();

                    try {
                        Method m = XposedHelpers.findMethodExact(navbarViewClass, "getRecentsButton");
                        mRecentBtn = (ImageView) m.invoke(param.thisObject);
                    } catch(NoSuchMethodError nme) {
                        if (DEBUG) log("getRecentsButton method doesn't exist");
                    }
                    mNavbarVertical = XposedHelpers.getBooleanField(param.thisObject, "mVertical");
                    updateRecentAltButton();
                }
            });

            XposedHelpers.findAndHookMethod(navbarTransitionsClass, "applyMode",
                    int.class, boolean.class, boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    final int mode = (Integer) param.args[0];
                    final boolean animate = (Boolean) param.args[1];
                    final boolean isOpaque = mode == MODE_OPAQUE || mode == MODE_LIGHTS_OUT;
                    final float alpha = isOpaque ? KeyButtonView.DEFAULT_QUIESCENT_ALPHA : 1f;
                    for(int i = 0; i < mNavbarViewInfo.length; i++) {
                        if (mNavbarViewInfo[i] != null) {
                            if (mNavbarViewInfo[i].customKey != null) {
                                mNavbarViewInfo[i].customKey.setQuiescentAlpha(alpha, animate);
                            }
                            if (mNavbarViewInfo[i].dpadLeft != null) {
                                mNavbarViewInfo[i].dpadLeft.setQuiescentAlpha(alpha, animate);
                            }
                            if (mNavbarViewInfo[i].dpadRight != null) {
                                mNavbarViewInfo[i].dpadRight.setQuiescentAlpha(alpha, animate);
                            }
                        }
                    }
                }
            });

            // TODO: Navbar ring targets
//            if (mRingTargetsEnabled || mNavbarLeftHanded) {
//                final Class<?> searchPanelViewClass = XposedHelpers.findClass(CLASS_SEARCH_PANEL_VIEW, classLoader);
//                final Class<?> glowPadTriggerListenerClass = XposedHelpers.findClass(CLASS_GLOWPAD_TRIGGER_LISTENER, classLoader);
//                final Class<?> glowPasViewClass = XposedHelpers.findClass(CLASS_GLOWPAD_VIEW, classLoader);
//
//                mRingTargetsBgStyle = BgStyle.valueOf(
//                        prefs.getString(GravityBoxSettings.PREF_KEY_NAVBAR_RING_TARGETS_BG_STYLE, "NONE"));
//                mRingHapticFeedback = RingHapticFeedback.valueOf(
//                        prefs.getString(GravityBoxSettings.PREF_KEY_NAVBAR_RING_HAPTIC_FEEDBACK, "DEFAULT"));

//                XposedHelpers.findAndHookMethod(searchPanelViewClass, "onFinishInflate", new XC_MethodHook() {
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        mGlowPadView = (View) XposedHelpers.getObjectField(param.thisObject, "mGlowPadView");
//                        if (mRingTargetsEnabled) {
//                            prefs.reload();
//                            setRingHapticFeedback();
//                            setRingTargets();
//                        }
//                    }
//                });

//                XC_MethodHook glowPadViewShowTargetsHook = new XC_MethodHook() {
//                    @Override
//                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
//                        if (param.thisObject == mGlowPadView) {
//                            if (mNavbarVertical && !isGlowPadVertical()) {
//                                rotateRingTargets();
//                            }
//                        }
//                    }
//                };
//                if (Utils.isXperiaDevice()) {
//                    XposedHelpers.findAndHookMethod(glowPasViewClass, "showTargets", boolean.class, int.class, glowPadViewShowTargetsHook);
//                } else {
//                    XposedHelpers.findAndHookMethod(glowPasViewClass, "showTargets", boolean.class, glowPadViewShowTargetsHook);
//                }

//                XposedHelpers.findAndHookMethod(navbarViewClass, "reorient", new XC_MethodHook() {
//                    @Override
//                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                        if (DEBUG) log("Navigation bar view reorient");
//                        if (mGlowPadView != null) {
//                            mNavbarVertical = XposedHelpers.getBooleanField(param.thisObject, "mVertical");
//                            if (mNavbarVertical && !isGlowPadVertical()) {
//                                rotateRingTargets();
//                            }
//                        }
//                    }
//                });

//                if (mRingTargetsEnabled) {
//                    XposedHelpers.findAndHookMethod(glowPadTriggerListenerClass, "onTrigger",
//                            View.class, int.class, new XC_MethodHook() {
//                        @Override
//                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                            if (DEBUG) log("GlowPadView.OnTriggerListener; index=" + ((Integer) param.args[1]));
//                            final int index = (Integer) param.args[1];
//                            @SuppressWarnings("unchecked")
//                            final ArrayList<Object> targets = (ArrayList<Object>) XposedHelpers.getObjectField(
//                                    mGlowPadView, "mTargetDrawables");
//                            final Object td = targets.get(index);
//        
//                            AppInfo appInfo = (AppInfo) XposedHelpers.getAdditionalInstanceField(td, "mGbAppInfo");
//                            if (appInfo != null) {
//                                try {
//                                    Object activityManagerNative = XposedHelpers.callStaticMethod(
//                                        XposedHelpers.findClass("android.app.ActivityManagerNative", null),
//                                            "getDefault");
//                                    XposedHelpers.callMethod(activityManagerNative, "dismissKeyguardOnNextActivity");
//                                } catch (Throwable t) {}
//                                Intent intent = appInfo.intent;
//                                // if intent is a GB action of broadcast type, handle it directly here
//                                if (ShortcutActivity.isGbBroadcastShortcut(intent)) {
//                                    if (mKeyguard != null && 
//                                            mKeyguard.isKeyguardLocked() && mKeyguard.isKeyguardSecure() &&
//                                                !ShortcutActivity.isActionSafe(intent.getStringExtra(
//                                                        ShortcutActivity.EXTRA_ACTION))) {
//                                        if (DEBUG) log("Keyguard is locked & secured - ignoring GB action");
//                                    } else {
//                                        Intent newIntent = new Intent(intent.getStringExtra(ShortcutActivity.EXTRA_ACTION));
//                                        newIntent.putExtras(intent);
//                                        mGlowPadView.getContext().sendBroadcast(newIntent);
//                                    }
//                                // otherwise start activity
//                                } else {
//                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
//                                    mGlowPadView.getContext().startActivity(intent);
//                                    mGlowPadView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
//                                }
//                            }
//                        }
//                    });
//                }
//            }

            XposedHelpers.findAndHookMethod(phoneStatusbarClass, "shouldDisableNavbarGestures", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (mNavbarRingDisabled) {
                        param.setResult(true);
                    }
                }
            });

            if (mNavbarLeftHanded) {
                XposedHelpers.findAndHookMethod(CLASS_DEADZONE, classLoader, "onTouchEvent",
                        MotionEvent.class, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            View v = (View) param.thisObject;
                            MotionEvent event = (MotionEvent) param.args[0];
                            final int action = event.getAction();
                            if (action == MotionEvent.ACTION_OUTSIDE) {
                                XposedHelpers.setLongField(v, "mLastPokeTime", event.getEventTime());
                            } else if (action == MotionEvent.ACTION_DOWN) {
                                int size = (int)(float)(Float)(XposedHelpers.callMethod(v, "getSize", event.getEventTime()));
                                boolean vertical = XposedHelpers.getBooleanField(v, "mVertical");
                                boolean isCaptured;
                                if (vertical) {
                                    float pixelsFromRight = v.getWidth() - event.getX();
                                    isCaptured = 0 <= pixelsFromRight && pixelsFromRight < size;
                                } else {
                                    isCaptured = event.getY() < size;
                                }
                                if (isCaptured) {
                                    return true;
                                }
                            }
                            return false;
                        } catch (Throwable t) {
                            XposedBridge.log(t);
                            return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                        }
                    }
                });

                XposedHelpers.findAndHookMethod(CLASS_SEARCH_PANEL_VIEW_CIRCLE, classLoader,
                        "updateCircleRect", Rect.class, float.class, boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        if (XposedHelpers.getBooleanField(param.thisObject, "mHorizontal")) {
                            float circleSize = XposedHelpers.getFloatField(param.thisObject,
                                    (boolean) param.args[2] ? "mCircleMinSize" : "mCircleSize");
                            int baseMargin = XposedHelpers.getIntField(param.thisObject, "mBaseMargin");
                            float offset = (float) param.args[1];
                            int left = (int) (-(circleSize / 2) + baseMargin + offset);
                            Rect rect = (Rect) param.args[0];
                            rect.set(left, rect.top, (int) (left + circleSize), rect.bottom);
                            if (DEBUG) log("SearchPanelCircleView rect: " + rect);
                        }
                    }
                });
            }

            XposedHelpers.findAndHookMethod(CLASS_KEY_BUTTON_RIPPLE, classLoader,
                    "getRipplePaint", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (mNavbarColorsEnabled) {
                        ((Paint)param.getResult()).setColor(mKeyGlowColor);
                    }
                }
            });
        } catch(Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void prepareNavbarViewInfo(ViewGroup navButtons, int index, 
            KeyButtonView appView, KeyButtonView dpadLeft, KeyButtonView dpadRight) {
        try {
            final int size = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    40, navButtons.getResources().getDisplayMetrics());
            if (DEBUG) log("App key view minimum size=" + size);

            mNavbarViewInfo[index] = new NavbarViewInfo();
            mNavbarViewInfo[index].navButtons = navButtons;
            mNavbarViewInfo[index].customKey = appView;
            mNavbarViewInfo[index].dpadLeft = dpadLeft;
            mNavbarViewInfo[index].dpadRight = dpadRight;
            mNavbarViewInfo[index].navButtons.addView(dpadLeft, 0);
            mNavbarViewInfo[index].navButtons.addView(dpadRight);

            int searchPosition = index == 0 ? 1 : navButtons.getChildCount()-2;
            View v = navButtons.getChildAt(searchPosition);
            if (v.getId() == -1 && !v.getClass().getName().equals(CLASS_KEY_BUTTON_VIEW) &&
                    !(v instanceof ViewGroup)) {
                mNavbarViewInfo[index].originalView = v;
            } else {
                searchPosition = searchPosition == 1 ? navButtons.getChildCount()-2 : 1;
                v = navButtons.getChildAt(searchPosition);
                if (v.getId() == -1 && !v.getClass().getName().equals(CLASS_KEY_BUTTON_VIEW) &&
                        !(v instanceof ViewGroup)) {
                    mNavbarViewInfo[index].originalView = v;
                }
            }
            mNavbarViewInfo[index].customKeyPosition = searchPosition;

            // find ime switcher and menu group
            int childCount = mNavbarViewInfo[index].navButtons.getChildCount();
            for (int i = 0; i < childCount; i++) {
                View child = mNavbarViewInfo[index].navButtons.getChildAt(i);
                if (child instanceof ViewGroup) {
                    mNavbarViewInfo[index].menuImeGroup = (ViewGroup)child;
                    break;
                }
            }

            // determine app key layout
            LinearLayout.LayoutParams lp = null;
            if (mNavbarViewInfo[index].originalView != null) {
                // determine layout from layout of placeholder view we found
                ViewGroup.LayoutParams ovlp = mNavbarViewInfo[index].originalView.getLayoutParams();
                if (DEBUG) log("originalView: lpWidth=" + ovlp.width + "; lpHeight=" + ovlp.height);
                if (ovlp instanceof LinearLayout.LayoutParams) {
                    lp = (LinearLayout.LayoutParams) ovlp;
                } else if (ovlp.width >= 0) {
                    lp = new LinearLayout.LayoutParams(ovlp.width, LinearLayout.LayoutParams.MATCH_PARENT, 0);
                } else if (ovlp.height >= 0) {
                    lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ovlp.height, 0);
                } else {
                    log("Weird layout of placeholder view detected");
                }
            } else {
                // determine layout from Back key
                final int resId = navButtons.getResources().getIdentifier("back", "id", PACKAGE_NAME);
                if (resId != 0) {
                    View back = navButtons.findViewById(resId);
                    if (back != null) {
                        ViewGroup.LayoutParams blp = back.getLayoutParams();
                        if (blp.width >= 0) {
                            lp = new LinearLayout.LayoutParams(blp.width, LinearLayout.LayoutParams.MATCH_PARENT, 0);
                        } else if (blp.height >= 0) {
                            lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, blp.height, 0);
                        } else {
                            log("Weird layout of back button view detected");
                        }
                    } else {
                        log("Could not find back button view");
                    }
                } else {
                    log("Could not find back button resource ID");
                }
            }
            // worst case scenario (should never happen, but just to make sure)
            if (lp == null) {
                lp = new LinearLayout.LayoutParams(size, size, 0);
            }
            if (DEBUG) log("appView: lpWidth=" + lp.width + "; lpHeight=" + lp.height);
            mNavbarViewInfo[index].customKey.setLayoutParams(lp);
            mNavbarViewInfo[index].dpadLeft.setLayoutParams(lp);
            mNavbarViewInfo[index].dpadRight.setLayoutParams(lp);
        } catch (Throwable t) {
            log("Error preparing NavbarViewInfo: " + t.getMessage());
        }
    }

    private static void setCustomKeyVisibility() {
        try {
            final int disabledFlags = XposedHelpers.getIntField(mNavigationBarView, "mDisabledFlags");
            final boolean visible = mCustomKeyEnabled &&
                    !((disabledFlags & STATUS_BAR_DISABLE_RECENT) != 0);
            for (int i = 0; i <= 1; i++) {
                // swap / unswap with menu key if necessary
                if ((!mCustomKeyEnabled || !mCustomKeySwapEnabled) && 
                        mNavbarViewInfo[i].menuCustomSwapped) {
                    swapMenuAndCustom(mNavbarViewInfo[i]);
                } else if (mCustomKeyEnabled && mCustomKeySwapEnabled && 
                        !mNavbarViewInfo[i].menuCustomSwapped) {
                    swapMenuAndCustom(mNavbarViewInfo[i]);
                }

                if (mNavbarViewInfo[i].visible == visible) continue;

                if (mNavbarViewInfo[i].originalView != null) {
                    mNavbarViewInfo[i].navButtons.removeViewAt(mNavbarViewInfo[i].customKeyPosition);
                    mNavbarViewInfo[i].navButtons.addView(visible ?
                            mNavbarViewInfo[i].customKey : mNavbarViewInfo[i].originalView,
                            mNavbarViewInfo[i].customKeyPosition);
                } else {
                    if (visible) {
                        mNavbarViewInfo[i].navButtons.addView(mNavbarViewInfo[i].customKey,
                                mNavbarViewInfo[i].customKeyPosition);
                    } else {
                        mNavbarViewInfo[i].navButtons.removeView(mNavbarViewInfo[i].customKey);
                    }
                }
                mNavbarViewInfo[i].visible = visible;
                mNavbarViewInfo[i].navButtons.requestLayout();
                if (DEBUG) log("setAppKeyVisibility: visible=" + visible);
            }
        } catch (Throwable t) {
            log("Error setting app key visibility: " + t.getMessage());
        }
    }

    private static void setMenuKeyVisibility() {
        try {
            final boolean showMenu = XposedHelpers.getBooleanField(mNavigationBarView, "mShowMenu");
            final int disabledFlags = XposedHelpers.getIntField(mNavigationBarView, "mDisabledFlags");
            final boolean visible = (showMenu || mAlwaysShowMenukey) &&
                    !((disabledFlags & STATUS_BAR_DISABLE_RECENT) != 0);
            int menuResId = mResources.getIdentifier("menu", "id", PACKAGE_NAME);
            int imeSwitcherResId = mResources.getIdentifier("ime_switcher", "id", PACKAGE_NAME);
            for (int i = 0; i <= 1; i++) {
                boolean isImeSwitcherVisible = false;
                View v = null;
                if (imeSwitcherResId != 0) {
                    v = mNavbarViewInfo[i].navButtons.findViewById(imeSwitcherResId);
                    if (v != null) {
                        isImeSwitcherVisible = v.getVisibility() == View.VISIBLE;
                    }
                }
                v = mNavbarViewInfo[i].navButtons.findViewById(menuResId);
                if (v != null) {
                    v.setVisibility(mDpadKeysVisible || isImeSwitcherVisible ? View.GONE :
                        visible ? View.VISIBLE : View.INVISIBLE);
                }
            }
        } catch (Throwable t) {
            log("Error setting menu key visibility");
        }
        
    }

    private static void hideImeSwitcher() {
        try {
            int imeSwitcherResId = mResources.getIdentifier("ime_switcher", "id", PACKAGE_NAME);
            for (int i = 0; i <= 1; i++) {
                View v = mNavbarViewInfo[i].navButtons.findViewById(imeSwitcherResId);
                if (v != null) {
                    v.setVisibility(View.GONE);
                }
            }
        } catch (Throwable t) {
            log("Error hiding IME switcher: " + t.getMessage());
        }
    }

    public static void setRecentAlt(boolean recentAlt) {
        if (mRecentBtn == null || mRecentAlt == recentAlt) return;

        mRecentAlt = recentAlt;
        if (mRecentAlt) {
            updateRecentAltButton();
            broadcastRecentsActions(mRecentBtn.getContext(),
                    new ModHwKeys.HwKeyAction(GravityBoxSettings.HWKEY_ACTION_CLEAR_ALL_RECENTS_SINGLETAP, null)); 
        } else {
            mRecentBtn.post(resetRecentKeyStateRunnable);
        }
    }

    private static Runnable resetRecentKeyStateRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRecentBtn.isPressed()) {
                mRecentBtn.postDelayed(this, 200);
            } else {
                updateRecentAltButton();
                broadcastRecentsActions(mRecentBtn.getContext(), mRecentsSingletapActionBck);
            }
        }
    };

    private static void updateRecentAltButton() {
        if (mRecentBtn != null) {
            if (mRecentAlt) {
                mRecentBtn.setImageDrawable(mNavbarVertical ? mRecentAltLandIcon : mRecentAltIcon);
            } else {
                mRecentBtn.setImageDrawable(mNavbarVertical ? mRecentLandIcon : mRecentIcon);
            }
        }
    }

    private static void broadcastRecentsActions(Context context, ModHwKeys.HwKeyAction singleTapAction) {
        if (context == null) return;

        Intent intent;
        intent = new Intent();
        intent.setAction(GravityBoxSettings.ACTION_PREF_HWKEY_CHANGED);
        intent.putExtra(GravityBoxSettings.EXTRA_HWKEY_KEY, GravityBoxSettings.PREF_KEY_HWKEY_RECENTS_SINGLETAP);
        intent.putExtra(GravityBoxSettings.EXTRA_HWKEY_VALUE, singleTapAction.actionId);
        intent.putExtra(GravityBoxSettings.EXTRA_HWKEY_CUSTOM_APP, singleTapAction.customApp);
        context.sendBroadcast(intent);
    }

    private static void setDpadKeyVisibility() {
        if (!mCursorControlEnabled) return;
        try {
            final int iconHints = XposedHelpers.getIntField(mNavigationBarView, "mNavigationIconHints");
            final int disabledFlags = XposedHelpers.getIntField(mNavigationBarView, "mDisabledFlags");
            mDpadKeysVisible = !((disabledFlags & STATUS_BAR_DISABLE_RECENT) != 0) && 
                    (iconHints & NAVIGATION_HINT_BACK_ALT) != 0;

            for (int i = 0; i <= 1; i++) {
                // hide/unhide app key or whatever view at that position
                View v = mNavbarViewInfo[i].navButtons.getChildAt(mNavbarViewInfo[i].customKeyPosition);
                if (v != null) {
                    v.setVisibility(mDpadKeysVisible ? View.GONE : View.VISIBLE);
                }
                // hide/unhide menu key
                int menuResId = mResources.getIdentifier("menu", "id", PACKAGE_NAME);
                v = mNavbarViewInfo[i].navButtons.findViewById(menuResId);
                if (v != null) {
                    if (mDpadKeysVisible) {
                        v.setVisibility(View.GONE);
                    } else {
                        setMenuKeyVisibility();
                    }
                }
                // Hide view group holding menu/customkey and ime switcher if all children hidden
                if (mNavbarViewInfo[i].menuImeGroup != null) {
                    boolean allHidden = true;
                    for (int j = 0; j < mNavbarViewInfo[i].menuImeGroup.getChildCount(); j++) {
                        allHidden &= mNavbarViewInfo[i].menuImeGroup.getChildAt(j)
                                .getVisibility() != View.VISIBLE;
                    }
                    mNavbarViewInfo[i].menuImeGroup.setVisibility(
                            mDpadKeysVisible && allHidden ? View.GONE : View.VISIBLE);
                }
                mNavbarViewInfo[i].dpadLeft.setVisibility(mDpadKeysVisible ? View.VISIBLE : View.GONE);
                mNavbarViewInfo[i].dpadRight.setVisibility(mDpadKeysVisible ? View.VISIBLE : View.GONE);
                if (DEBUG) log("setDpadKeyVisibility: visible=" + mDpadKeysVisible);
            }
        } catch (Throwable t) {
            log("Error setting dpad key visibility: " + t.getMessage());
        }
    }

    private static void updateRecentsKeyCode() {
        if (mRecentsKeys == null) return;

        try {
            final boolean hasAction = recentsKeyHasAction();
            for (Object o : mRecentsKeys) {
                if (o != null) {
                    XposedHelpers.setIntField(o, "mCode", hasAction ? KeyEvent.KEYCODE_APP_SWITCH : 0);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static boolean recentsKeyHasAction() {
        return (mRecentsSingletapAction.actionId != 0 ||
                mRecentsLongpressAction.actionId != 0 ||
                mRecentsDoubletapAction.actionId != 0 ||
                !mHwKeysEnabled);
    }

    private static void updateHomeKeyLongpressSupport() {
        if (mHomeKeys == null) return;

        try {
            for (HomeKeyInfo hkInfo : mHomeKeys) {
                if (hkInfo.homeKey != null) {
                    XposedHelpers.setBooleanField(hkInfo.homeKey, "mSupportsLongpress",
                            mHomeLongpressAction == 0 ? hkInfo.supportsLongPressDefault : true);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void setKeyColor() {
        try {
            View v = (View) XposedHelpers.getObjectField(mNavigationBarView, "mCurrentView");
            ViewGroup navButtons = (ViewGroup) v.findViewById(
                    mResources.getIdentifier("nav_buttons", "id", PACKAGE_NAME));
            setKeyColorRecursive(navButtons);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static void setKeyColorRecursive(ViewGroup vg) {
        if (vg == null) return;
        final int childCount = vg.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = vg.getChildAt(i);
            if (child instanceof ViewGroup) {
                setKeyColorRecursive((ViewGroup) child);
            } else if (child instanceof ImageView) {
                ImageView imgv = (ImageView)vg.getChildAt(i);
                if (mNavbarColorsEnabled) {
                    imgv.setColorFilter(mKeyColor, PorterDuff.Mode.SRC_ATOP);
                } else {
                    imgv.clearColorFilter();
                }
                if (imgv.getClass().getName().equals(CLASS_KEY_BUTTON_VIEW) &&
                    !mNavbarColorsEnabled) {
                    Drawable ripple = imgv.getBackground();
                    if (ripple != null && 
                            ripple.getClass().getName().equals(CLASS_KEY_BUTTON_RIPPLE)) {
                        Paint paint = (Paint)XposedHelpers.getObjectField(ripple, "mRipplePaint");
                        if (paint != null) {
                            paint.setColor(0xffffffff);
                        }
                    }
                } else if (imgv instanceof KeyButtonView) {
                    ((KeyButtonView) imgv).setGlowColor(mNavbarColorsEnabled ?
                            mKeyGlowColor : mKeyDefaultGlowColor);
                }
            }
        }
    }

    private static void swapBackAndRecents() {
        try {
            final int backButtonResId = mResources.getIdentifier("back", "id", PACKAGE_NAME);
            final int recentAppsResId = mResources.getIdentifier("recent_apps", "id", PACKAGE_NAME);
            for (int i = 0; i < 2; i++) {
                if (mNavbarViewInfo[i].navButtons == null) continue;
                View backKey = mNavbarViewInfo[i].navButtons.findViewById(backButtonResId);
                View recentsKey = mNavbarViewInfo[i].navButtons.findViewById(recentAppsResId);
                int backPos = mNavbarViewInfo[i].navButtons.indexOfChild(backKey);
                int recentsPos = mNavbarViewInfo[i].navButtons.indexOfChild(recentsKey);
                mNavbarViewInfo[i].navButtons.removeView(backKey);
                mNavbarViewInfo[i].navButtons.removeView(recentsKey);
                if (backPos < recentsPos) {
                    mNavbarViewInfo[i].navButtons.addView(recentsKey, backPos);
                    mNavbarViewInfo[i].navButtons.addView(backKey, recentsPos);
                } else {
                    mNavbarViewInfo[i].navButtons.addView(backKey, recentsPos);
                    mNavbarViewInfo[i].navButtons.addView(recentsKey, backPos);
                }
            }
        }
        catch (Throwable t) {
            log("Error swapping back and recents key: " + t.getMessage());
        }
    }

    private static void swapMenuAndCustom(NavbarViewInfo nvi) {
        if (!nvi.customKey.isAttachedToWindow()) return;

        try {
            final int menuButtonResId = mResources.getIdentifier("menu", "id", PACKAGE_NAME);
            View menuKey = (View) nvi.navButtons.findViewById(menuButtonResId).getParent();
            View customKey = nvi.customKey;
            int menuPos = nvi.navButtons.indexOfChild(menuKey);
            int customPos = nvi.customKeyPosition;
            nvi.navButtons.removeView(menuKey);
            nvi.navButtons.removeView(customKey);
            if (menuPos < customPos) {
                nvi.navButtons.addView(customKey, menuPos);
                nvi.navButtons.addView(menuKey, customPos);
            } else {
                nvi.navButtons.addView(menuKey, customPos);
                nvi.navButtons.addView(customKey, menuPos);
            }
            nvi.customKeyPosition = menuPos;
            nvi.menuCustomSwapped = !nvi.menuCustomSwapped;
        }
        catch (Throwable t) {
            log("Error swapping menu and custom key: " + t.getMessage());
        }
    }

    // TODO: Navbar ring targets
//    private static void setRingTargets() {
//        if (mGlowPadView == null) return;
//
//        try {
//            Context context = mGlowPadView.getContext();
//
//            final ArrayList<Object> newTargets = new ArrayList<Object>();
//            final ArrayList<String> newDescriptions = new ArrayList<String>();
//            final ArrayList<String> newDirections = new ArrayList<String>();
//
//            final int dummySlotCount = isGlowPadVertical() ? 4 : 1;
//            for (int i = 0; i < dummySlotCount; i++) {
//                newTargets.add(GlowPadHelper.createTargetDrawable(context, null, mGlowPadView.getClass()));
//                newDescriptions.add(null);
//                newDirections.add(null);
//            }
//
//            for (int i = 0; i < (12 - dummySlotCount); i++) {
//                if (i < GravityBoxSettings.PREF_KEY_NAVBAR_RING_TARGET.size()) {
//                    String app = mPrefs.getString(
//                            GravityBoxSettings.PREF_KEY_NAVBAR_RING_TARGET.get(i), null);
//                    AppInfo ai = GlowPadHelper.getAppInfo(context, app, 55, mRingTargetsBgStyle);
//                    newTargets.add(GlowPadHelper.createTargetDrawable(context, ai, mGlowPadView.getClass()));
//                    newDescriptions.add(ai == null ? null : ai.name);
//                    newDirections.add(null);
//                } else {
//                    newTargets.add(GlowPadHelper.createTargetDrawable(context, null, mGlowPadView.getClass()));
//                    newDescriptions.add(null);
//                    newDirections.add(null);
//                }
//            }
//
//            XposedHelpers.setObjectField(mGlowPadView, "mTargetDrawables", newTargets);
//            XposedHelpers.setObjectField(mGlowPadView, "mTargetDescriptions", newDescriptions);
//            XposedHelpers.setObjectField(mGlowPadView, "mDirectionDescriptions", newDirections);
//            mGlowPadView.requestLayout();
//        } catch(Throwable t) {
//            XposedBridge.log(t);
//        }
//    }

//    @SuppressWarnings("unchecked")
//    private static void updateRingTarget(int index, String app) {
//        if (mGlowPadView == null || index < 0) return;
//
//        try {
//            final Context context = mGlowPadView.getContext();
//            final ArrayList<Object> targets = 
//                    (ArrayList<Object>) XposedHelpers.getObjectField(mGlowPadView, "mTargetDrawables");
//            final ArrayList<String> descs = 
//                    (ArrayList<String>)XposedHelpers.getObjectField(mGlowPadView, "mTargetDescriptions");
//            index++; // take dummy drawable at position 0 into account
//            if (isGlowPadVertical()) {
//                if (mNavbarLeftHanded) {
//                    index -= 3;
//                    if (index < 0) index += 12;
//                } else {
//                    index += 3;
//                }
//            }
//
//            AppInfo ai = GlowPadHelper.getAppInfo(context, app, 55, mRingTargetsBgStyle);
//            if (targets != null && targets.size() > index) {
//                targets.set(index, GlowPadHelper.createTargetDrawable(context, ai, mGlowPadView.getClass()));
//                if (DEBUG) log("Ring target at index " + index + " set to: " + (ai == null ? "null" : ai.name));
//            }
//            if (descs != null && descs.size() > index) {
//                descs.set(index, ai == null ? null : ai.name);
//            }
//            mGlowPadView.requestLayout();
//        } catch(Throwable t) {
//            XposedBridge.log(t);
//        }
//    }
//
//    private static boolean isGlowPadVertical() {
//        Boolean vertical = (Boolean) XposedHelpers.getAdditionalInstanceField(mGlowPadView, "mGbVertical");
//        return (vertical != null && vertical.booleanValue());
//    }
//
//    @SuppressWarnings("unchecked")
//    private static void rotateRingTargets() {
//        try {
//            final ArrayList<Object> targets = 
//                    (ArrayList<Object>) XposedHelpers.getObjectField(mGlowPadView, "mTargetDrawables");
//            final ArrayList<String> descs = 
//                    (ArrayList<String>)XposedHelpers.getObjectField(mGlowPadView, "mTargetDescriptions");
//
//            int rotateBy = 3;
//            if (mNavbarLeftHanded) {
//                View container = (View) mGlowPadView.getParent();
//                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) container.getLayoutParams();
//                lp.addRule(RelativeLayout.ALIGN_PARENT_START, RelativeLayout.TRUE);
//                container.setLayoutParams(lp);
//                XposedHelpers.setIntField(mGlowPadView, "mGravity", Gravity.END);
//                rotateBy = mRingTargetsEnabled ? -3 : 2;
//            }
//            if (targets != null) Collections.rotate(targets, rotateBy);
//            if (descs != null) Collections.rotate(descs, rotateBy);
//            mGlowPadView.requestLayout();
//            XposedHelpers.setAdditionalInstanceField(mGlowPadView, "mGbVertical", true);
//        } catch (Throwable t) {
//            XposedBridge.log(t);
//        }
//    }
//
//    private static void setRingHapticFeedback() {
//        if (mGlowPadView == null) return;
//
//        try {
//            if (mRingVibrateDurationOrig == null) {
//                mRingVibrateDurationOrig = XposedHelpers.getIntField(mGlowPadView, "mVibrationDuration");
//                if (mRingHapticFeedback == RingHapticFeedback.DEFAULT) return;
//            }
//            int vibrateDuration;
//            switch (mRingHapticFeedback) {
//                case ENABLED: vibrateDuration = 20; break;
//                case DISABLED: vibrateDuration = 0; break;
//                default:
//                case DEFAULT: vibrateDuration = mRingVibrateDurationOrig; break;
//            }
//            XposedHelpers.setIntField(mGlowPadView, "mVibrationDuration", vibrateDuration);
//            XposedHelpers.callMethod(mGlowPadView, "setVibrateEnabled", vibrateDuration > 0);
//        } catch (Throwable t) {
//            XposedBridge.log(t);
//        }
//    }

    private static void updateCustomKeyIcon() {
        try {
            Resources res = mGbContext.getResources();
            for (NavbarViewInfo nvi : mNavbarViewInfo) {
                nvi.customKey.setImageDrawable(res.getDrawable(getCustomKeyIconId()));
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static int getCustomKeyIconId() {
        if (mUseLargerIcons) {
            return mCustomKeyAltIcon ?
                    R.drawable.ic_sysbar_apps2 : R.drawable.ic_sysbar_apps;
        } else {
            return mCustomKeyAltIcon ?
                    R.drawable.ic_sysbar_apps2_lollipop : R.drawable.ic_sysbar_apps_lollipop;
        }
    }
}
