package com.mc88.applock88;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.security.MessageDigest;

public class LockActivity extends Activity {

    private static final String PREFS = "applock";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_LAST_PKG = "last_unlocked_pkg";
    private static final String KEY_LAST_TIME = "last_unlock_time";

    private WebView webView;
    private String targetPkg;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        targetPkg = getIntent().getStringExtra("pkg");
        if(targetPkg == null) targetPkg = "";

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(Color.parseColor("#0e0e10"));

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/lock.html");

        setContentView(webView);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK){
            goToHome();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        goToHome();
    }

    private void goToHome() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
        finishAndRemoveTask();
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for(byte b : hash){
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch(Exception e){
            return "";
        }
    }

    public class Bridge {

        @JavascriptInterface
        public String getAppLabel(){
            try {
                android.content.pm.PackageManager pm = getPackageManager();
                android.content.pm.ApplicationInfo info =
                    pm.getApplicationInfo(targetPkg, 0);
                return pm.getApplicationLabel(info).toString();
            } catch(Exception e){
                return targetPkg;
            }
        }

        @JavascriptInterface
        public boolean hasPin(){
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            return sp.contains(KEY_PIN_HASH);
        }

        @JavascriptInterface
        public boolean verifyPin(String pin){
            if(pin == null) return false;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            String saved = sp.getString(KEY_PIN_HASH, "");
            if(saved.isEmpty()) return false;

            boolean ok = saved.equals(sha256(pin));
            if(ok){
                // Save grace period so the service does not reopen the lock screen
                sp.edit()
                    .putString(KEY_LAST_PKG, targetPkg)
                    .putLong(KEY_LAST_TIME, System.currentTimeMillis())
                    .apply();

                runOnUiThread(new Runnable(){
                    @Override public void run(){
                        finishAndRemoveTask();
                    }
                });
            }
            return ok;
        }

        @JavascriptInterface
        public void cancel(){
            goToHome();
        }
    }
}
