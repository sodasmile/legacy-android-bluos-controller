package com.bluesound.legacy;

/**
 * A streamable album — title and artist shown in the list,
 * service + albumId sent to the BluOS player via /Add?playnow=1.
 */
class Album {

    final String title;
    final String artist;
    final String service;
    final String albumId;

    Album(String title, String artist, String service, String albumId) {
        this.title   = title;
        this.artist  = artist;
        this.service = service;
        this.albumId = albumId;
    }

    static final Album[] ALL = {
        new Album("Nebraska", "Bruce Springsteen", "Tidal", "24385359"),
    };
}
