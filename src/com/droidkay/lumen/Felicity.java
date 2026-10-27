package com.droidkay.lumen;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Talks to the Felicity inverter's built-in WiFi over the home network (TCP port 53970).
 * Read-only: it only asks for data, it never changes inverter settings.
 */
final class Felicity {
    private Felicity() {}

    static final int PORT = 53970;
    static final String CMD_REAL = "wifilocalMonitor:get dev real infor";
    static final String CMD_BASIC = "wifilocalMonitor:get dev basice infor";

    /** Sends one command and returns the text reply (several JSON chunks may be glued). */
    static String ask(String host, String command, int connectMs) throws Exception {
        Socket s = new Socket();
        try {
            s.connect(new InetSocketAddress(host, PORT), connectMs);
            s.setSoTimeout(3000);
            OutputStream os = s.getOutputStream();
            os.write(command.getBytes("US-ASCII"));
            os.flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            while (bo.size() < 128 * 1024) {
                int n;
                try {
                    n = in.read(buf);
                } catch (SocketTimeoutException e) {
                    break;
                }
                if (n <= 0) break;
                bo.write(buf, 0, n);
                s.setSoTimeout(500); // after the first chunk, stop once it goes quiet
            }
            if (bo.size() == 0) throw new Exception("No reply from inverter");
            return new String(bo.toByteArray(), "US-ASCII").trim();
        } finally {
            try {
                s.close();
            } catch (Exception ignored) {
            }
        }
    }

    static Reading read(String host) throws Exception {
        String raw = ask(host, CMD_REAL, 3000);
        Reading r = Reading.parse(raw);
        if (r == null) throw new Exception("Unexpected reply from inverter");
        return r;
    }

    // ------------------------------------------------------------ discovery

    /** This phone's IPv4 addresses on Wi-Fi/LAN. */
    static List<Inet4Address> localAddresses() {
        ArrayList<Inet4Address> out = new ArrayList<Inet4Address>();
        try {
            Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
            if (e == null) return out;
            for (NetworkInterface ni : Collections.list(e)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String n = ni.getName();
                if (n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("tun")) continue; // mobile data / VPN
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) out.add((Inet4Address) a);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    interface Progress {
        void onProgress(int done, int total);
    }

    /** Scans the local /24 network for a Felicity inverter. Returns its IP or null. */
    static String discover(final Progress p) {
        List<Inet4Address> mine = localAddresses();
        if (mine.isEmpty()) return null;
        final ArrayList<String> candidates = new ArrayList<String>();
        for (Inet4Address a : mine) {
            byte[] b = a.getAddress();
            String prefix = (b[0] & 0xFF) + "." + (b[1] & 0xFF) + "." + (b[2] & 0xFF) + ".";
            for (int h = 1; h < 255; h++) {
                String ip = prefix + h;
                if (!ip.equals(a.getHostAddress()) && !candidates.contains(ip)) candidates.add(ip);
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(48);
        final int total = candidates.size();
        final int[] done = {0};
        ArrayList<Future<String>> fs = new ArrayList<Future<String>>();
        for (final String ip : candidates) {
            fs.add(pool.submit(new java.util.concurrent.Callable<String>() {
                @Override
                public String call() {
                    String found = null;
                    Socket s = new Socket();
                    try {
                        s.connect(new InetSocketAddress(ip, PORT), 600);
                        found = ip;
                    } catch (Exception ignored) {
                    } finally {
                        try {
                            s.close();
                        } catch (Exception ignored) {
                        }
                        synchronized (done) {
                            done[0]++;
                            if (p != null) p.onProgress(done[0], total);
                        }
                    }
                    return found;
                }
            }));
        }
        pool.shutdown();
        String result = null;
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS);
            for (Future<String> f : fs) {
                String ip = f.isDone() ? f.get() : null;
                if (ip == null) continue;
                try {
                    if (read(ip) != null) {
                        result = ip;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        pool.shutdownNow();
        return result;
    }
}
