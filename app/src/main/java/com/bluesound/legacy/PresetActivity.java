package com.bluesound.legacy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import java.io.IOException;
import java.net.URLEncoder;

/**
 * Full-screen vertically-scrolling list of radio station presets.
 * Reached by swiping right on the now-playing area in MainActivity.
 * Tapping a station tells the BluOS player to start streaming it,
 * then returns to the main screen.
 */
public class PresetActivity extends Activity {

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

        setContentView(R.layout.activity_preset);

        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String host = prefs.getString(MainActivity.KEY_HOST, "");
        int    port = prefs.getInt(MainActivity.KEY_PORT, MainActivity.DEFAULT_PORT);
        if (host != null && host.length() > 0) {
            client = new BluOSClient(host, port);
        }

        // Build name list for the adapter
        String[] names = new String[Preset.ALL.length];
        for (int i = 0; i < Preset.ALL.length; i++) {
            names[i] = Preset.ALL[i].name;
        }

        ListView list = (ListView) findViewById(R.id.list_presets);
        list.setAdapter(new ArrayAdapter<String>(
                this, R.layout.item_preset, names));

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                playPreset(Preset.ALL[position]);
            }
        });
    }

    private void playPreset(final Preset preset) {
        if (client == null) {
            Toast.makeText(this, "No player configured", Toast.LENGTH_SHORT).show();
            return;
        }

        // Fire and forget on a background thread, then return to main screen
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.playUrl(preset.url);
                } catch (final IOException e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            Toast.makeText(PresetActivity.this,
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
}
