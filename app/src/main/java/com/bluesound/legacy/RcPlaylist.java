package com.bluesound.legacy;

/**
 * A Tidal playlist prefixed with "RC: " — fetched live from the player.
 *
 * The naming convention is the entire management API: rename a Tidal playlist
 * to start with "RC: " and it appears in the panel; remove the prefix and
 * it disappears. No app update needed.
 *
 * "RC: " is stripped from the display name so "RC: Bursdag" → "Bursdag".
 */
class RcPlaylist {

    static final String PREFIX = "RC: ";

    final String name;      // display name (prefix already stripped)
    final String playUrl;   // relative path, e.g. /Load?service=Tidal&name=...&id=...
    final String imageUrl;  // full URL to artwork, or null

    RcPlaylist(String name, String playUrl, String imageUrl) {
        this.name     = name;
        this.playUrl  = playUrl;
        this.imageUrl = imageUrl;
    }
}
