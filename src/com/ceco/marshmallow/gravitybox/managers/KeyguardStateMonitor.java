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
package com.ceco.marshmallow.gravitybox.managers;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.XC_MethodHook;

public class KeyguardStateMonitor {
    public static final String TAG="GB:KeyguardStateMonitor";
    public static final String CLASS_KG_MONITOR =
            "com.android.systemui.statusbar.policy.KeyguardMonitor";
    private static boolean DEBUG = false;

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }

    public interface Listener {
        void onKeyguardStateChanged();
    }

    private Context mContext;
    private boolean mIsShowing;
    private boolean mIsSecured;
    private boolean mIsLocked;
    private boolean mIsTrustManaged;
    private Object mMonitor;
    private Object mUpdateMonitor;
    private List<Listener> mListeners = new ArrayList<>();

    protected KeyguardStateMonitor(Context context) {
        mContext = context;
        createHooks();
    }

    private void createHooks() {
        try {
            ClassLoader cl = mContext.getClassLoader();
            Class<?> monitorClass = XposedHelpers.findClass(CLASS_KG_MONITOR, cl);

            XposedBridge.hookAllConstructors(monitorClass, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    mMonitor = param.thisObject;
                    mUpdateMonitor = XposedHelpers.getObjectField(mMonitor, "mKeyguardUpdateMonitor");
                }
            });

            XposedHelpers.findAndHookMethod(CLASS_KG_MONITOR, cl,
                    "notifyKeyguardChanged", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                    boolean showing = XposedHelpers.getBooleanField(param.thisObject, "mShowing");
                    boolean secured = XposedHelpers.getBooleanField(param.thisObject, "mSecure");
                    boolean locked = !XposedHelpers.getBooleanField(param.thisObject, "mCanSkipBouncer");
                    boolean managed = getIsTrustManaged();
                    if (showing != mIsShowing || secured != mIsSecured || 
                            locked != mIsLocked || managed != mIsTrustManaged) {
                        mIsShowing = showing;
                        mIsSecured = secured;
                        mIsLocked = locked;
                        mIsTrustManaged = managed;
                        notifyStateChanged();
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private boolean getIsTrustManaged() {
        return (boolean) XposedHelpers.callMethod(mUpdateMonitor,
                "getUserTrustIsManaged", getCurrentUserId());
    }

    private void notifyStateChanged() {
        if (DEBUG) log("showing:" + mIsShowing + "; secured:" + mIsSecured + 
                "; locked:" + mIsLocked + "; trustManaged:" + mIsTrustManaged);
        synchronized (mListeners) {
            for (Listener l : mListeners) {
                l.onKeyguardStateChanged();
            }
        }
    }

    public void registerListener(Listener l) {
        if (l == null) return;
        synchronized (mListeners) {
            if (!mListeners.contains(l)) {
                mListeners.add(l);
            }
        }
    }

    public void unregisterListener(Listener l) {
        if (l == null) return;
        synchronized (mListeners) {
            if (mListeners.contains(l)) {
                mListeners.remove(l);
            }
        }
    }

    public int getCurrentUserId() {
        try {
            return XposedHelpers.getIntField(mMonitor, "mCurrentUser");
        } catch (Throwable t) {
            return 0;
        }
    }

    public boolean isShowing() {
        return mIsShowing;
    }

    public boolean isSecured() {
        return mIsSecured;
    }

    public boolean isLocked() {
        return mIsLocked;
    }

    public boolean isTrustManaged() {
        return mIsTrustManaged;
    }
}
