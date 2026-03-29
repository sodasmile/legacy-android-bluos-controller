package com.bluesound.legacy;

/**
 * Holds the parsed state from a BluOS /Status XML response.
 */
public class BluOSStatus {
    public String  state;       // "play", "pause", "stop", "stream", etc.
    public String  title;
    public String  artist;
    public String  album;
    public int     volume;      // 0–100
    public boolean isPlaying;
    public boolean mute;        // true when muted
    public int     totlen;      // total track length in seconds; 0 for live streams
    public String  imageUrl;    // full http://host:port/Artwork?... URL, or null

    public BluOSStatus() {
        state    = "stop";
        title    = "";
        artist   = "";
        album    = "";
        volume   = 0;
        isPlaying = false;
        mute     = false;
        totlen   = 0;
        imageUrl = null;
    }
}
