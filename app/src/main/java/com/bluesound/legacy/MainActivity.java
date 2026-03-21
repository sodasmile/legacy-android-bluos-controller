package com.bluesound.legacy;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/**
 * Main kiosk activity — fullscreen BluOS remote control.
 *
 * Kiosk behaviour:
 *   - Declared as HOME launcher in manifest → becomes default home screen
 *   - Back button suppressed
 *   - Screen kept on; wakes and shows over the lock screen on resume
 *   - BootReceiver starts this activity after device reboot
 *
 * Polling: fetches /Status every 2 s on a background thread, updates UI on main thread.
 */
public class MainActivity extends Activity {

    static final String PREFS       = "BluOSPrefs";
    static final String KEY_HOST    = "host";
    static final String KEY_PORT    = "port";
    static final int    DEFAULT_PORT = 11000;

    private static final int POLL_INTERVAL_MS = 2000;

    // Views
    private TextView txtStatus;
    private TextView txtTitle;
    private TextView txtArtist;
    private TextView txtAlbum;
    private TextView txtVolume;
    private Button   btnPlayPause;

    // State
    private BluOSClient client;
    private boolean isPlaying    = false;
    private int     currentVolume = 0;
    private boolean polling      = false;

    private final Handler handler = new Handler();

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (polling) {
                fetchStatus();
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Fullscreen + keep screen on + show over lock screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN       |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON   |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_main);

        txtStatus   = (TextView) findViewById(R.id.txt_status);
        txtTitle    = (TextView) findViewById(R.id.txt_title);
        txtArtist   = (TextView) findViewById(R.id.txt_artist);
        txtAlbum    = (TextView) findViewById(R.id.txt_album);
        txtVolume   = (TextView) findViewById(R.id.txt_volume);
        btnPlayPause = (Button)  findViewById(R.id.btn_play_pause);

        setupButtons();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadClient();   // Reload settings in case they were changed
        polling = true;
        handler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        polling = false;
        handler.removeCallbacks(pollRunnable);
    }

    @Override
    public void onBackPressed() {
        // Intentionally suppressed — kiosk mode
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private void loadClient() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String host = prefs.getString(KEY_HOST, "");
        int    port = prefs.getInt(KEY_PORT, DEFAULT_PORT);

        if (host == null || host.length() == 0) {
            client = null;
            txtStatus.setText("No player configured — tap \u2699 to set up");
            txtTitle.setText("");
            txtArtist.setText("");
            txtAlbum.setText("");
            txtVolume.setText("--");
        } else {
            client = new BluOSClient(host, port);
        }
    }

    private void setupButtons() {
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { togglePlayPause(); }
        });

        findViewById(R.id.btn_prev).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("back"); }
        });

        findViewById(R.id.btn_next).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendCommand("skip"); }
        });

        findViewById(R.id.btn_vol_down).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setVolume(Math.max(0, currentVolume - 1));
            }
        });

        findViewById(R.id.btn_vol_up).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                setVolume(Math.min(100, currentVolume + 1));
            }
        });

        findViewById(R.id.btn_settings).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        // Swipe right on the now-playing area → open station list
        final GestureDetector swipe = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    private static final int MIN_DISTANCE = 80;
                    private static final int MIN_VELOCITY = 80;

                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true; // must return true or fling events are never delivered
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                          float vX, float vY) {
                        float dX = e2.getX() - e1.getX();
                        float dY = e2.getY() - e1.getY();
                        if (Math.abs(dX) > Math.abs(dY)
                                && Math.abs(dX) > MIN_DISTANCE
                                && Math.abs(vX) > MIN_VELOCITY) {
                            if (dX < 0) {
                                // Left swipe → radio presets
                                startActivity(new Intent(MainActivity.this,
                                        PresetActivity.class));
                            } else {
                                // Right swipe → Tidal albums
                                startActivity(new Intent(MainActivity.this,
                                        AlbumActivity.class));
                            }
                            return true;
                        }
                        return false;
                    }
                });

        findViewById(R.id.layout_now_playing).setOnTouchListener(
                new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return swipe.onTouchEvent(event);
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    private void togglePlayPause() {
        if (client == null) return;

        // Optimistic UI flip
        final boolean wasPlaying = isPlaying;
        isPlaying = !isPlaying;
        updatePlayPauseButton();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (wasPlaying) client.pause();
                    else            client.play();
                    // Trigger an immediate status poll to confirm
                    handler.post(new Runnable() {
                        @Override public void run() { fetchStatus(); }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            // Revert optimistic update
                            isPlaying = wasPlaying;
                            updatePlayPauseButton();
                            txtStatus.setText("Error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void sendCommand(final String cmd) {
        if (client == null) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if ("skip".equals(cmd))     client.skip();
                    else if ("back".equals(cmd)) client.back();
                    // Short delay lets the player update before we poll
                    handler.postDelayed(new Runnable() {
                        @Override public void run() { fetchStatus(); }
                    }, 300);
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            txtStatus.setText("Error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    private void setVolume(final int level) {
        if (client == null) return;

        // Optimistic update
        currentVolume = level;
        txtVolume.setText(String.valueOf(level));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.setVolume(level);
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            txtStatus.setText("Error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Status polling
    // -------------------------------------------------------------------------

    private void fetchStatus() {
        if (client == null) return;

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final BluOSStatus status = client.getStatus();
                    handler.post(new Runnable() {
                        @Override public void run() {
                            if (status != null) updateUI(status);
                            else txtStatus.setText("No response from player");
                        }
                    });
                } catch (final Exception e) {
                    handler.post(new Runnable() {
                        @Override public void run() {
                            txtStatus.setText("Cannot reach player");
                        }
                    });
                }
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // UI updates
    // -------------------------------------------------------------------------

    private void updateUI(BluOSStatus status) {
        isPlaying     = status.isPlaying;
        currentVolume = status.volume;

        txtTitle.setText(status.title.length() > 0 ? status.title : "Nothing playing");
        txtArtist.setText(status.artist);
        txtAlbum.setText(status.album);
        txtVolume.setText(String.valueOf(status.volume));
        txtStatus.setText(stateLabel(status.state));

        updatePlayPauseButton();
    }

    private void updatePlayPauseButton() {
        btnPlayPause.setText(isPlaying ? "||" : ">");
    }

    private String stateLabel(String state) {
        if ("play".equals(state))         return "Playing";
        if ("pause".equals(state))        return "Paused";
        if ("stop".equals(state))         return "Stopped";
        if ("connecting".equals(state))   return "Connecting...";
        if ("loading".equals(state))      return "Loading...";
        if ("streamingInterrupted".equals(state)) return "Interrupted";
        return state;
    }
}
