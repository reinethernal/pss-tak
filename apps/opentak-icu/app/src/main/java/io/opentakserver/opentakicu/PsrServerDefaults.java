package io.opentakserver.opentakicu;

import android.content.Context;
import android.util.Log;

/**
 * Leftover hook from the old «baked-in HQ» build. Apps no longer ship a
 * default host or CA — configuration arrives via {@link InviteConfig}
 * (HQ invite link or handoff from ПСР TAK).
 */
public final class PsrServerDefaults {
    private static final String TAG = "PsrServerDefaults";

    private PsrServerDefaults() {}

    public static void apply(Context context) {
        Log.d(TAG, "No baked-in server defaults; wait for invite / opentakicu:// link");
    }
}
