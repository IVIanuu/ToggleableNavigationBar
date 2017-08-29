package com.ivianuu.toggleablenavigationbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.view.WindowManager;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedBridge.hookAllMethods;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;
import static de.robv.android.xposed.XposedHelpers.getObjectField;
import static de.robv.android.xposed.XposedHelpers.setObjectField;

/**
 * Main xposed
 */
public class MainXposed implements IXposedHookLoadPackage {

    private static final String ACTION_SHOW_NAVIGATION_BAR = "com.ivianuu.toggleablenavigationbar.SHOW_NAVIGATION_BAR";
    private static final String ACTION_HIDE_NAVIGATION_BAR = "com.ivianuu.toggleablenavigationbar.HIDE_NAVIGATION_BAR";

    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String PHONE_STATUS_BAR = "com.android.systemui.statusbar.phone.PhoneStatusBar";

    private static boolean showNavigationBar = false;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(SYSTEM_UI)) {
            // we only care about system ui
            return;
        }

        Class<?> phoneStatusBarClass = findClass(PHONE_STATUS_BAR, lpparam.classLoader);

        // we register the receiver on start
        hookAllMethods(phoneStatusBarClass, "start", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                log("start called");
                registerReceiver(param.thisObject);
            }
        });

        // block creating navigation bar while disabled
        hookAllMethods(phoneStatusBarClass, "createNavigationBarView", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // do not allow adding navigation bar
                if (!showNavigationBar) {
                    log("block creating navigation bar");
                    param.setResult(null);
                } else {
                    log("creating navigation bar is allowed");
                }
            }
        });

        hookAllMethods(phoneStatusBarClass, "addNavigationBar", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                // do not allow adding navigation bar
                if (!showNavigationBar) {
                    log("block adding navigation bar");
                    param.setResult(null);
                } else {
                    log("adding navigation bar is allowed");
                }
            }
        });
    }

    private static void registerReceiver(final Object phoneStatusBar) {
        BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                log("intent received");
                if (intent.getAction() == null) return;

                if (intent.getAction().equals(ACTION_SHOW_NAVIGATION_BAR)) {
                    addNavigationBar(phoneStatusBar);
                } else if (intent.getAction().equals(ACTION_HIDE_NAVIGATION_BAR)) {
                    removeNavigationBar(phoneStatusBar);
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_SHOW_NAVIGATION_BAR);
        intentFilter.addAction(ACTION_HIDE_NAVIGATION_BAR);

        Context context = (Context) getObjectField(phoneStatusBar, "mContext");
        context.registerReceiver(broadcastReceiver, intentFilter);
    }

    private static void addNavigationBar(Object phoneStatusBar) {
        log("show navigation bar received");
        showNavigationBar = true;

        // If we have no Navbar view and we should have one, create it
        if (getObjectField(phoneStatusBar, "mNavigationBarView") != null) {
            log("already added");
            return;
        }

        Context mContext = (Context) getObjectField(phoneStatusBar, "mContext");

        callMethod(phoneStatusBar, "createNavigationBarView", mContext);
        callMethod(phoneStatusBar, "addNavigationBar");
    }

    private static void removeNavigationBar(Object phoneStatusBar) {
        log("hide navigation bar received");
        showNavigationBar = false;
        Object navigationBarView = getObjectField(phoneStatusBar, "mNavigationBarView");
        if (navigationBarView == null) {
            log("already hidden");
            return;
        }

        WindowManager windowManager
                = (WindowManager) getObjectField(phoneStatusBar, "mWindowManager");

        try {
            windowManager.removeView((View) navigationBarView);
        } catch (Exception e) {
            e.printStackTrace();
        }
        setObjectField(phoneStatusBar, "mNavigationBarView", null);
    }

    private static void log(String message, Object... args) {
        XposedBridge.log(String.format(message, args));
    }
}
