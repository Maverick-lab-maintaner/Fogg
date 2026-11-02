package com.example.dialsender.ble;

import android.graphics.Bitmap;

/**
 * Optional bridge to Bluetrum's image encoder used for HryFine custom faces.
 *
 * The encoder is a proprietary native library (libabpartool.so) with a tiny JNI
 * class com.bluetrum.abpartool.ParTool. Neither is part of this repository. A
 * local build can add them (copy the .so from your own HryFine install into
 * app/src/main/jniLibs/arm64-v8a/ and add the ParTool class, see
 * docs in WHAT_WAS_DONE_AND_NEXT.md). Without them, everything else works and
 * custom faces report "image encoder unavailable".
 */
final class ParEncoder {
    private static final String CLASS = "com.bluetrum.abpartool.ParTool";

    private ParEncoder() {
    }

    static boolean isAvailable() {
        try {
            Object ok = Class.forName(CLASS).getMethod("isAvailable").invoke(null);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Null if the encoder is missing or fails. */
    static byte[] bitmapToPar(Bitmap bmp) {
        try {
            return (byte[]) Class.forName(CLASS).getMethod("bitmapToPar", Bitmap.class).invoke(null, bmp);
        } catch (Throwable t) {
            return null;
        }
    }
}
