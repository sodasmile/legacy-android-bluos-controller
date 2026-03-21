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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Full-screen vertically-scrolling list of Tidal albums.
 * Reached by swiping right on the now-playing area in MainActivity.
 * Tapping an album tells the BluOS player to start playing it via
 * /Add?playnow=1&service=Tidal&albumid=... then returns to the main screen.
 */
public class AlbumActivity extends Activity {

    private BluOSClient client;
    private final Handler handler = new Handler();

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

        ListView list = (ListView) findViewById(R.id.list_albums);
        list.setAdapter(new AlbumAdapter());

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playAlbum(Album.ALL[position]);
            }
        });
    }

    private void playAlbum(final Album album) {
        if (client == null) {
            Toast.makeText(this, "No player configured", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.playAlbum(album.service, album.albumId);
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            Toast.makeText(AlbumActivity.this,
                                    "Could not reach player", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                handler.post(new Runnable() {
                    @Override public void run() { finish(); }
                });
            }
        }).start();
    }

    // Two-line list adapter: bold title + dim artist
    private class AlbumAdapter extends BaseAdapter {

        @Override public int getCount()                             { return Album.ALL.length; }
        @Override public Object getItem(int position)              { return Album.ALL[position]; }
        @Override public long getItemId(int position)              { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater()
                        .inflate(R.layout.item_album, parent, false);
            }
            Album a = Album.ALL[position];
            ((TextView) convertView.findViewById(R.id.txt_album_title)).setText(a.title);
            ((TextView) convertView.findViewById(R.id.txt_album_artist)).setText(a.artist);
            return convertView;
        }
    }
}
