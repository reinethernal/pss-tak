package io.opentakserver.opentakicu;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import io.opentakserver.opentakicu.contants.Preferences;

/**
 * No baked-in HQ host. Only migrate broken local prefs so a stream can start
 * after an invite (or a previous settings screen visit) without UDP URL errors.
 */
public final class PsrServerDefaults {
    private static final String TAG = "PsrServerDefaults";

    private PsrServerDefaults() {}

    public static void apply(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor ed = prefs.edit();
        boolean changed = false;

        String proto = prefs.getString(Preferences.STREAM_PROTOCOL, Preferences.STREAM_PROTOCOL_DEFAULT);
        String fixedProto = Preferences.normalizeProtocol(proto);
        if (!fixedProto.equals(proto)) {
            ed.putString(Preferences.STREAM_PROTOCOL, fixedProto);
            changed = true;
            Log.i(TAG, "Migrated stream protocol '" + proto + "' → " + fixedProto);
        }

        String addr = prefs.getString(Preferences.STREAM_ADDRESS, Preferences.STREAM_ADDRESS_DEFAULT);
        String host = Preferences.hostOnly(addr);
        if (!host.isEmpty() && !host.equals(addr)) {
            ed.putString(Preferences.STREAM_ADDRESS, host);
            changed = true;
            Log.i(TAG, "Migrated stream address to host-only: " + host);
        }

        if (changed) ed.apply();
    }
}
