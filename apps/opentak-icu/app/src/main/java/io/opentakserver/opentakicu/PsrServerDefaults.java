package io.opentakserver.opentakicu;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import io.opentakserver.opentakicu.contants.Preferences;

/**
 * One-shot defaults for the ПСР OpenTAKServer build (fts.plasmadancer.ru).
 * Copies the CA truststore from assets and fills empty preference keys.
 */
public final class PsrServerDefaults {
    private static final String TAG = "PsrServerDefaults";
    private static final String FLAG = "psr_defaults_applied_v1";

    private PsrServerDefaults() {}

    public static void apply(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(FLAG, false)) {
            // Still ensure address fallbacks if user cleared them
            ensureKey(prefs, Preferences.STREAM_ADDRESS, Preferences.STREAM_ADDRESS_DEFAULT);
            ensureKey(prefs, Preferences.ATAK_SERVER_ADDRESS, Preferences.ATAK_SERVER_ADDRESS_DEFAULT);
            return;
        }

        File dest = new File(context.getFilesDir(), "truststore-root.p12");
        try (InputStream in = context.getAssets().open("truststore-root.p12");
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy truststore from assets", e);
        }

        SharedPreferences.Editor ed = prefs.edit();
        putIfAbsent(ed, prefs, Preferences.STREAM_ADDRESS, Preferences.STREAM_ADDRESS_DEFAULT);
        putIfAbsent(ed, prefs, Preferences.STREAM_PORT, Preferences.STREAM_PORT_DEFAULT);
        putIfAbsent(ed, prefs, Preferences.STREAM_PATH, Preferences.STREAM_PATH_DEFAULT);
        if (!prefs.contains(Preferences.STREAM_USE_TCP)) {
            ed.putBoolean(Preferences.STREAM_USE_TCP, Preferences.STREAM_USE_TCP_DEFAULT);
        }
        putIfAbsent(ed, prefs, Preferences.ATAK_SERVER_ADDRESS, Preferences.ATAK_SERVER_ADDRESS_DEFAULT);
        putIfAbsent(ed, prefs, Preferences.ATAK_SERVER_PORT, Preferences.ATAK_SERVER_PORT_DEFAULT);
        if (!prefs.contains(Preferences.ATAK_SEND_COT)) {
            ed.putBoolean(Preferences.ATAK_SEND_COT, Preferences.ATAK_SEND_COT_DEFAULT);
        }
        if (!prefs.contains(Preferences.ATAK_SERVER_SSL)) {
            ed.putBoolean(Preferences.ATAK_SERVER_SSL, Preferences.ATAK_SERVER_SSL_DEFAULT);
        }
        putIfAbsent(ed, prefs, Preferences.ATAK_SERVER_SSL_TRUST_STORE_PASSWORD,
                Preferences.ATAK_SERVER_SSL_TRUST_STORE_PASSWORD_DEFAULT);
        putIfAbsent(ed, prefs, Preferences.STREAM_CERTIFICATE_PASSWORD,
                Preferences.STREAM_CERTIFICATE_PASSWORD_DEFAULT);
        if (dest.isFile()) {
            ed.putString(Preferences.ATAK_SERVER_SSL_TRUST_STORE, dest.getAbsolutePath());
            ed.putString(Preferences.STREAM_CERTIFICATE, dest.getAbsolutePath());
            // Prefer RTSPS + truststore when cert is present
            if (!prefs.contains(Preferences.STREAM_SELF_SIGNED_CERT)) {
                ed.putBoolean(Preferences.STREAM_SELF_SIGNED_CERT, true);
            }
            // RTSPS port used by nginx on this server
            if (!prefs.contains("psr_rtsps_port_hint")) {
                // keep STREAM_PORT at 8554 for plain RTSP; operator can switch
                ed.putBoolean("psr_rtsps_port_hint", true);
            }
        }
        ed.putBoolean(FLAG, true);
        ed.apply();
        Log.i(TAG, "Applied ПСР defaults for fts.plasmadancer.ru");
    }

    private static void putIfAbsent(SharedPreferences.Editor ed, SharedPreferences prefs, String key, String value) {
        if (!prefs.contains(key) && value != null) ed.putString(key, value);
    }

    private static void ensureKey(SharedPreferences prefs, String key, String value) {
        if (!prefs.contains(key) && value != null) {
            prefs.edit().putString(key, value).apply();
        }
    }
}
