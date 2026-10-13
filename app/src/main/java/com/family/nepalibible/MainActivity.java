package com.family.nepalibible;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String APP_ROOT = "file:///android_asset/";

    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private int counter = 0;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // saves her place, text size and highlights
        // Security: the app only ever shows its own built-in files.
        // (file:///android_asset/ still works with these switched off.)
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(false);
        s.setTextZoom(100);                    // the app has its own big-text buttons

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Block any navigation away from the app's own pages.
                return !request.getUrl().toString().startsWith(APP_ROOT);
            }
        });
        web.addJavascriptInterface(new TtsBridge(), "AndroidTTS");

        setupTts();
        web.loadUrl(APP_ROOT + "index.html");
    }

    private void setupTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = tts.setLanguage(new Locale("ne", "NP"));
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                r = tts.setLanguage(new Locale("hi", "IN"));   // a Hindi voice can read Devanagari
            }
            ttsReady = !(r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED);
            tts.setSpeechRate(0.9f);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) { }
                @Override public void onDone(String id) { js("window.ttsDone&&window.ttsDone()"); }
                @Override @Deprecated public void onError(String id) { js("window.ttsError&&window.ttsError()"); }
                @Override public void onStop(String id, boolean interrupted) { }
            });
        });
    }

    private void js(final String code) {
        runOnUiThread(() -> web.evaluateJavascript(code, null));
    }

    private class TtsBridge {
        @JavascriptInterface
        public boolean available() { return ttsReady; }

        @JavascriptInterface
        public void speak(String text) {
            if (text == null || text.length() > 4000) return;   // only short verse text is accepted
            if (!ttsReady) { js("window.ttsError&&window.ttsError()"); return; }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), "v" + (counter++));
        }

        @JavascriptInterface
        public void stop() { if (tts != null) tts.stop(); }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        web.evaluateJavascript("window.appBack?window.appBack():false", value -> {
            if (!"true".equals(value)) finish();
        });
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
