package com.bluesound.legacy;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal BluOS HTTP API client.
 * All methods are blocking — call from a background thread.
 *
 * BluOS base URL: http://<host>:11000
 */
public class BluOSClient {

    private final String host;
    private final int port;

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS    = 5000;

    public BluOSClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // -------------------------------------------------------------------------
    // Public commands
    // -------------------------------------------------------------------------

    public BluOSStatus getStatus() throws IOException, XmlPullParserException {
        String xml = doGet("/Status");
        if (xml == null) return null;
        return parseStatus(xml);
    }

    public void play()  throws IOException { doGet("/Play"); }
    public void pause() throws IOException { doGet("/Pause"); }
    public void skip()  throws IOException { doGet("/Skip"); }
    public void back()  throws IOException { doGet("/Back"); }

    public void setVolume(int level) throws IOException {
        int clamped = Math.max(0, Math.min(100, level));
        doGet("/Volume?level=" + clamped);
    }

    public void playAlbum(String service, String albumId) throws IOException {
        doGet("/Add?playnow=1&service=" + service + "&albumid=" + albumId);
    }

    /** Load a playlist using the play URL path returned by the Browse API. */
    public void loadPlaylist(String playUrl) throws IOException {
        doGet(playUrl);
    }

    /**
     * Fetch all Tidal playlists whose name starts with "RC: " by paginating
     * through /Browse My Playlists. The prefix is stripped in the returned
     * RcPlaylist objects. Image URLs are resolved to full http://host:port/...
     * so callers don't need to know the player address.
     */
    public List<RcPlaylist> fetchRcPlaylists()
            throws IOException, XmlPullParserException {
        List<RcPlaylist> result = new ArrayList<RcPlaylist>();
        // First page key — note: inner path is already %-encoded, the & between
        // outer query params must be encoded as %26 (handled in the loop below).
        String key = "Tidal:Playlist/%2FPlaylists%3Fservice%3DTidal";
        int maxPages = 20; // safety cap — 20 pages × 30 items = 600 playlists max
        while (key != null && maxPages-- > 0) {
            // The nextKey value (from XML) uses & as separator; these must be
            // encoded as %26 so they aren't mistaken for additional URL params.
            String xml = doGet("/Browse?key=" + key.replace("&", "%26"));
            if (xml == null) break;
            key = parseRcPage(xml, result);
        }
        return result;
    }

    /**
     * Parse one page of /Browse playlist results.
     * Appends matching RC: items to {@code out}.
     * Returns the nextKey attribute value (for the next page), or null if done.
     */
    private String parseRcPage(String xml, List<RcPlaylist> out)
            throws IOException, XmlPullParserException {
        String nextKey = null;
        XmlPullParser p = Xml.newPullParser();
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        p.setInput(new StringReader(xml));

        int event = p.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = p.getName();
                if ("browse".equals(tag)) {
                    nextKey = p.getAttributeValue(null, "nextKey");
                } else if ("item".equals(tag)) {
                    String text = p.getAttributeValue(null, "text");
                    if (text != null && text.startsWith(RcPlaylist.PREFIX)) {
                        String displayName = text.substring(RcPlaylist.PREFIX.length());
                        String playUrl    = p.getAttributeValue(null, "playURL");
                        String imageRel   = p.getAttributeValue(null, "image");
                        // Resolve relative image path to a full URL
                        String imageUrl   = (imageRel != null && imageRel.length() > 0)
                                ? "http://" + host + ":" + port + imageRel
                                : null;
                        if (playUrl != null && playUrl.length() > 0) {
                            out.add(new RcPlaylist(displayName, playUrl, imageUrl));
                        }
                    }
                }
            }
            event = p.next();
        }
        return nextKey;
    }

    public void playUrl(String url) throws IOException {
        try {
            doGet("/Play?url=" + URLEncoder.encode(url, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            // UTF-8 is always supported
        }
    }

    // -------------------------------------------------------------------------
    // HTTP helper
    // -------------------------------------------------------------------------

    private String doGet(String path) throws IOException {
        URL url = new URL("http://" + host + ":" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code != 200) return null;

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            reader.close();
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    // -------------------------------------------------------------------------
    // XML parsing
    // -------------------------------------------------------------------------

    private BluOSStatus parseStatus(String xml) throws IOException, XmlPullParserException {
        BluOSStatus status = new BluOSStatus();

        // title1/2/3 are populated for all service types and take precedence
        // over name/artist/album. For radio, <name> is the raw stream URL;
        // <title1> is the station name, <title2> the current programme/song.
        String title1 = "", title2 = "", title3 = "";

        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
        parser.setInput(new StringReader(xml));

        int event = parser.getEventType();
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                String tag = parser.getName();
                if ("state".equals(tag)) {
                    status.state = readText(parser);
                    status.isPlaying = "play".equals(status.state);
                } else if ("title1".equals(tag)) {
                    title1 = readText(parser);
                } else if ("title2".equals(tag)) {
                    title2 = readText(parser);
                } else if ("title3".equals(tag)) {
                    title3 = readText(parser);
                } else if ("name".equals(tag)) {
                    status.title = readText(parser);
                } else if ("artist".equals(tag)) {
                    status.artist = readText(parser);
                } else if ("album".equals(tag)) {
                    status.album = readText(parser);
                } else if ("volume".equals(tag)) {
                    try {
                        status.volume = Integer.parseInt(readText(parser).trim());
                    } catch (NumberFormatException e) {
                        status.volume = 0;
                    }
                }
            }
            event = parser.next();
        }

        // Prefer title1/2/3 — they are display-friendly for every service type
        if (title1.length() > 0) status.title  = title1;
        if (title2.length() > 0) status.artist = title2;
        if (title3.length() > 0) status.album  = title3;

        return status;
    }

    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String text = "";
        if (parser.next() == XmlPullParser.TEXT) {
            text = parser.getText();
            parser.nextTag();
        }
        return text != null ? text : "";
    }
}
