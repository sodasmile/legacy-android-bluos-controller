package com.bluesound.legacy;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers BluOS players on the local network using mDNS (Bonjour).
 *
 * BluOS players advertise themselves as _musc._tcp.local services. We send a
 * standard DNS PTR query to the mDNS multicast group (224.0.0.251:5353) and
 * parse the responses to extract each player's friendly name, IP, and port.
 *
 * Uses only MulticastSocket (available since API 1) — no library dependencies.
 * The WifiManager.MulticastLock is required on Android to receive multicast
 * packets over WiFi; without it the radio silently drops them.
 */
class MdnsDiscovery {

    interface Listener {
        void onDiscoveryComplete(List<Player> players);
    }

    static class Player {
        final String name;
        final String host;
        final int    port;

        Player(String name, String host, int port) {
            this.name = name;
            this.host = host;
            this.port = port;
        }

        public String toString() {
            return name + "  (" + host + ")";
        }
    }

    private static final String MDNS_GROUP   = "224.0.0.251";
    private static final int    MDNS_PORT    = 5353;
    private static final String SERVICE_TYPE = "_musc._tcp.local.";
    private static final int    TIMEOUT_MS   = 3000;
    private static final int    RECV_TIMEOUT = 500;   // per-receive, loop until deadline

    // DNS record types we care about
    private static final int TYPE_A   = 1;
    private static final int TYPE_PTR = 12;
    private static final int TYPE_SRV = 33;

    private final Context  context;
    private final Handler  handler;
    private final Listener listener;

    MdnsDiscovery(Context context, Listener listener) {
        this.context  = context.getApplicationContext();
        this.handler  = new Handler();
        this.listener = listener;
    }

    /** Runs discovery on a background thread; calls listener on the main thread when done. */
    void discover() {
        new Thread(new Runnable() {
            public void run() { doDiscover(); }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Discovery logic (runs on background thread)
    // -------------------------------------------------------------------------

    private void doDiscover() {
        // Acquire a multicast lock so Android's WiFi radio passes multicast to us.
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock = wifi.createMulticastLock("BluOSDiscovery");
        lock.acquire();

        // Intermediate maps filled in as we parse packets.
        // Multiple packets may arrive; together they build the full picture.
        final List<String>            instances = new ArrayList<String>();          // PTR answers
        final Map<String, Integer>    ports     = new HashMap<String, Integer>();   // instance → port
        final Map<String, String>     targets   = new HashMap<String, String>();    // instance → target host
        final Map<String, String>     addresses = new HashMap<String, String>();    // host → IP

        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket(MDNS_PORT);
            socket.setReuseAddress(true);
            InetAddress group = InetAddress.getByName(MDNS_GROUP);
            socket.joinGroup(group);
            socket.setSoTimeout(RECV_TIMEOUT);

            // Send PTR query for _musc._tcp.local.
            byte[] query = buildPtrQuery(SERVICE_TYPE);
            socket.send(new DatagramPacket(query, query.length, group, MDNS_PORT));

            // Collect responses until the deadline.
            byte[] buf      = new byte[4096];
            long   deadline = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                    socket.receive(pkt);
                    parseMdnsPacket(pkt.getData(), pkt.getLength(),
                            pkt.getAddress().getHostAddress(),
                            instances, ports, targets, addresses);
                } catch (IOException e) {
                    // Per-receive timeout — keep looping until deadline.
                }
            }

        } catch (IOException e) {
            // Ignore — returns empty list below.
        } finally {
            if (socket != null) socket.close();
            lock.release();
        }

        // Assemble the final player list.
        final List<Player> players = new ArrayList<Player>();
        for (String instance : instances) {
            Integer port   = ports.get(instance);
            String  target = targets.get(instance);
            String  ip     = (target != null) ? addresses.get(target) : null;
            if (port == null) continue;
            if (ip == null)   continue;

            // Strip service type suffix to get the human-readable player name.
            String friendlyName = instance;
            int dot = instance.indexOf("._musc._tcp");
            if (dot > 0) friendlyName = instance.substring(0, dot);

            players.add(new Player(friendlyName, ip, port));
        }

        final List<Player> result = players;
        handler.post(new Runnable() {
            public void run() { listener.onDiscoveryComplete(result); }
        });
    }

    // -------------------------------------------------------------------------
    // DNS packet building
    // -------------------------------------------------------------------------

    /** Builds a minimal mDNS PTR query packet for the given service type name. */
    private static byte[] buildPtrQuery(String name) {
        byte[] encodedName = encodeName(name);
        byte[] packet      = new byte[12 + encodedName.length + 4];

        // Header: transaction ID=0, flags=0 (query), QDCOUNT=1, rest=0.
        packet[5] = 1; // QDCOUNT = 1

        // Question section.
        System.arraycopy(encodedName, 0, packet, 12, encodedName.length);
        int off = 12 + encodedName.length;
        packet[off + 1] = 12; // QTYPE  = PTR (12)
        packet[off + 3] = 1;  // QCLASS = IN  (1)

        return packet;
    }

