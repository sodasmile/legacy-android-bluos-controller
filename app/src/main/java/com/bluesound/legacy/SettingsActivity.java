package com.bluesound.legacy;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Simple settings screen for configuring the BluOS player's IP address and port.
 * Values are persisted in SharedPreferences and read by MainActivity on every onResume.
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

        ((Button) findViewById(R.id.btn_save)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveSettings(); }
        });

        ((Button) findViewById(R.id.btn_back)).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
    }

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
