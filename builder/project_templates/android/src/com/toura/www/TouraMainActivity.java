package com.toura.www;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.net.URL;


import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.net.Uri;
import android.text.TextUtils;

import com.flurry.android.FlurryAgent;
import com.phonegap.DroidGap;
import com.toura.www.push.IntentReceiver;

public class TouraMainActivity extends DroidGap {
  private boolean isInForeground;

  public WebView getAppView() {
    return appView;
  }

  /*
   * Disable trackball navigation etc.
   */
  @Override
  public boolean onTrackballEvent(MotionEvent event) {
  	return true;
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    super.loadUrl("file:///android_asset/www/index.html");

    /* Galaxy Tab enables pinch/zoom on the entire webview by
       default, thus you can zoom everywhere. Setting this to
       true on regular phone devices seems to have no effect,
       so unfortunately we can't use it for magic pinch/zoom
       in image detail etc. Anyway, disable for sake of
       Galaxy Tab
    */
    WebSettings ws = super.appView.getSettings();
    ws.setSupportZoom(false);
    ws.setBuiltInZoomControls(false);
    IntentReceiver.setTouraMainActivity(this);

    Resources resources = this.getResources();
    AssetManager assetManager = resources.getAssets();

    try {
        InputStream inputStream = assetManager.open("touraconfig.properties");
        Properties properties = new Properties();
        properties.load(inputStream);
        boolean ads = Boolean.parseBoolean(properties.getProperty("ads"));

        if (ads) {
          super.appView.setWebViewClient(new DroidGap.GapViewClient(this) {
            @Override
            public void onLoadResource (WebView view, String url) {

              if (
                  !url.contains("http://127.0.0.1") &&
                  !url.contains("file://") &&
                  !url.contains("mwhenry.com") &&
                  !url.contains("s3.amazonaws.com")
                  ) {
                      view.getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                      view.stopLoading();

                /* TODO: figure out a way to achieve this using childbrowser instead of kicking out to
                   Android's browser. Something like:
                   com.phonegap.plugins.childBrowser.ChildBrowser cb = new com.phonegap.plugins.childBrowser.ChildBrowser();
                   cb.showWebPage(url, true); */
              }
            }
          });
        }

    } catch (IOException e) {
        Log.d("Toura", "Failed to open property file");
        e.printStackTrace();
    }
  }

  /*
   * onStart added for Flurry
   */
  @Override
  public void onStart() {
    super.onStart();
    Resources resources = this.getResources();
    AssetManager assetManager = resources.getAssets();
    InputStream inputStream = null;
    try {
        inputStream = assetManager.open("touraconfig.properties");
        Properties properties = new Properties();
        properties.load(inputStream);
        FlurryAgent.onStartSession(this, properties.getProperty("flurryApiKey"));
    } catch (IOException e) {
        System.err.println("Failed to open touraconfig.properties file");
        e.printStackTrace();
    } finally {
      if (inputStream != null) {
        try {
          inputStream.close();
        } catch (IOException e) {}
      }
    }
  }

  /*
   * onStop added for Flurry
   */
  @Override
  public void onStop() {
    super.onStop();
    FlurryAgent.onEndSession(this);
  }

  /*
   * Log something with flurry
   */
  public void logEvent(String eventId,  Map<String, String> parameters) {
    FlurryAgent.logEvent( eventId, parameters);
  }

  public boolean isInForeground() {
      return isInForeground;
  }

  public void showAlert(String message) {
    appView.loadUrl("javascript: " + createShowAlertScript(message));
  }

  @Override
  protected void onPause() {
      super.onPause();
      isInForeground = false;
  }

  @Override
  protected void onResume() {
      super.onResume();
      isInForeground = true;
      Intent intent = getIntent();
      if (intent.hasExtra("alert")) {
        String url = "javascript:document.addEventListener('deviceready', function() {dojo.subscribe('/app/started', function() { " + createShowAlertScript(intent.getStringExtra("alert")) + " });}, false);";
        Log.i(TouraApplication.LOG_TAG, "Showing alert in TouraMainActivity.onResume()! url: " + url);
        appView.loadUrl(url);
      }
  }

  private String createShowAlertScript(String message) {
    return "toura.app.Notifications.notify({alert:'" + message.replace("'", "\\'") + "'});";
  }
}
