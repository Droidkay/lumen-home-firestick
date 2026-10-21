package com.droidkay.lumen;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/** Current weather from Open-Meteo (free, no account or key). Cached so the widget shows instantly. */
final class Weather {
    private Weather() {}

    static final class City {
        final String name;
        final double lat, lon;

        City(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    static final City[] CITIES = {
        new City("Lagos", 6.4541, 3.3947),
        new City("Abuja", 9.0765, 7.3986),
        new City("Port Harcourt", 4.8156, 7.0498),
        new City("Ibadan", 7.3775, 3.9470),
        new City("Kano", 12.0022, 8.5920),
        new City("Enugu", 6.4584, 7.5464),
        new City("Benin City", 6.3350, 5.6037),
        new City("Accra", 5.6037, -0.1870),
        new City("Nairobi", -1.2921, 36.8219),
        new City("Johannesburg", -26.2041, 28.0473),
        new City("London", 51.5072, -0.1276),
        new City("Dubai", 25.2048, 55.2708),
        new City("New York", 40.7128, -74.0060),
        new City("Houston", 29.7604, -95.3698),
        new City("Toronto", 43.6532, -79.3832),
    };

    static final class Now {
        final int city;
        final double temp, hi, lo;
        final int code;
        final boolean day;
        final long fetched;

        Now(int city, double temp, int code, boolean day, double hi, double lo, long fetched) {
            this.city = city;
            this.temp = temp;
            this.code = code;
            this.day = day;
            this.hi = hi;
            this.lo = lo;
            this.fetched = fetched;
        }
    }

    static Now cached(Context c) {
        String s = Prefs.getStr(c, "weatherCache", null);
        if (s == null) return null;
        try {
            String[] p = s.split("\\|");
            return new Now(Integer.parseInt(p[0]), Double.parseDouble(p[1]), Integer.parseInt(p[2]),
                    "1".equals(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]), Long.parseLong(p[6]));
        } catch (Exception e) {
            return null;
        }
    }

    /** Network call: run on a background thread. Returns null on failure. */
    static Now fetch(Context c, int cityIdx) {
        City city = CITIES[cityIdx];
        HttpURLConnection con = null;
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                            + "&current=temperature_2m,weather_code,is_day"
                            + "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                    city.lat, city.lon);
            con = (HttpURLConnection) new URL(url).openConnection();
            con.setConnectTimeout(8000);
            con.setReadTimeout(8000);
            con.setRequestProperty("User-Agent", "LumenHome/1.1");
            if (con.getResponseCode() != 200) return null;
            InputStream in = con.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            in.close();
            JSONObject j = new JSONObject(bo.toString("UTF-8"));
            JSONObject cur = j.getJSONObject("current");
            JSONObject daily = j.optJSONObject("daily");
            double hi = Double.NaN, lo = Double.NaN;
            if (daily != null) {
                hi = daily.getJSONArray("temperature_2m_max").optDouble(0, Double.NaN);
                lo = daily.getJSONArray("temperature_2m_min").optDouble(0, Double.NaN);
            }
            Now now = new Now(cityIdx, cur.getDouble("temperature_2m"), cur.getInt("weather_code"),
                    cur.optInt("is_day", 1) == 1, hi, lo, System.currentTimeMillis());
            Prefs.setStr(c, "weatherCache", cityIdx + "|" + now.temp + "|" + now.code + "|" + (now.day ? 1 : 0)
                    + "|" + now.hi + "|" + now.lo + "|" + now.fetched);
            return now;
        } catch (Throwable t) {
            return null;
        } finally {
            if (con != null) con.disconnect();
        }
    }

    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Foggy";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return code >= 65 ? "Heavy rain" : "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "—";
    }

    static int icon(int code, boolean day) {
        if (code <= 1) return day ? IconView.SUN : IconView.MOON;
        if (code == 2) return IconView.PARTLY;
        if (code == 3) return IconView.CLOUD;
        if (code == 45 || code == 48) return IconView.FOG;
        if (code >= 95) return IconView.STORM;
        return IconView.RAIN;
    }
}
