package com.audioStreaming;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.TaskStackBuilder;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import com.facebook.infer.annotation.Assertions;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataRenderer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.io.IOException;

public class Signal extends Service implements ExoPlayer.EventListener, MetadataRenderer.Output, ExtractorMediaSource.EventListener {
    private static final String TAG = "ReactNative";

    // Notification
    private Class<?> clsActivity;
    private static final int NOTIFY_ME_ID = 696969;
    private Notification.Builder notifyBuilder;
    private NotificationManager notifyManager = null;
    private Notification foregroundNotification;
    public static RemoteViews remoteViews;

    // Player
    private SimpleExoPlayer player = null;

    public static final String BROADCAST_PLAYBACK_STOP = "stop",
            BROADCAST_PLAYBACK_PLAY = "pause",
            BROADCAST_EXIT = "exit",
            BROADCAST_DISMISSED = "dismiss";

    private final IBinder binder = new RadioBinder();
    private final SignalReceiver receiver = new SignalReceiver(this);
    private Context context;
    private String streamingURL;
    private EventsReceiver eventsReceiver;
    private ReactNativeAudioStreamingModule module;

    private TelephonyManager phoneManager;
    private PhoneListener phoneStateListener;

    @Override
    public void onCreate() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_PLAYBACK_STOP);
        intentFilter.addAction(BROADCAST_PLAYBACK_PLAY);
        intentFilter.addAction(BROADCAST_DISMISSED);
        intentFilter.addAction(BROADCAST_EXIT);
        registerReceiver(this.receiver, intentFilter);
    }
    
    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

    }

    @Override
    public void     onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

    }
    
    public void setData(Context context, ReactNativeAudioStreamingModule module) {
        this.context = context;
        this.clsActivity = module.getClassActivity();
        this.module = module;
        
        this.eventsReceiver = new EventsReceiver(this.module);
        
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CREATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.IDLE));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.DESTROYED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STARTED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CONNECTING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PLAYING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.READY));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STOPPED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PAUSED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.COMPLETED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ERROR));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_START));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_END));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.METADATA_UPDATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ALBUM_UPDATED));

        this.phoneStateListener = new PhoneListener(this.module);
        this.phoneManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        if (this.phoneManager != null) {
            this.phoneManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
        }
    }

    public void onLoadingChanged(boolean isLoading) {


    }

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
        Log.d("onPlayerStateChanged", ""+playbackState);

        switch (playbackState) {
            case ExoPlayer.STATE_IDLE:
                sendBroadcast(new Intent(Mode.IDLE));
                break;
            case ExoPlayer.STATE_BUFFERING:

                sendBroadcast(new Intent(Mode.BUFFERING_START));
                break;
            case ExoPlayer.STATE_READY:
                if (this.player != null && this.player.getPlayWhenReady()) {
                    sendBroadcast(new Intent(Mode.PLAYING));
                } else {
                    sendBroadcast(new Intent(Mode.READY));
                }
                break;
            case ExoPlayer.STATE_ENDED:
                sendBroadcast(new Intent(Mode.STOPPED));
                break;
        }
    }

    @Override
    public void onTimelineChanged(Timeline timeline, Object manifest) {}
    
    
    @Override
    public void onPlayerError(ExoPlaybackException error) {
        Log.d(TAG, error.getMessage());
        sendBroadcast(new Intent(Mode.ERROR));
    }
    @Override
    public void onPositionDiscontinuity() {

    }

    private void playingView() {
        if (remoteViews != null && notifyBuilder != null) {
           // remoteViews.setTextViewText(R.id.player_status_text, "Streaming");
            remoteViews.setViewVisibility(R.id.btn_streaming_notification_play, View.GONE);
            remoteViews.setViewVisibility(R.id.btn_streaming_notification_pause, View.VISIBLE);

            foregroundNotification = notifyBuilder.build();
            foregroundNotification.bigContentView = remoteViews;
            notifyManager.notify(NOTIFY_ME_ID, foregroundNotification);
        }

    }

    private void pausedView() {
        if (remoteViews != null && notifyBuilder != null) {
            //remoteViews.setTextViewText(R.id.player_status_text, "Paused");
            remoteViews.setViewVisibility(R.id.btn_streaming_notification_play, View.VISIBLE);
            remoteViews.setViewVisibility(R.id.btn_streaming_notification_pause, View.GONE);
            foregroundNotification = notifyBuilder.build();
            foregroundNotification.bigContentView = remoteViews;
            notifyManager.notify(NOTIFY_ME_ID, foregroundNotification);
        }

    }

    private static String getDefaultUserAgent() {
        StringBuilder result = new StringBuilder(64);
        result.append("Dalvik/");
        result.append(System.getProperty("java.vm.version")); // such as 1.1.0
        result.append(" (Linux; U; Android ");

        String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
        result.append(version.length() > 0 ? version : "1.0");

        // add the model for the release build
        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }
        String id = Build.ID; // "MASTER" or "M4-rc20"
        if (id.length() > 0) {
            result.append(" Build/");
            result.append(id);
        }
        result.append(")");
        return result.toString();
    }
    
    /**
     *  Player controls
     */
    
    public void play(String url) {
        if (player != null ) {
            player.setPlayWhenReady(false);
            player.stop();
            player.seekTo(0);
        }
        
        boolean playWhenReady = true; // TODO Allow user to customize this
        this.streamingURL = url;
        
        // Create player
        Handler mainHandler = new Handler();
        TrackSelector trackSelector = new DefaultTrackSelector();
        LoadControl loadControl = new DefaultLoadControl();
        this.player = ExoPlayerFactory.newSimpleInstance(this.getApplicationContext(), trackSelector, loadControl);
        
        // Create source
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this.getApplication(), getDefaultUserAgent(), bandwidthMeter);
        MediaSource audioSource = new ExtractorMediaSource(Uri.parse(this.streamingURL), dataSourceFactory, extractorsFactory, mainHandler, this);
        
        // Start preparing audio
        player.prepare(audioSource);
        player.addListener(this);
        player.setPlayWhenReady(playWhenReady);
        this.playingView();
    }
    
    public void start() {
        // Assertions.assertNotNull(player);
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }
    
    public void pause() {
        // Assertions.assertNotNull(player);
        if (player != null) {
            player.setPlayWhenReady(false);
            sendBroadcast(new Intent(Mode.STOPPED));
            this.pausedView();
        }
    }
    
    public void resume() {
        // Assertions.assertNotNull(player);
        if (player != null) {
            player.setPlayWhenReady(true);
            this.playingView();
        }
    }
    
    public void stop() {
        // Assertions.assertNotNull(player);
        if (player != null) {
            player.setPlayWhenReady(false);
            sendBroadcast(new Intent(Mode.STOPPED));
            this.pausedView();
        }

    }
    
    public boolean isPlaying() {
        //Assertions.assertNotNull(player);
        return player != null && player.getPlayWhenReady() && player.getPlaybackState() != ExoPlayer.STATE_ENDED;
    }
    
    public long getDuration() {
        //Assertions.assertNotNull(player);
        return player != null ? player.getDuration() : new Long(0);
    }
    
    public long getCurrentPosition() {
        //Assertions.assertNotNull(player);
        return player != null ? player.getCurrentPosition() : new Long(0);
    }
    
    public int getBufferPercentage() {
        Assertions.assertNotNull(player);
        return player.getBufferedPercentage();
    }
    
    public void seekTo(long timeMillis) {
        Assertions.assertNotNull(player);
        player.seekTo(timeMillis);
    }
    
    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return netInfo != null && netInfo.isConnectedOrConnecting();
    }

    public void setPlaybackRate(float speed) {
        PlaybackParameters pp = new PlaybackParameters(speed, 1);
        player.setPlaybackParameters(pp);
    }
    
    /**
     *  Meta data information
     */
    
    @Override
    public void onMetadata(Metadata metadata) {

    }
    
    /**
     *  Notification control
     */
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }
    
    // Notification
    private PendingIntent makePendingIntent(String broadcast) {
        Intent intent = new Intent(broadcast);
        return PendingIntent.getBroadcast(this.context, 0, intent, 0);
    }
    
    public NotificationManager getNotifyManager() {
        return notifyManager;
    }
    
    public class RadioBinder extends Binder {
        public Signal getService() {
            return Signal.this;
        }
    }

    public String getAppTitle() {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        String title = stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
        return title;
    }

    public void showNotification(Boolean persist, String title) {
        if (this.clsActivity == null) { 
            this.clsActivity = this.module.getClassActivity();
        }
        Resources res = context.getResources();
        String packageName = context.getPackageName();
        // need drawable because mipmap make android oreo system crashed
        int smallIconResId = res.getIdentifier("ic_notification", "drawable", packageName);
        int largeIconResId = res.getIdentifier("ic_launcher", "drawable", packageName);
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);
        remoteViews = new RemoteViews(packageName, R.layout.streaming_notification_player);
        notifyBuilder = new Notification.Builder(this.context)
            .setContent(remoteViews)
            .setSmallIcon(smallIconResId == 0 ? largeIconResId : smallIconResId)
            .setLargeIcon(largeIconBitmap)
            .setContentTitle(this.getAppTitle())
            .setContentText("Playing an audio file")
            .setOngoing(persist);

        Intent resultIntent = new Intent(this.context, this.clsActivity);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this.context);
        stackBuilder.addParentStack(this.clsActivity);
        stackBuilder.addNextIntent(resultIntent);
        
        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
                                                                          PendingIntent.FLAG_UPDATE_CURRENT);
        
        notifyBuilder.setContentIntent(resultPendingIntent);
        
        remoteViews.setTextViewText(R.id.title, this.getAppTitle());
        remoteViews.setTextViewText(R.id.subtitle, "Playing an audio file");
        remoteViews.setImageViewResource(R.id.streaming_icon, largeIconResId);
        remoteViews.setOnClickPendingIntent(R.id.btn_streaming_notification_play, makePendingIntent(BROADCAST_PLAYBACK_PLAY));
        remoteViews.setOnClickPendingIntent(R.id.btn_streaming_notification_pause, makePendingIntent(BROADCAST_PLAYBACK_PLAY));
        remoteViews.setOnClickPendingIntent(R.id.btn_streaming_notification_stop, makePendingIntent(BROADCAST_EXIT));
        remoteViews.setTextViewText(R.id.song_name_notification, title);
        notifyBuilder.setDeleteIntent(makePendingIntent(BROADCAST_DISMISSED));
        notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel("com.audioStreaming", "Audio Streaming",
                            NotificationManager.IMPORTANCE_HIGH);
            if (notifyManager != null) {
                notifyManager.createNotificationChannel(channel);
            }

            notifyBuilder.setChannelId("com.audioStreaming");
            notifyBuilder.setOnlyAlertOnce(true);
            
        }
        Notification foregroundNotification;
        foregroundNotification = notifyBuilder.build();
        foregroundNotification.bigContentView = remoteViews;
        notifyManager.notify(NOTIFY_ME_ID, foregroundNotification);
        this.playingView();
    }
    
    public void clearNotification() {
        if (notifyManager != null) {
            notifyManager.cancel(NOTIFY_ME_ID);
        }
    }
    
    public void exitNotification() {
        notifyManager.cancelAll();
        clearNotification();
        notifyBuilder = null;
        notifyManager = null;
    }

    public String getStreamingURL() {
        return this.streamingURL;
    }

    @Override
    public void onLoadError(IOException error) {
        Log.e(TAG, error.getMessage());
    }
}
