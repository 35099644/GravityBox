/*
 * Copyright (C) 2014 Peter Gregus for GravityBox Project (C3C076@xda)
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

package com.ceco.gm2.gravitybox;

import com.ceco.gm2.gravitybox.ledcontrol.LedSettings;
import com.ceco.gm2.gravitybox.ledcontrol.LedSettings.LedMode;
import com.ceco.gm2.gravitybox.ledcontrol.QuietHours;
import com.ceco.gm2.gravitybox.ledcontrol.QuietHoursActivity;

import android.app.KeyguardManager;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class ModLedControl {
    private static final String TAG = "GB:ModLedControl";
    public static final boolean DEBUG = false;
    private static final String CLASS_NOTIFICATION_MANAGER_SERVICE = "com.android.server.NotificationManagerService";
    private static final String CLASS_STATUSBAR_MGR_SERVICE = "com.android.server.StatusBarManagerService";
    private static final String PACKAGE_NAME_GRAVITYBOX = "com.ceco.gm2.gravitybox";

    private static XSharedPreferences mPrefs;
    private static Context mContext;
    private static Handler mHandler;
    private static PowerManager mPm;
    private static SensorManager mSm;
    private static KeyguardManager mKm;
    private static Sensor mProxSensor;
    private static boolean mProxSensorListenerRegistered;
    private static boolean mScreenCovered;
    private static boolean mOnPanelRevealedBlocked;
    private static QuietHours mQuietHours;

    private static SensorEventListener mProxSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) { 
            mScreenCovered = event.values[0] == 0;
            if (DEBUG) log("Screen covered: " + mScreenCovered);
        }
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private static BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(LedSettings.ACTION_UNC_SETTINGS_CHANGED) ||
                    action.equals(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED)) {
                mPrefs.reload();
                mQuietHours = new QuietHours(mPrefs);
                if (intent.hasExtra(LedSettings.EXTRA_UNC_AS_ENABLED)) {
                    toggleActiveScreenFeature(intent.getBooleanExtra(
                            LedSettings.EXTRA_UNC_AS_ENABLED, false));
                }
            }
            if (action.equals(Intent.ACTION_USER_PRESENT)) {
                if (DEBUG) log("User present");
                mOnPanelRevealedBlocked = false;
                if (mProxSensorListenerRegistered && mSm != null && mProxSensor != null) {
                    mSm.unregisterListener(mProxSensorEventListener, mProxSensor);
                    mProxSensorListenerRegistered = false;
                    if (DEBUG) log("Prox sensor listener unregistered");
                }
            }
            if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                if (!mProxSensorListenerRegistered && mSm != null && mProxSensor != null) {
                    mSm.registerListener(mProxSensorEventListener, mProxSensor, SensorManager.SENSOR_DELAY_NORMAL);
                    mProxSensorListenerRegistered = true;
                    if (DEBUG) log("Prox sensor listener registered");
                }
            }
        }
    };

    public static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void initZygote() {
        mPrefs = new XSharedPreferences(GravityBox.PACKAGE_NAME, "ledcontrol");
        mPrefs.makeWorldReadable();
        mQuietHours = new QuietHours(mPrefs);

        try {
            final Class<?> nmsClass = XposedHelpers.findClass(CLASS_NOTIFICATION_MANAGER_SERVICE, null);
            XposedBridge.hookAllConstructors(nmsClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mContext == null) {
                        mContext = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                        mHandler = (Handler) XposedHelpers.getObjectField(param.thisObject, "mHandler");

                        IntentFilter intentFilter = new IntentFilter();
                        intentFilter.addAction(LedSettings.ACTION_UNC_SETTINGS_CHANGED);
                        intentFilter.addAction(Intent.ACTION_USER_PRESENT);
                        intentFilter.addAction(QuietHoursActivity.ACTION_QUIET_HOURS_CHANGED);
                        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
                        mContext.registerReceiver(mBroadcastReceiver, intentFilter);

                        toggleActiveScreenFeature(!mPrefs.getBoolean(LedSettings.PREF_KEY_LOCKED, false) && 
                                mPrefs.getBoolean(LedSettings.PREF_KEY_ACTIVE_SCREEN_ENABLED, false));
                        if (DEBUG) log("Notification manager service initialized");
                    }
                }
            });

            switch (Build.VERSION.SDK_INT) {
                case 16:
                    XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, null,
                            "enqueueNotificationWithTag", String.class, String.class, int.class,
                            Notification.class, int[].class, notifyHook);
                    break;
                case 17:
                    XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, null,
                            "enqueueNotificationWithTag", String.class, String.class, int.class,
                            Notification.class, int[].class, int.class, notifyHook);
                    break;
                case 18:
                    XposedHelpers.findAndHookMethod(CLASS_NOTIFICATION_MANAGER_SERVICE, null,
                            "enqueueNotificationWithTag", String.class, String.class, String.class, int.class,
                            Notification.class, int[].class, int.class, notifyHook);
                    break;
            }

            XposedHelpers.findAndHookMethod(CLASS_STATUSBAR_MGR_SERVICE, null, "onPanelRevealed", 
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
                    if (mOnPanelRevealedBlocked) {
                        param.setResult(null);
                        if (DEBUG) log("onPanelRevealed blocked");
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private static XC_MethodHook notifyHook = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(final MethodHookParam param) throws Throwable {
            try {
                if (mPrefs.getBoolean(LedSettings.PREF_KEY_LOCKED, false)) {
                    if (DEBUG) log("Ultimate notification control feature locked.");
                    return;
                }

                final int id = (Integer) param.args[Build.VERSION.SDK_INT > 17 ? 3 : 2];
                final String pkgName = (String) param.args[0];
                Notification n = (Notification) param.args[Build.VERSION.SDK_INT > 17 ? 4 : 3];

                if (pkgName.equals(PACKAGE_NAME_GRAVITYBOX) && id >= 2049) return;

                LedSettings ls = LedSettings.deserialize(mPrefs.getStringSet(pkgName, null));
                if (!ls.getEnabled()) {
                    // use default settings in case they are active
                    ls = LedSettings.deserialize(mPrefs.getStringSet("default", null));
                    if (!ls.getEnabled() && !mQuietHours.quietHoursActive(ls, n)) {
                        return;
                    }
                }
                if (DEBUG) log(pkgName + ": " + ls.toString());

                final boolean qhActive = mQuietHours.quietHoursActive(ls, n);
                final boolean qhActiveIncludingLed = mQuietHours.quietHoursActiveIncludingLED(ls, n);

                if (((n.flags & Notification.FLAG_ONGOING_EVENT) == Notification.FLAG_ONGOING_EVENT) &&
                        !ls.getOngoing() && !qhActive) {
                    if (DEBUG) log("Ongoing led control disabled. Ignoring.");
                    return;
                }

                // lights
                if (qhActiveIncludingLed || 
                        (ls.getEnabled() && ls.getLedMode() == LedMode.OFF)) {
                    n.defaults &= ~Notification.DEFAULT_LIGHTS;
                    n.flags &= ~Notification.FLAG_SHOW_LIGHTS;
                } else if (ls.getEnabled() && ls.getLedMode() == LedMode.OVERRIDE) {
                    n.defaults &= ~Notification.DEFAULT_LIGHTS;
                    n.flags |= Notification.FLAG_SHOW_LIGHTS;
                    n.ledOnMS = ls.getLedOnMs();
                    n.ledOffMS = ls.getLedOffMs();
                    n.ledARGB = ls.getColor();
                }

                // sound
                if (qhActive) {
                    n.defaults &= ~Notification.DEFAULT_SOUND;
                    n.sound = null;
                    n.flags &= ~Notification.FLAG_INSISTENT;
                } else {
                    if (ls.getSoundOverride()) {
                        n.defaults &= ~Notification.DEFAULT_SOUND;
                        n.sound = ls.getSoundUri();
                    }
                    if (ls.getSoundOnlyOnce()) {
                        n.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
                    } else {
                        n.flags &= ~Notification.FLAG_ONLY_ALERT_ONCE;
                    }
                    if (ls.getInsistent()) {
                        n.flags |= Notification.FLAG_INSISTENT;
                    } else {
                        n.flags &= ~Notification.FLAG_INSISTENT;
                    }
                }

                // vibration
                if (qhActive) {
                    n.defaults &= ~Notification.DEFAULT_VIBRATE;
                    n.vibrate = new long[] {0};
                } else if (ls.getVibrateOverride() && ls.getVibratePattern() != null) {
                    n.defaults &= ~Notification.DEFAULT_VIBRATE;
                    n.vibrate = ls.getVibratePattern();
                }

                if (DEBUG) log("Notification info: defaults=" + n.defaults + "; flags=" + n.flags);
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }

        @Override
        protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
            try {
                if (mPm != null && !mPm.isScreenOn() && !mScreenCovered && mKm.isKeyguardLocked()) {
                    final String pkgName = (String) param.args[0];
                    LedSettings ls = LedSettings.deserialize(mPrefs.getStringSet(pkgName, null));
                    if (!ls.getEnabled()) {
                        // use default settings in case they are active
                        ls = LedSettings.deserialize(mPrefs.getStringSet("default", null));
                        if (!ls.getEnabled()) {
                            return;
                        }
                    }
                    if (!ls.getActiveScreenEnabled()) return;

                    Notification n = (Notification) param.args[Build.VERSION.SDK_INT > 17 ? 4 : 3];
                    if (mQuietHours.quietHoursActive(ls, n)) {
                        return;
                    }

                    if (((n.flags & Notification.FLAG_ONGOING_EVENT) == Notification.FLAG_ONGOING_EVENT) &&
                            !ls.getOngoing()) {
                        if (DEBUG) log("Ongoing led control disabled. Ignoring.");
                        return;
                    }

                    if (DEBUG) log("Performing Active Screen for " + pkgName);
                    final LedSettings fls = ls;
                    mOnPanelRevealedBlocked = fls.getActiveScreenExpanded();
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (fls.getActiveScreenExpanded()) {
                                mContext.sendBroadcast(new Intent(ModHwKeys.ACTION_EXPAND_NOTIFICATIONS));
                            }
                            final WakeLock wl = mPm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                                    PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE, TAG);
                            wl.acquire();
                            wl.release();
                        }
                    }, 1000);
                }
            } catch (Throwable t) {
                XposedBridge.log(t);
            }
        }
    };

    private static void toggleActiveScreenFeature(boolean enable) {
        try {
            if (enable && mContext != null) {
                mScreenCovered = false;
                mPm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
                mKm = (KeyguardManager) mContext.getSystemService(Context.KEYGUARD_SERVICE);
                mSm = (SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE);
                mProxSensor = mSm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            } else {
                mProxSensor = null;
                mSm = null;
                mPm = null;
                mKm = null;
            }
            if (DEBUG) log("Active screen feature: " + enable);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }
}
