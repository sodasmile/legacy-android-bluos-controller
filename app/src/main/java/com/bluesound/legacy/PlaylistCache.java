package com.bluesound.legacy;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton cache for RC:-prefixed Tidal playlists.
 *
 * Loaded once in the background when the app starts so the swipe panel
 * opens instantly without any visible network wait. Automatically
 * invalidates and reloads when the player host or port changes.
 */
class PlaylistCache {

    private static String          cachedHost = "";
    private static int             cachedPort = 0;
    private static List<RcPlaylist> data      = null;
    private static boolean         loading    = false;

    /**
     * Pre-warm the cache. Call from MainActivity after the client is ready.
     * No-op if the cache is already fresh for this host/port.
     * If the host or port changed the old data is discarded and a fresh
     * fetch is started.
     */
    static synchronized void preload(final BluOSClient client,
                                     final String host, final int port) {
        boolean samePlayer = host.equals(cachedHost) && port == cachedPort;
        if (samePlayer && (data != null || loading)) return;

        data       = null;
        loading    = true;
        cachedHost = host;
        cachedPort = port;

        new Thread(new Runnable() {
            public void run() {
                List<RcPlaylist> result;
                try {
                    result = client.fetchRcPlaylists();
                } catch (Exception e) {
                    result = new ArrayList<RcPlaylist>();
                }
                synchronized (PlaylistCache.class) {
                    data    = result;
                    loading = false;
                }
            }
        }).start();
    }

    /** Returns the cached list, or null if still loading or not started. */
    static synchronized List<RcPlaylist> get() { return data; }

    /** True while the background fetch is in progress. */
    static synchronized boolean isLoading() { return loading; }
}
