package com.bluesound.legacy;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.widget.ImageView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Async image loader with a simple in-memory cache.
 *
 * Images are decoded with inSampleSize=4 so old devices don't run out of
 * heap — typical 600px album art becomes ~150px in memory (~90 KB).
 * With 15 playlists that's well under 2 MB total.
 *
 * A fixed thread pool of 3 is used instead of spawning one thread per
 * visible list item, which matters on slow single-core devices.
 *
 * ListView recycling is handled by tagging each ImageView with the URL it
 * is waiting for. If the view is recycled before the fetch completes the
 * result is silently discarded.
 */
class ImageCache {

    private static final HashMap<String, Bitmap> cache =
            new HashMap<String, Bitmap>();

    // 3 concurrent fetches — enough to fill a small screen quickly without
    // overwhelming the network stack on an old device.
    private static final ExecutorService pool =
            Executors.newFixedThreadPool(3);

    /**
     * Load the image at {@code url} into {@code view} asynchronously.
     * Returns immediately. If the bitmap is already cached it is set
     * synchronously on the calling (main) thread.
     */
    static void load(final String url, final ImageView view,
                     final Handler handler) {
        if (url == null) {
            view.setImageBitmap(null);
            return;
        }

        // Cache hit: set immediately, no thread needed
        synchronized (cache) {
            Bitmap hit = cache.get(url);
            if (hit != null) {
                view.setImageBitmap(hit);
                return;
            }
        }

        // Tag view so we can detect recycling
        view.setTag(url);
        view.setImageBitmap(null);

        pool.execute(new Runnable() {
            public void run() {
                final Bitmap bmp = fetch(url);
                if (bmp != null) {
                    synchronized (cache) { cache.put(url, bmp); }
                }
                handler.post(new Runnable() {
                    public void run() {
                        // Only deliver if the view still wants this URL
                        if (url.equals(view.getTag())) {
                            view.setImageBitmap(bmp);
                        }
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) return null;

            BitmapFactory.Options opts = new BitmapFactory.Options();
            // inSampleSize=4: 600px→150px (54 KB), 300px→75px (22 KB)
            opts.inSampleSize = 4;
            return BitmapFactory.decodeStream(conn.getInputStream(), null, opts);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    static void clear() {
        synchronized (cache) { cache.clear(); }
    }
}
