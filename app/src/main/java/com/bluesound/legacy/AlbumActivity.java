package com.bluesound.legacy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen list of RC:-prefixed Tidal playlists, reached by swiping
 * right on the now-playing area.
 *
 * The playlist data is pre-loaded in the background by MainActivity as soon
 * as the app starts, so this screen almost always opens instantly.  If it
 * hasn't arrived yet (e.g. the panel was opened very quickly after launch)
 * a "Loading…" message is shown and the list appears as soon as the data is
 * ready, with no user action required.
 *
 * Each row shows the album artwork on the left and the playlist name on the
 * right. Images are loaded asynchronously via ImageCache (3-thread pool,
 * downsampled 4x on decode) and cached for the session.
 */
public class AlbumActivity extends Activity {

    private BluOSClient       client;
    private final Handler     handler   = new Handler();
    private List<RcPlaylist>  playlists = new ArrayList<RcPlaylist>();
    private PlaylistAdapter   adapter;
    private TextView          txtEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN     |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        );

        setContentView(R.layout.activity_album);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String host = prefs.getString(MainActivity.KEY_HOST, "");
        int    port = prefs.getInt(MainActivity.KEY_PORT, MainActivity.DEFAULT_PORT);
        if (host != null && host.length() > 0) {
            client = new BluOSClient(host, port);
        }

        txtEmpty = (TextView) findViewById(R.id.txt_empty);
        ListView list = (ListView) findViewById(R.id.list_albums);
        adapter = new PlaylistAdapter();
        list.setAdapter(adapter);

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                play(playlists.get(position));
            }
        });

        showFromCache();
    }

    // -------------------------------------------------------------------------
    // Cache polling
    // -------------------------------------------------------------------------

    private void showFromCache() {
        List<RcPlaylist> cached = PlaylistCache.get();
        if (cached != null) {
            applyData(cached);
        } else if (PlaylistCache.isLoading()) {
            txtEmpty.setText("Loading\u2026");
            txtEmpty.setVisibility(View.VISIBLE);
            pollForCache();
        } else {
            txtEmpty.setText("No player configured");
            txtEmpty.setVisibility(View.VISIBLE);
        }
    }

    /** Poll every 300 ms until PlaylistCache has data, then display it. */
    private void pollForCache() {
        List<RcPlaylist> cached = PlaylistCache.get();
        if (cached != null) {
            applyData(cached);
        } else {
            handler.postDelayed(new Runnable() {
                public void run() { pollForCache(); }
            }, 300);
        }
    }

    private void applyData(List<RcPlaylist> data) {
        playlists = data;
        adapter.notifyDataSetChanged();
        if (playlists.isEmpty()) {
            txtEmpty.setText("No RC: playlists found");
            txtEmpty.setVisibility(View.VISIBLE);
        } else {
            txtEmpty.setVisibility(View.GONE);
        }
    }

    // -------------------------------------------------------------------------
    // Playback
    // -------------------------------------------------------------------------

    private void play(final RcPlaylist playlist) {
        if (client == null) {
            Toast.makeText(this, "No player configured", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(new Runnable() {
            public void run() {
                try {
                    client.loadPlaylist(playlist.playUrl);
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        public void run() {
                            Toast.makeText(AlbumActivity.this,
                                    "Could not reach player",
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                handler.post(new Runnable() {
                    public void run() { finish(); }
                });
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // List adapter
    // -------------------------------------------------------------------------

    private class PlaylistAdapter extends BaseAdapter {

        public int    getCount()           { return playlists.size(); }
        public Object getItem(int pos)     { return playlists.get(pos); }
        public long   getItemId(int pos)   { return pos; }

        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater()
                        .inflate(R.layout.item_album, parent, false);
            }
            RcPlaylist p = playlists.get(pos);
            ((TextView)  convertView.findViewById(R.id.txt_album_title))
                    .setText(p.name);
            ImageCache.load(p.imageUrl,
                    (ImageView) convertView.findViewById(R.id.img_album_art),
                    handler);
            return convertView;
        }
    }
}
