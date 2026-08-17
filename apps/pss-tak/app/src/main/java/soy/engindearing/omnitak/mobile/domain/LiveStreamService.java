package soy.engindearing.omnitak.mobile.domain;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.sources.audio.MicrophoneSource;
import com.pedro.encoder.input.sources.video.Camera2Source;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.library.rtsp.RtspStream;
import com.pedro.library.util.streamclient.RtspStreamClient;
import com.pedro.library.view.OpenGlView;
import com.pedro.rtsp.rtsp.Protocol;

import soy.engindearing.omnitak.mobile.MainActivity;
import soy.engindearing.omnitak.mobile.OmniTAKApp;
import soy.engindearing.omnitak.mobile.R;

/** In-process RTSP publisher (OpenTAK ICU / RootEncoder), Java so Kotlin 2.0 can still compile. */
public class LiveStreamService extends Service implements ConnectChecker {
    private static final String TAG = "LiveStream";
    private static final String CHANNEL_ID = "psr_live_stream";
    private static final int NOTIFY_ID = 1102;

    public class LocalBinder extends Binder {
        public LiveStreamService service() {
            return LiveStreamService.this;
        }
    }

    private final IBinder binder = new LocalBinder();
    @Nullable private RtspStream stream;
    @Nullable private OpenGlView preview;
    @Nullable private String lastHost;
    private int lastPort = 8554;
    @Nullable private String lastPath;

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFY_ID, buildNotification("Трансляция"), fgsTypes());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        try {
            stopPublish();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    public void attachPreview(@NonNull FrameLayout host) {
        if (preview == null) {
            preview = new OpenGlView(this);
            host.removeAllViews();
            host.addView(preview, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        RtspStream s = ensureStream();
        if (!s.isOnPreview()) {
            try {
                s.startPreview(preview, true);
            } catch (Exception e) {
                Log.w(TAG, "preview: " + e.getMessage());
            }
        }
    }

    public void detachPreview() {
        if (stream != null && stream.isOnPreview()) {
            try {
                stream.stopPreview();
            } catch (Exception ignored) {
            }
        }
    }

    public void switchCamera() {
        if (stream == null) return;
        if (stream.getVideoSource() instanceof Camera2Source) {
            try {
                ((Camera2Source) stream.getVideoSource()).switchCamera();
            } catch (Exception ignored) {
            }
        }
    }

    public void startPublish(@NonNull String host, @NonNull String path,
                             @NonNull String username, @NonNull String password, int port) {
        if (host.isEmpty() || username.isEmpty() || password.isEmpty()) {
            LiveStreamState.INSTANCE.setError("Нужны хост, логин и пароль OTS");
            return;
        }
        String url = "rtsp://" + host + ":" + port + "/" + path;
        LiveStreamState.INSTANCE.setStarting(url);
        lastHost = host;
        lastPort = port;
        lastPath = path;
        RtspStream s = ensureStream();
        try {
            RtspStreamClient client = (RtspStreamClient) s.getStreamClient();
            client.setProtocol(Protocol.TCP);
            client.setAuthorization(username, password);
        } catch (Exception e) {
            Log.w(TAG, "auth setup: " + e.getMessage());
        }
        int orientation = CameraHelper.getCameraOrientation(this);
        if (s.isOnPreview()) {
            try {
                s.stopPreview();
            } catch (Exception ignored) {
            }
        }
        boolean videoOk = s.prepareVideo(1280, 720, 1_200_000, 30, 2, orientation);
        s.changeVideoSource(new Camera2Source(getApplicationContext()));
        s.changeAudioSource(new MicrophoneSource());
        boolean audioOk = s.prepareAudio(44100, true, 128 * 1024, true, true);
        if (!videoOk || !audioOk) {
            LiveStreamState.INSTANCE.setError("Кодек не подготовился");
            return;
        }
        if (preview != null) {
            try {
                s.startPreview(preview, true);
            } catch (Exception ignored) {
            }
        }
        s.startStream(url);
        startForeground(NOTIFY_ID, buildNotification("Эфир → " + path), fgsTypes());
        Context app = getApplicationContext();
        if (app instanceof OmniTAKApp) {
            ((OmniTAKApp) app).getLiveStreamCot().start(host, port, path);
        }
    }

    public void stopPublish() {
        if (stream != null && stream.isStreaming()) {
            try {
                stream.stopStream();
            } catch (Exception ignored) {
            }
        }
        LiveStreamState.INSTANCE.setIdle();
        Context app = getApplicationContext();
        if (app instanceof OmniTAKApp) {
            ((OmniTAKApp) app).getLiveStreamCot().stop();
        }
    }

    private RtspStream ensureStream() {
        if (stream == null) {
            stream = new RtspStream(getApplicationContext(), this);
        }
        return stream;
    }

    @Override
    public void onConnectionSuccess() {
        LiveStreamState.INSTANCE.setLive();
        startForeground(NOTIFY_ID, buildNotification("Эфир идёт"), fgsTypes());
    }

    @Override
    public void onConnectionFailed(@NonNull String reason) {
        Log.e(TAG, "stream failed: " + reason);
        LiveStreamState.INSTANCE.setError(reason);
        Context app = getApplicationContext();
        if (app instanceof OmniTAKApp) {
            ((OmniTAKApp) app).getLiveStreamCot().stop();
        }
        if (stream != null) {
            try {
                stream.stopStream();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onConnectionStarted(@NonNull String url) {
    }

    @Override
    public void onDisconnect() {
        if (LiveStreamState.INSTANCE.getStatus().getValue() == LiveStreamState.Status.Live) {
            LiveStreamState.INSTANCE.setIdle();
        }
        Context app = getApplicationContext();
        if (app instanceof OmniTAKApp) {
            ((OmniTAKApp) app).getLiveStreamCot().stop();
        }
    }

    @Override
    public void onAuthError() {
        LiveStreamState.INSTANCE.setError("MediaMTX отклонил логин/пароль");
        if (stream != null) {
            try {
                stream.stopStream();
            } catch (Exception ignored) {
            }
        }
        Context app = getApplicationContext();
        if (app instanceof OmniTAKApp) {
            ((OmniTAKApp) app).getLiveStreamCot().stop();
        }
    }

    @Override
    public void onAuthSuccess() {
    }

    @Override
    public void onNewBitrate(long bitrate) {
        LiveStreamState.INSTANCE.setBitrate(bitrate);
    }

    private int fgsTypes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        }
        return 0;
    }

    private Notification buildNotification(String text) {
        ensureChannel(this);
        PendingIntent tap = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("ПСР TAK")
                .setContentText(text)
                .setSmallIcon(R.mipmap.app_icon)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(tap)
                .build();
    }

    public static void start(Context context) {
        ensureChannel(context);
        ContextCompat.startForegroundService(context, new Intent(context, LiveStreamService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, LiveStreamService.class));
    }

    private static void ensureChannel(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                    new NotificationChannel(CHANNEL_ID, "Трансляция", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
