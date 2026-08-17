package io.opentakserver.opentakicu;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import io.opentakserver.opentakicu.contants.Preferences;

/**
 * Apply HQ invite / TAK handoff deep links. No host is baked into the APK —
 * address, ports and CA come from the URI the operator opened.
 *
 * {@code opentakicu://import?address=…&port=8554&protocol=rtsp&path=…&username=…
 * &password=…&atak_address=…&atak_port=8089&autostart=1
 * &truststore_url=https://…/truststore-root.p12}
 */
public final class InviteConfig {
    private static final String TAG = "InviteConfig";

    private InviteConfig() {}

    public static boolean apply(Context context, Uri data) {
        if (data == null) return false;
        String scheme = data.getScheme();
        if (scheme == null || !"opentakicu".equalsIgnoreCase(scheme)) return false;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor ed = prefs.edit();
        boolean any = false;

        String address = first(data, "address", "host");
        String protocol = first(data, "protocol");
        String port = first(data, "port", "stream_port");
        String path = first(data, "path");
        if (address != null && address.contains("://")) {
            try {
                Uri stream = Uri.parse(address);
                if (protocol == null && stream.getScheme() != null) protocol = stream.getScheme();
                if (stream.getHost() != null) address = stream.getHost();
                if (port == null && stream.getPort() > 0) port = String.valueOf(stream.getPort());
                if ((path == null || path.isEmpty()) && stream.getPath() != null) {
                    String p = stream.getPath();
                    if (p.startsWith("/")) p = p.substring(1);
                    if (!p.isEmpty()) path = p;
                }
            } catch (Exception ignored) {
                address = Preferences.hostOnly(address);
            }
        } else if (address != null) {
            address = Preferences.hostOnly(address);
        }

        any |= put(ed, Preferences.STREAM_ADDRESS, address);
        any |= put(ed, Preferences.STREAM_PORT, port);
        any |= put(ed, Preferences.STREAM_PROTOCOL, Preferences.normalizeProtocol(protocol));
        any |= put(ed, Preferences.STREAM_PATH, path);
        any |= put(ed, Preferences.STREAM_USERNAME, first(data, "username", "user"));
        any |= put(ed, Preferences.STREAM_PASSWORD, first(data, "password", "pw"));
        any |= put(ed, Preferences.ATAK_SERVER_ADDRESS, first(data, "atak_address", "cot_host"));
        any |= put(ed, Preferences.ATAK_SERVER_PORT, first(data, "atak_port", "cot_port"));
        any |= put(ed, Preferences.ATAK_SERVER_USERNAME, first(data, "atak_username"));
        any |= put(ed, Preferences.ATAK_SERVER_PASSWORD, first(data, "atak_password"));

        String ssl = first(data, "atak_ssl");
        if (ssl != null) {
            ed.putBoolean(Preferences.ATAK_SERVER_SSL, truthy(ssl));
            any = true;
        }
        String selfSigned = first(data, "self_signed_cert");
        if (selfSigned != null) {
            ed.putBoolean(Preferences.STREAM_SELF_SIGNED_CERT, truthy(selfSigned));
            any = true;
        }
        String autostart = first(data, "autostart", "start");
        if (autostart != null) {
            ed.putBoolean(Preferences.STREAM_AUTOSTART, truthy(autostart));
            any = true;
        }

        // If invite gave a stream host but no CoT host, reuse it.
        if (prefs.getString(Preferences.ATAK_SERVER_ADDRESS, "").isEmpty() && address != null) {
            ed.putString(Preferences.ATAK_SERVER_ADDRESS, address);
            any = true;
        }

        ed.apply();

        String trustUrl = first(data, "truststore_url", "ca_url");
        if (trustUrl != null && !trustUrl.isEmpty()) {
            new Thread(() -> downloadTruststore(context, trustUrl), "icu-truststore").start();
        }
        if (any) {
            Log.i(TAG, "Applied invite config host=" + address + " protocol=" + Preferences.normalizeProtocol(protocol));
        }
        return any;
    }

    private static boolean put(SharedPreferences.Editor ed, String key, String value) {
        if (value == null || value.isEmpty()) return false;
        ed.putString(key, value);
        return true;
    }

    private static String first(Uri data, String... keys) {
        for (String k : keys) {
            String v = data.getQueryParameter(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static boolean truthy(String v) {
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    private static void downloadTruststore(Context context, String url) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                Log.w(TAG, "truststore download HTTP " + code);
                return;
            }
            File dest = new File(context.getFilesDir(), "truststore-root.p12");
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            if (dest.length() < 32) {
                Log.w(TAG, "truststore too small, ignoring");
                return;
            }
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putString(Preferences.ATAK_SERVER_SSL_TRUST_STORE, dest.getAbsolutePath())
                    .putString(Preferences.STREAM_CERTIFICATE, dest.getAbsolutePath())
                    .putString(Preferences.ATAK_SERVER_SSL_TRUST_STORE_PASSWORD, "atakatak")
                    .putString(Preferences.STREAM_CERTIFICATE_PASSWORD, "atakatak")
                    .putBoolean(Preferences.STREAM_SELF_SIGNED_CERT, true)
                    .apply();
            Log.i(TAG, "Saved truststore to " + dest.getAbsolutePath());
        } catch (Exception e) {
            Log.w(TAG, "truststore download failed: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
