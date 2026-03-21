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
