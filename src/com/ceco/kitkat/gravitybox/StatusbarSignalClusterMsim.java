package com.ceco.kitkat.gravitybox;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import com.ceco.kitkat.gravitybox.StatusBarIconManager.ColorInfo;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

public class StatusbarSignalClusterMsim extends StatusbarSignalCluster {
    protected static String[] sMobileViewNames = new String[] { "mMobileStrengthView", "mMobileStrengthView2" };
    protected static String[] sMobileTypeViewNames = new String[] { "mMobileTypeView", "mMobileTypeView2" };

    protected Object mNetworkControllerCallback2;
    protected SignalActivity[] mMobileActivity;

    public StatusbarSignalClusterMsim(LinearLayout view, StatusBarIconManager iconManager) {
        super(view, iconManager);
    }

    @Override
    protected void initPreferences() {
        super.initPreferences();
        mConnectionStateEnabled = false;
    }

    @Override
    protected void createHooks() {
        try {
            XposedHelpers.findAndHookMethod(mView.getClass(), "applySubscription", 
                    int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    apply((Integer) param.args[0]);
                }
            });

            if (mDataActivityEnabled) {
                try {
                    final ClassLoader classLoader = mView.getContext().getClassLoader();
                    final Class<?> networkCtrlClass = XposedHelpers.findClass(
                            "com.android.systemui.statusbar.policy.MSimNetworkController", classLoader);
                    final Class<?> networkCtrlCbClass = XposedHelpers.findClass(
                            "com.android.systemui.statusbar.policy.MSimNetworkController.MSimNetworkSignalChangedCallback", 
                                classLoader);
                    XposedHelpers.findAndHookMethod(mView.getClass(), "setNetworkController",
                            networkCtrlClass, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            mNetworkControllerCallback = Proxy.newProxyInstance(classLoader, 
                                new Class<?>[] { networkCtrlCbClass }, new NetworkControllerCallbackMsim(param.args[0]));
                            XposedHelpers.callMethod(param.args[0], "addAddNetworkSignalChangedCallback",
                                    mNetworkControllerCallback, 0);
                            mNetworkControllerCallback2 = Proxy.newProxyInstance(classLoader, 
                                    new Class<?>[] { networkCtrlCbClass }, new NetworkControllerCallbackMsim(param.args[0]));
                                XposedHelpers.callMethod(param.args[0], "addAddNetworkSignalChangedCallback",
                                        mNetworkControllerCallback, 1);
                            if (DEBUG) log("setNetworkController: callback registered");
                        }
                    });

