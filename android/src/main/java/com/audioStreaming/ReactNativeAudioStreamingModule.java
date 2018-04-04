package com.audioStreaming;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import javax.annotation.Nullable;
import android.app.Activity;

public class ReactNativeAudioStreamingModule extends ReactContextBaseJavaModule
    implements ServiceConnection {
    
  public static final String SHOULD_SHOW_NOTIFICATION = "showInAndroidNotifications";
  public static final String SHOULD_NOTIFICATION_PERSIST = "persistNotification";
  public static final String AUDIO_TITLE_PLACEHOLDER = "audioTitlePlaceholder";
  private ReactApplicationContext context;
    
  private Class<?> clsActivity;
  private static Signal signal;
  private Intent bindIntent;
  private boolean shouldShowNotification;
  private boolean shouldNotificationPersist = true;
  private String audioTitlePlaceholder = "";
  public ReactNativeAudioStreamingModule(ReactApplicationContext reactContext) {
    super(reactContext);
    this.context = reactContext;
  }
    
  public ReactApplicationContext getReactApplicationContextModule() {
    return this.context;
  }
    
  public Class<?> getClassActivity() {
    Activity activity = getCurrentActivity();
    if (this.clsActivity == null && activity != null) {
      this.clsActivity = activity.getClass();
    }
    return this.clsActivity;
  }
    
  public void stopOncall() {
    this.signal.stop();
  }
    
  public Signal getSignal() {
    return signal;
  }
    
  public void sendEvent(ReactContext reactContext, String eventName, @Nullable WritableMap params) {
    this.context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
        .emit(eventName, params);
  }
    
  @Override public String getName() {
    return "ReactNativeAudioStreaming";
  }
    
  @Override public void initialize() {
    super.initialize();
        
    try {
      bindIntent = new Intent(this.context, Signal.class);
      this.context.bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
    } catch (Exception e) {
      Log.e("ERROR", e.getMessage());
    }
  }
    
  @Override public void onServiceConnected(ComponentName className, IBinder service) {
    signal = ((Signal.RadioBinder) service).getService();
    signal.setData(this.context, this);
    WritableMap params = Arguments.createMap();
    sendEvent(this.getReactApplicationContextModule(), "streamingOpen", params);
  }
    
  @Override public void onServiceDisconnected(ComponentName className) {
    signal = null;
  }
    
  @ReactMethod public void play(String streamingURL, ReadableMap options) {
    this.shouldShowNotification =
        options.hasKey(SHOULD_SHOW_NOTIFICATION) && options.getBoolean(SHOULD_SHOW_NOTIFICATION);
    if (options.hasKey(SHOULD_NOTIFICATION_PERSIST)) {
      this.shouldNotificationPersist = options.getBoolean(SHOULD_NOTIFICATION_PERSIST);
    }
    if (options.hasKey(AUDIO_TITLE_PLACEHOLDER)) {
      this.audioTitlePlaceholder = options.getString(AUDIO_TITLE_PLACEHOLDER);
    }
    playInternal(streamingURL);
  }
    
  private void playInternal(String streamingURL) {
    signal.play(streamingURL);
        
    if (shouldShowNotification) {
      signal.showNotification(this.shouldNotificationPersist, this.audioTitlePlaceholder);
    }
  }
    
  @ReactMethod public void stop() {
    signal.stop();
  }
    
  @ReactMethod public void pause() {
    // Not implemented on aac
    this.stop();
  }
    
  @ReactMethod public void resume() {
    // Not implemented on aac
    signal.resume();
  }
    
  @ReactMethod public void destroyNotification() {
    signal.exitNotification();
  }
    
  @ReactMethod public void seekToTime(int seconds) {
    signal.seekTo(seconds * 1000);
  }
    
  @ReactMethod public void getStatus(Callback callback) {
    WritableMap state = Arguments.createMap();
    state.putDouble("duration", signal.getDuration());
    state.putDouble("progress", signal.getCurrentPosition());
    state.putString("status", signal != null && signal.isPlaying() ? Mode.PLAYING : Mode.STOPPED);
    state.putString("url", signal.getStreamingURL());
    callback.invoke(null, state);
  }

  @ReactMethod public void setCurrentPlaybackRate(float speed) {
    signal.setPlaybackRate(speed);
  }
}
