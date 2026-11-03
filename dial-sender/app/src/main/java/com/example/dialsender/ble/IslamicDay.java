package com.example.dialsender.ble;

import android.content.Context;

import java.util.Calendar;
import java.util.Locale;

/**
 * Day-aware Islamic wording shared by every Islamic face, so they all agree:
 * Jumu'ah on Fridays, Suhoor / Iftar in Ramadan, Eid greetings, and the Qibla.
 * Dates use the Umm al-Qura calendar (ICU).
 */
public final class IslamicDay {
    private IslamicDay() {
    }

    private static final String[] ARABIC = { "الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء" };

    /** {hijriMonth 1–12, hijriDay}, or {0, 0} if ICU is unavailable. */
    static int[] hijri(long now) {
        try {
            android.icu.util.Calendar cal = android.icu.util.Calendar.getInstance(
                    new android.icu.util.ULocale("en@calendar=islamic-umalqura"));
            cal.setTimeInMillis(now);
            return new int[] { cal.get(android.icu.util.Calendar.MONTH) + 1, cal.get(android.icu.util.Calendar.DAY_OF_MONTH) };
        } catch (RuntimeException e) {
            return new int[] { 0, 0 };
        }
    }

    public static boolean isRamadan(long now) {
        return hijri(now)[0] == 9;
    }

    /** Eid al-Fitr 1–3 Shawwal, Eid al-Adha 10–13 Dhu al-Hijjah. */
    public static boolean isEid(long now) {
        int[] h = hijri(now);
        return (h[0] == 10 && h[1] <= 3) || (h[0] == 12 && h[1] >= 10 && h[1] <= 13);
    }

    private static boolean isFriday(long now) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        return c.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY;
    }

    /** English name for a prayer slot today: JUMU'AH on Friday midday, SUHOOR ENDS / IFTAR in Ramadan. */
    public static String name(int prayer, long now) {
        if (prayer == PrayerTimes.DHUHR && isFriday(now))
            return "JUMU'AH";
        if (isRamadan(now)) {
            if (prayer == PrayerTimes.FAJR)
                return "SUHOOR ENDS";
            if (prayer == PrayerTimes.MAGHRIB)
                return "IFTAR";
        }
        return PrayerTimes.NAMES[prayer].toUpperCase(Locale.ENGLISH);
    }

    /** Arabic name for the same slot. */
    public static String arabic(int prayer, long now) {
        if (prayer == PrayerTimes.DHUHR && isFriday(now))
            return "الجمعة";
        if (isRamadan(now)) {
            if (prayer == PrayerTimes.FAJR)
                return "السحور";
            if (prayer == PrayerTimes.MAGHRIB)
                return "الإفطار";
        }
        return ARABIC[prayer];
    }

    /** Small heading above the next prayer: EID MUBARAK, RAMADAN, or NEXT PRAYER. */
    public static String heading(long now) {
        if (isEid(now))
            return "EID MUBARAK";
        if (isRamadan(now))
            return "RAMADAN · NEXT";
        return "NEXT PRAYER";
    }

    /** Arabic counterpart of the heading, or "" when the plain heading is used. */
    public static String headingArabic(long now) {
        if (isEid(now))
            return "عيد مبارك";
        if (isRamadan(now))
            return "رمضان كريم";
        return "";
    }

    /** "QIBLA 118°" for the stored location. */
    public static String qibla(Context c) {
        double[] ll = PrayerScheduler.location(c);
        return "QIBLA " + PrayerTimes.qibla(ll[0], ll[1]) + "°";
    }
}
