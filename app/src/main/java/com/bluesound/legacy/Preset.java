package com.bluesound.legacy;

/**
 * A radio station preset — name shown in the list, url sent to the BluOS player.
 * Matches the presets used in the aluminium-dial (bluos-knob) project.
 */
class Preset {

    final String name;
    final String url;

    Preset(String name, String url) {
        this.name = name;
        this.url  = url;
    }

    static final Preset[] ALL = {
        new Preset("Radio Paradise",  "http://stream.radioparadise.com/mp3-192"),
        new Preset("NRK P1",          "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/p1"),
        new Preset("NRK P2",          "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/p2"),
        new Preset("NRK P3",          "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/p3"),
        new Preset("NRK Jazz",        "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/jazz"),
        new Preset("NRK Klassisk",    "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/klassisk"),
        new Preset("NRK mP3",         "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/mp3"),
        new Preset("NRK Nyheter",     "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/nyheter"),
        new Preset("NRK Sport",       "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/sport"),
        new Preset("NRK Folkemusikk", "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/folkemusikk"),
        new Preset("NRK Radio Super", "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/radio_super"),
        new Preset("NRK P1+",         "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/p1pluss"),
        new Preset("NRK P3 Musikk",   "https://lyd.nrk.no/icecast/mp3/high/s0w7hwn47m/p3musikk"),
    };
}
