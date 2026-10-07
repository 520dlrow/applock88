package com.mc88.applock88;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AppLockService extends AccessibilityService {

    private static final String PREFS = "applock";
    private static final String KEY_LOCKED = "locked_apps";

    /* Apps we never lock — locking these would trap the user */
    private static final Set<String> NEVER_LOCK = new HashSet<>(Arrays.asList(
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "android",
        "com.android.phone"
    ));

    private final Set<String> lockedApps = new HashSet<>();
    private final Set<String> sessionUnlocked = new HashSet<>();
    private long lastUnlockTime = 0;

    /* Unlocked apps stay unlocked for this many ms after a correct PIN */
    private static final long UNLOCK_WINDOW_MS = 60 * 1000L; /* 1 minute */

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
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        /* Ignore our own package and system packages */
        if (pkg.equals(getPackageName())) return;
        if (NEVER_LOCK.contains(pkg)) return;

        /* Refresh list on every event so changes apply instantly */
        loadLockedApps();

        if (!lockedApps.contains(pkg)) return;

        /* Already unlocked in this session? */
        if (sessionUnlocked.contains(pkg)) return;

        /* Within the global unlock window? */
        long now = System.currentTimeMillis();
        if (lastUnlockTime > 0 && (now - lastUnlockTime) < UNLOCK_WINDOW_MS) return;

        /* Show the lock screen */
        Intent intent = new Intent(this, LockActivity.class);
        intent.putExtra("pkg", pkg);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
        /* Nothing to do */
    }

    /* Called by LockActivity when the PIN is correct */
    public static void markSessionUnlocked(String pkg) {
        /* Static helper not strictly needed — LockActivity handles it directly */
    }
}