                    XposedHelpers.findAndHookMethod(mView.getClass(), "onAttachedToWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            View v = (View) XposedHelpers.getObjectField(mView, "mWifiStrengthView");
                            if (v != null && v.getParent() instanceof FrameLayout) {
                                mWifiActivity = new SignalActivity((FrameLayout)v.getParent(), SignalType.WIFI);
                                if (DEBUG) log("onAttachedToWindow: mWifiActivity created");
                            }

                            if (mMobileActivity == null) {
                                mMobileActivity = new SignalActivity[2];
                            }
                            v = (View) XposedHelpers.getObjectField(mView, "mMobileStrengthView");
                            if (v != null && v.getParent() instanceof FrameLayout) {
                                mMobileActivity[0] = new SignalActivity((FrameLayout)v.getParent(), SignalType.MOBILE,
                                        Gravity.BOTTOM | Gravity.RIGHT);
                                if (DEBUG) log("onAttachedToWindow: mMobileActivity created");
                            }

                            v = (View) XposedHelpers.getObjectField(mView, "mMobileStrengthView2");
                            if (v != null && v.getParent() instanceof FrameLayout) {
                                mMobileActivity[1] = new SignalActivity((FrameLayout)v.getParent(), SignalType.MOBILE,
                                        Gravity.BOTTOM | Gravity.RIGHT);
                                if (DEBUG) log("onAttachedToWindow: mMobileActivity2 created");
                            }
                        }
                    });

                    XposedHelpers.findAndHookMethod(mView.getClass(), "onDetachedFromWindow", new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            mWifiActivity = null;
                            if (mMobileActivity != null) {
                                mMobileActivity[0] = mMobileActivity[1] = null;
                            }
                            if (DEBUG) log("onDetachedFromWindow: signal activities destoyed");
                        }
                    });
                } catch (Throwable t) {
                    log("Error hooking SignalActivity related methods: " + t.getMessage());
                }
            }

        } catch (Throwable t) {
            log("Error hooking apply() method: " + t.getMessage());
        }
    }

    @Override
    protected void update() {
        if (mView != null) {
            try {
                XposedHelpers.callMethod(mView, "applySubscription", 0);
                XposedHelpers.callMethod(mView, "applySubscription", 1);
            } catch (Throwable t) {
                logAndMute("invokeApply", t);
            }
        }
    }

    @Override
    protected void updateMobileIcon(int simSlot) {
        try {
            boolean mobileVisible = ((boolean[])XposedHelpers.getObjectField(mView, "mMobileVisible"))[simSlot];
            if (DEBUG) log("Mobile visible for slot " + simSlot + ": " + mobileVisible);
            if (mobileVisible &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView mobile = (ImageView) XposedHelpers.getObjectField(mView, sMobileViewNames[simSlot]);
                if (mobile != null) {
                    int resId = ((int[])XposedHelpers.getObjectField(mView, "mMobileStrengthIconId"))[simSlot];
                    Drawable d = mIconManager.getMobileIcon(resId, true);
                    if (d != null) mobile.setImageDrawable(d);
                }
                if (mIconManager.isMobileIconChangeAllowed()) {
                    ImageView mobileType = (ImageView) XposedHelpers.getObjectField(mView, sMobileTypeViewNames[simSlot]);
                    if (mobileType != null) {
                        try {
                            int resId = ((int[])XposedHelpers.getObjectField(mView, "mMobileTypeIconId"))[simSlot];
                            Drawable d = mResources.getDrawable(resId).mutate();
                            d = mIconManager.applyColorFilter(d);
                            mobileType.setImageDrawable(d);
                        } catch (Resources.NotFoundException e) { 
                            mobileType.setImageDrawable(null);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logAndMute("updateMobileIcon", t);
        }
    }

    @Override
    protected void updateWiFiIcon() {
        try {
            if (XposedHelpers.getBooleanField(mView, "mWifiVisible") &&
                    mIconManager.getSignalIconMode() != StatusBarIconManager.SI_MODE_DISABLED) {
                ImageView wifiIcon = (ImageView) XposedHelpers.getObjectField(mView, "mWifiStrengthView");
                if (wifiIcon != null) {
                    int resId = XposedHelpers.getIntField(mView, "mWifiStrengthIconId");
                    Drawable d = mIconManager.getWifiIcon(resId, true);
                    if (d != null) wifiIcon.setImageDrawable(d);
                }
            }
        } catch (Throwable t) {
            logAndMute("updateWiFiIcon", t);
        }
    }

    @Override
    public void onIconManagerStatusChanged(int flags, ColorInfo colorInfo) {
        super.onIconManagerStatusChanged(flags, colorInfo);

        if ((flags & StatusBarIconManager.FLAG_DATA_ACTIVITY_COLOR_CHANGED) != 0 &&
                    mDataActivityEnabled && mMobileActivity != null) {
            if (mMobileActivity[0] != null) {
                mMobileActivity[0].updateDataActivityColor();
            }
            if (mMobileActivity[1] != null) {
                mMobileActivity[1].updateDataActivityColor();
            }
        }
    }

    protected class NetworkControllerCallbackMsim implements InvocationHandler {
        private Object mController;

        public NetworkControllerCallbackMsim(Object controller) {
            mController = controller;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String methodName = method.getName();
            try {
                if (methodName.equals("onWifiSignalChanged")) {
                    if (mWifiActivity != null) {
                        mWifiActivity.update((Boolean)args[0], true, 
                                (Boolean)args[3], (Boolean)args[4]);
                    }
                } else if (methodName.equals("onMobileDataSignalChanged")) {
                    if (mMobileActivity != null) {
                        int slot = PhoneWrapper.getMsimPreferredDataSubscription();
                        boolean in = ((boolean[]) XposedHelpers.getObjectField(mController, "mMSimDataActivityIn"))[slot];
                        boolean out = ((boolean[]) XposedHelpers.getObjectField(mController, "mMSimDataActivityOut"))[slot];
                        if (DEBUG) log("NetworkControllerCallbackMsim: onMobileDataSignalChanged " + 
                                slot + "; enabled:" + args[0] + "; in:" + in + "; out:" + out);
                        if (mMobileActivity[slot] != null) {
                            mMobileActivity[slot].update((Boolean)args[0], true, in, out);
                        }
                    }
                }
            } catch (Throwable t) {
                logAndMute("NetworkControllerCallbackMsim", t);
            }

            return null;
        }
    }
}
