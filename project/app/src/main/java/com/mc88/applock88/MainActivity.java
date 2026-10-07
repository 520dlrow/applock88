package com.mc88.applock;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String PREFS = "applock";
    private static final String KEY_LOCKED = "locked_apps";
    private static final String KEY_PIN_HASH = "pin_hash";

    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        webView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setBackgroundColor(Color.parseColor("#0e0e10"));

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");

        setContentView(webView);
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for(byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch(Exception e){ return ""; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        /* Refresh service status whenever user comes back */
        if(webView != null){
            webView.post(new Runnable(){
                @Override public void run(){
                    webView.evaluateJavascript(
                        "window.onServiceCheck && window.onServiceCheck();", null);
                }
            });
        }
    }

    public class Bridge {

        @JavascriptInterface
        public String getVersion(){ return "1.0"; }

        /* Returns JSON array of {pkg, label} for all launchable apps */
        @JavascriptInterface
        public String getApps(){
            try {
                PackageManager pm = getPackageManager();
                Intent main = new Intent(Intent.ACTION_MAIN, null);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> list = pm.queryIntentActivities(main, 0);

                JSONArray arr = new JSONArray();
                Set<String> seen = new HashSet<>();

                for(ResolveInfo ri : list){
                    if(ri == null || ri.activityInfo == null) continue;
                    String pkg = ri.activityInfo.packageName;
                    if(pkg.equals(getPackageName())) continue;
                    if(seen.contains(pkg)) continue;
                    seen.add(pkg);

                    ApplicationInfo ai;
                    try { ai = pm.getApplicationInfo(pkg, 0); }
                    catch(Exception e){ continue; }

                    String label = pm.getApplicationLabel(ai).toString();

                    JSONObject o = new JSONObject();
                    o.put("pkg", pkg);
                    o.put("label", label);
                    arr.put(o);
                }

                /* Sort alphabetically by label */
                JSONArray sorted = new JSONArray();
                java.util.List<JSONObject> objs = new java.util.ArrayList<>();
                for(int i = 0; i < arr.length(); i++) objs.add(arr.getJSONObject(i));
                java.util.Collections.sort(objs, new java.util.Comparator<JSONObject>(){
                    @Override public int compare(JSONObject a, JSONObject b){
                        return a.optString("label").compareToIgnoreCase(b.optString("label"));
                    }
                });
                for(JSONObject o : objs) sorted.put(o);

                return sorted.toString();
            } catch(Exception e){
                return "[]";
            }
        }

        @JavascriptInterface
        public String getLocked(){
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            Set<String> saved = sp.getStringSet(KEY_LOCKED, new HashSet<String>());
            JSONArray arr = new JSONArray();
            for(String p : saved) arr.put(p);
            return arr.toString();
        }

        @JavascriptInterface
        public void setLocked(String pkg, boolean locked){
            if(pkg == null || pkg.isEmpty()) return;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            Set<String> current = new HashSet<>(sp.getStringSet(KEY_LOCKED, new HashSet<String>()));
            if(locked) current.add(pkg);
            else current.remove(pkg);
            sp.edit().putStringSet(KEY_LOCKED, current).apply();
        }

        @JavascriptInterface
        public boolean hasPin(){
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            return sp.contains(KEY_PIN_HASH);
        }

        @JavascriptInterface
        public void setPin(String pin){
            if(pin == null || pin.length() < 4) return;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().putString(KEY_PIN_HASH, sha256(pin)).apply();
        }

        @JavascriptInterface
        public boolean verifyPin(String pin){
            if(pin == null) return false;
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            String saved = sp.getString(KEY_PIN_HASH, "");
            return !saved.isEmpty() && saved.equals(sha256(pin));
        }

        @JavascriptInterface
        public boolean isServiceEnabled(){
            try {
                String enabled = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                if(enabled == null) return false;
                String myService = getPackageName() + "/" + AppLockService.class.getName();
                String myServiceShort = getPackageName() + "/.AppLockService";
                return enabled.contains(myService) || enabled.contains(myServiceShort);
            } catch(Exception e){
                return false;
            }
        }

        @JavascriptInterface
        public void openAccessibilitySettings(){
            try {
                Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch(Exception ignored){}
        }

        @JavascriptInterface
        public void clearAll(){
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            sp.edit().clear().apply();
        }
    }
}