    /** Encodes a dotted DNS name into the DNS wire label format. */
    private static byte[] encodeName(String name) {
        String[] labels   = name.split("\\.");
        int      totalLen = 1; // root null byte
        for (String label : labels) {
            if (label.length() > 0) totalLen += 1 + label.length();
        }
        byte[] result = new byte[totalLen];
        int pos = 0;
        for (String label : labels) {
            if (label.length() > 0) {
                result[pos++] = (byte) label.length();
                byte[] lb = label.getBytes();
                System.arraycopy(lb, 0, result, pos, lb.length);
                pos += lb.length;
            }
        }
        // result[pos] == 0 already (default byte value = root label).
        return result;
    }

    // -------------------------------------------------------------------------
    // DNS packet parsing
    // -------------------------------------------------------------------------

    private static void parseMdnsPacket(byte[] data, int len, String senderIp,
            List<String> instances, Map<String, Integer> ports,
            Map<String, String> targets, Map<String, String> addresses) {
        if (len < 12) return;

        int qdCount = u16(data, 4);
        int anCount = u16(data, 6);
        int nsCount = u16(data, 8);
        int arCount = u16(data, 10);

        int[] offset = {12};

        // Skip questions.
        for (int i = 0; i < qdCount && offset[0] < len; i++) {
            decodeName(data, len, offset);
            offset[0] += 4; // QTYPE + QCLASS
        }

        // Parse all resource records (answers + authority + additional).
        int total = anCount + nsCount + arCount;
        for (int i = 0; i < total && offset[0] < len; i++) {
            parseRR(data, len, offset, senderIp, instances, ports, targets, addresses);
        }
    }

    private static void parseRR(byte[] data, int len, int[] offset, String senderIp,
            List<String> instances, Map<String, Integer> ports,
            Map<String, String> targets, Map<String, String> addresses) {
        if (offset[0] >= len) return;

        String name  = decodeName(data, len, offset);
        if (offset[0] + 10 > len) return;

        int type  = u16(data, offset[0]);
        // class and TTL skipped (bytes +2..+7)
        int rdLen = u16(data, offset[0] + 8);
        offset[0] += 10;

        int rdStart = offset[0];
        offset[0]   = rdStart + rdLen; // advance past this record regardless

        if (type == TYPE_PTR) {
            int[] ptrOff   = {rdStart};
            String instance = decodeName(data, len, ptrOff);
            if (instance.length() > 0 && !instances.contains(instance)) {
                instances.add(instance);
            }

        } else if (type == TYPE_SRV) {
            // priority(2) + weight(2) + port(2) + target name
            if (rdStart + 6 > len) return;
            int    port   = u16(data, rdStart + 4);
            int[]  srvOff = {rdStart + 6};
            String target = decodeName(data, len, srvOff);
            ports.put(name, port);
            if (target.length() > 0) targets.put(name, target);

        } else if (type == TYPE_A) {
            // 4-byte IPv4 address
            if (rdStart + 4 > len) return;
            String ip = (data[rdStart] & 0xFF) + "." +
                        (data[rdStart+1] & 0xFF) + "." +
                        (data[rdStart+2] & 0xFF) + "." +
                        (data[rdStart+3] & 0xFF);
            addresses.put(name, ip);
            // Also index by the sender IP as a direct fallback key.
            addresses.put(senderIp, ip);
        }
    }

    /**
     * Decodes a DNS name (with pointer compression) starting at offset[0].
     * Updates offset[0] to point to the byte immediately after the name.
     */
    private static String decodeName(byte[] data, int len, int[] offset) {
        StringBuilder sb       = new StringBuilder();
        int           pos      = offset[0];
        boolean       jumped   = false;
        int           afterPtr = -1;
        int           safety   = 20;

        while (pos < len && safety-- > 0) {
            int b = data[pos] & 0xFF;
            if (b == 0) {
                pos++;
                break;
            } else if ((b & 0xC0) == 0xC0) {
                // Compressed pointer: next 14 bits are the offset to jump to.
                if (pos + 1 >= len) break;
                if (!jumped) {
                    afterPtr = pos + 2; // resume here after the full name is decoded
                    jumped   = true;
                }
                pos = ((b & 0x3F) << 8) | (data[pos + 1] & 0xFF);
            } else {
                // Regular label: b is the label length.
                pos++;
                if (pos + b > len) break;
                if (sb.length() > 0) sb.append('.');
                sb.append(new String(data, pos, b));
                pos += b;
            }
        }

        offset[0] = jumped ? afterPtr : pos;
        return sb.toString();
    }

    /** Reads an unsigned 16-bit big-endian integer from data[offset]. */
    private static int u16(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    }
}
