package com.mc88.applock88;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppLockService extends AccessibilityService {

    private static final String PREFS = "applock";
    private static final String KEY_LOCKED = "locked_apps";
    private static final long GRACE_PERIOD_MS = 2000;

    private static final Set<String> NEVER_LOCK = new HashSet<>(Arrays.asList(
        "com.android.systemui", "com.android.settings",
        "com.google.android.packageinstaller", "com.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "android", "com.android.phone"
    ));

    private final Set<String> lockedApps = new HashSet<>();
    private String lastUnlockedPkg = "";
    private long lastUnlockTime = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        loadLockedApps();
    }

    private void loadLockedApps() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> saved = sp.getStringSet(KEY_LOCKED, new HashSet<String>());
        lockedApps.clear();
        lockedApps.addAll(saved);
        lastUnlockedPkg = sp.getString("last_unlocked_pkg", "");
        lastUnlockTime = sp.getLong("last_unlock_time", 0);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        String pkg = "";
        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq != null) pkg = pkgSeq.toString();

        // فحص بديل: إذا لم يحمل الحدث packageName
        if (pkg.isEmpty() && Build.VERSION.SDK_INT >= 21) {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows != null) {
                for (AccessibilityWindowInfo w : windows) {
                    if (w.isActive() && w.getRoot() != null) {
                        pkg = w.getRoot().getPackageName().toString();
                        break;
                    }
                }
            }
        }

        if (pkg.isEmpty()) return;
        if (pkg.equals(getPackageName())) return;
        if (NEVER_LOCK.contains(pkg)) return;

        loadLockedApps();
        if (!lockedApps.contains(pkg)) return;

        long now = System.currentTimeMillis();
        if (pkg.equals(lastUnlockedPkg) && (now - lastUnlockTime) < GRACE_PERIOD_MS) {
            return;
        }

        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra("pkg", pkg);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {}
}
