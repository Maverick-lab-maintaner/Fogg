package com.example.dialsender.ble;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Offline prayer times from the sun's position (standard astronomical
 * approximation, accurate to about a minute). Fajr 18 deg / Isha 17 deg (Muslim
 * World League), Maghrib at sunset, Asr by shadow factor 1 (standard) or 2
 * (Hanafi). Above ~48 deg latitude twilight can last all night in summer, so
 * Fajr/Isha fall back to the angle-based portion of the night.
 */
public final class PrayerTimes {
    public static final String[] NAMES = { "Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha" };
    public static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;

    private static final double FAJR_ANGLE = 18, ISHA_ANGLE = 17;
    private static final double KAABA_LAT = 21.4225, KAABA_LON = 39.8262;

    private PrayerTimes() {
    }

    /** Epoch millis for the six times on the local calendar day of {@code day}. */
    public static long[] forDay(Calendar day, double lat, double lon, boolean hanafi) {
        Calendar noon = (Calendar) day.clone();
        noon.set(Calendar.HOUR_OF_DAY, 12);
        noon.set(Calendar.MINUTE, 0);
        noon.set(Calendar.SECOND, 0);
        noon.set(Calendar.MILLISECOND, 0);
        TimeZone tz = noon.getTimeZone();
        double tzHours = tz.getOffset(noon.getTimeInMillis()) / 3600000.0;

        double jd = noon.getTimeInMillis() / 86400000.0 + 2440587.5;
        double d = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * d, 360);
        double q = fix(280.459 + 0.98564736 * d, 360);
        double l = fix(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360);
        double e = 23.439 - 0.00000036 * d;
        double ra = fix(atan2(cos(e) * sin(l), cos(l)) / 15.0, 24);
        double decl = asin(sin(e) * sin(l));
        double eqt = q / 15.0 - ra;

        double dhuhr = 12 + tzHours - lon / 15.0 - eqt;
        double sunArc = arc(0.833, lat, decl);
        double sunrise = dhuhr - sunArc, sunset = dhuhr + sunArc;
        double fajr = dhuhr - arc(FAJR_ANGLE, lat, decl);
        double isha = dhuhr + arc(ISHA_ANGLE, lat, decl);
        double asrAlt = acot((hanafi ? 2 : 1) + Math.tan(Math.toRadians(Math.abs(lat - decl))));
        double asr = dhuhr + Math.toDegrees(Math.acos(
                (Math.sin(asrAlt) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat)))) / 15.0;

        double night = 24 - (sunset - sunrise);
        double fajrMax = FAJR_ANGLE / 60.0 * night, ishaMax = ISHA_ANGLE / 60.0 * night;
        if (Double.isNaN(fajr) || sunrise - fajr > fajrMax)
            fajr = sunrise - fajrMax;
        if (Double.isNaN(isha) || isha - sunset > ishaMax)
            isha = sunset + ishaMax;

        double[] hours = { fajr, sunrise, dhuhr, asr, sunset, isha };
        long[] out = new long[6];
        for (int i = 0; i < 6; i++) {
            // Wall-clock time, rounded to the minute as timetables are.
            int minutes = Math.max(0, Math.min(1439, (int) Math.round(hours[i] * 60)));
            Calendar c = (Calendar) noon.clone();
            c.set(Calendar.HOUR_OF_DAY, minutes / 60);
            c.set(Calendar.MINUTE, minutes % 60);
            out[i] = c.getTimeInMillis();
        }
        return out;
    }

    /** Degrees clockwise from true north toward the Kaaba. */
    public static int qibla(double lat, double lon) {
        double dl = Math.toRadians(KAABA_LON - lon);
        double y = Math.sin(dl);
        double x = Math.cos(Math.toRadians(lat)) * Math.tan(Math.toRadians(KAABA_LAT))
                - Math.sin(Math.toRadians(lat)) * Math.cos(dl);
        return (int) Math.round(fix(Math.toDegrees(Math.atan2(y, x)), 360));
    }

    /** Hours from noon until the sun is {@code angle} degrees below the horizon. */
    private static double arc(double angle, double lat, double decl) {
        double c = (-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat));
        return Math.toDegrees(Math.acos(c)) / 15.0;
    }

    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double cos(double deg) { return Math.cos(Math.toRadians(deg)); }
    private static double asin(double x) { return Math.toDegrees(Math.asin(x)); }
    private static double atan2(double y, double x) { return Math.toDegrees(Math.atan2(y, x)); }
    private static double acot(double x) { return Math.atan(1 / x); }

    private static double fix(double a, double b) {
        a = a - b * Math.floor(a / b);
        return a < 0 ? a + b : a;
    }
}
