package com.bluesound.legacy;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

/**
 * Settings screen: configure BluOS player IP and port.
 * Also allows auto-discovery via mDNS (_musc._tcp.local) so the user
 * never has to type an IP address manually.
 */
public class SettingsActivity extends Activity {

    private EditText editHost;
    private EditText editPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );

        setContentView(R.layout.activity_settings);

        editHost = (EditText) findViewById(R.id.edit_host);
        editPort = (EditText) findViewById(R.id.edit_port);

        // Pre-fill with saved values
        SharedPreferences prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String savedHost = prefs.getString(MainActivity.KEY_HOST, "");
        int    savedPort = prefs.getInt(MainActivity.KEY_PORT, MainActivity.DEFAULT_PORT);

        editHost.setText(savedHost);
        editPort.setText(String.valueOf(savedPort));

        ((Button) findViewById(R.id.btn_discover)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startDiscovery(); }
        });

        ((Button) findViewById(R.id.btn_save)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveSettings(); }
        });

        ((Button) findViewById(R.id.btn_back)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

    // -------------------------------------------------------------------------
    // mDNS discovery
    // -------------------------------------------------------------------------

    private void startDiscovery() {
        final Button btn = (Button) findViewById(R.id.btn_discover);
        btn.setEnabled(false);
        btn.setText("SEARCHING...");

        new MdnsDiscovery(this, new MdnsDiscovery.Listener() {
            @Override
            public void onDiscoveryComplete(final List<MdnsDiscovery.Player> players) {
                btn.setEnabled(true);
                btn.setText("DISCOVER PLAYERS");

                if (players.isEmpty()) {
                    Toast.makeText(SettingsActivity.this,
                            "No players found on network", Toast.LENGTH_LONG).show();
                    return;
                }

                // Build name list for the selection dialog
                final String[] names = new String[players.size()];
                for (int i = 0; i < players.size(); i++) {
                    names[i] = players.get(i).toString();
                }

                new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("Select Player")
                        .setItems(names, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                MdnsDiscovery.Player p = players.get(which);
                                editHost.setText(p.host);
                                editPort.setText(String.valueOf(p.port));
                            }
                        })
                        .show();
            }
        }).discover();
    }

    // -------------------------------------------------------------------------
    // Save
    // -------------------------------------------------------------------------

    private void saveSettings() {
        String host    = editHost.getText().toString().trim();
        String portStr = editPort.getText().toString().trim();

        if (host.length() == 0) {
            Toast.makeText(this, "Please enter a player IP address", Toast.LENGTH_SHORT).show();
            return;
        }

        int port = MainActivity.DEFAULT_PORT;
        if (portStr.length() > 0) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                // Use default — the hint text shows 11000
            }
        }

        SharedPreferences.Editor editor =
                getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit();
        editor.putString(MainActivity.KEY_HOST, host);
        editor.putInt(MainActivity.KEY_PORT, port);
        editor.commit();

        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onBackPressed() {
        finish();   // Allow back navigation from settings
    }
}
