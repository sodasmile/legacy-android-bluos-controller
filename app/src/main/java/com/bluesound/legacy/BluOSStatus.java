package com.bluesound.legacy;

/**
 * Holds the parsed state from a BluOS /Status XML response.
 */
public class BluOSStatus {
    public String state;    // "play", "pause", "stop", "connecting", "loading", etc.
    public String title;
    public String artist;
    public String album;
    public int volume;      // 0–100
    public boolean isPlaying;

    public BluOSStatus() {
        state = "stop";
        title = "";
        artist = "";
        album = "";
        volume = 0;
        isPlaying = false;
    }
}
