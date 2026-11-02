package com.example.dialsender.ble;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import com.caverock.androidsvg.RenderOptions;
import com.caverock.androidsvg.SVG;

/**
 * Vector faces: openly licensed SVG artwork (assets/hryfaces/svg, credits.json)
 * rendered at the watch's exact pixel size, so lines and calligraphy stay sharp.
 * Each SVG is turned into an ink mask (dark strokes = ink) and painted with gold
 * foil, ivory or a solid silhouette over a drawn background. The top
 * {@link HryFaces#CLOCK_BAND} stays clear for the watch's own clock.
 */
public final class HrySvgFaces {
    private HrySvgFaces() {
    }

    private enum Bg { EMERALD, NAVY, BLACK, BURGUNDY, TEAL, MIDNIGHT, PURPLE, SUNSET, NIGHT }
    private enum Ink { GOLD, IVORY, SILHOUETTE }

    private static final class Design {
        final String name, file;
        final Bg bg;
        final Ink ink;
        /** fit = centred in the art area; fill = tiled/cover over the whole screen at low strength. */
        final boolean fill, bottom;

        Design(String name, String file, Bg bg, Ink ink, boolean fill, boolean bottom) {
            this.name = name;
            this.file = file;
            this.bg = bg;
            this.ink = ink;
            this.fill = fill;
            this.bottom = bottom;
        }
    }

    private static final Design[] DESIGNS = {
            new Design("Bismillah Gold", "bismillah_round.svg", Bg.EMERALD, Ink.GOLD, false, false),
            new Design("Bismillah Night", "bismillah_wide.svg", Bg.NAVY, Ink.IVORY, false, false),
            new Design("Bismillah Arch", "bismillah_arch.svg", Bg.BLACK, Ink.GOLD, false, false),
            new Design("Kufic Bismillah", "bismillah_kufic.svg", Bg.BURGUNDY, Ink.GOLD, false, false),
            new Design("Square Kufic", "kufic_muhammad.svg", Bg.BLACK, Ink.GOLD, false, false),
            new Design("Star Lattice", "pattern_star.svg", Bg.TEAL, Ink.GOLD, true, false),
            new Design("Star Rosette", "star_draft.svg", Bg.MIDNIGHT, Ink.GOLD, false, false),
            new Design("Interlace", "interlace.svg", Bg.PURPLE, Ink.GOLD, false, false),
            new Design("Octagram", "octagram.svg", Bg.EMERALD, Ink.GOLD, false, false),
            new Design("Mosque Sunset", "mosque_a.svg", Bg.SUNSET, Ink.SILHOUETTE, false, true),
            new Design("Mosque Night", "mosque_b.svg", Bg.NIGHT, Ink.SILHOUETTE, false, true),
    };

    public static int count() {
        return DESIGNS.length;
    }

    public static String name(int i) {
        return DESIGNS[i].name;
    }

    /** Null if the SVG cannot be read. */
    public static HryFaces.Face render(Context ctx, int index, int w, int h) {
        Design d = DESIGNS[index];
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        background(c, w, h, d.bg);

        int band = HryFaces.CLOCK_BAND;
        RectF area;
        if (d.fill)
            area = new RectF(0, 0, w, h);
        else if (d.bottom)
            area = new RectF(0, band, w, h);
        else {
            float side = Math.min(w - 36, h - band - 24);
            float cx = w / 2f, cy = band + (h - band) / 2f;
            area = new RectF(cx - side / 2f, cy - side / 2f, cx + side / 2f, cy + side / 2f);
            if (d.file.startsWith("bismillah_wide") || d.file.startsWith("bismillah_arch"))
                area = new RectF(14, band + 6, w - 14, h - 14);
        }
        Bitmap mask = inkMask(ctx, d.file, Math.round(area.width()), Math.round(area.height()),
                d.fill ? "xMidYMid slice" : d.bottom ? "xMidYMax meet" : "xMidYMid meet");
        if (mask == null)
            return null;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        switch (d.ink) {
            case GOLD:
                p.setShader(HryFaces.foil(area.left, area.top, area.right, area.bottom));
                break;
            case IVORY:
                p.setColor(0xFFF3EAD3);
                break;
            default:
                p.setColor(d.bg == Bg.SUNSET ? 0xFF120A10 : 0xFF050A16);
        }
        if (d.fill) {
            p.setAlpha(110);   // a lattice behind the clock, not over it
            c.drawBitmap(mask, area.left, area.top, p);
            Paint shade = new Paint();
            shade.setShader(new LinearGradient(0, 0, 0, band + 40, 0xE0061518, 0x00061518, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, band + 40, shade);
            Paint medal = new Paint(Paint.ANTI_ALIAS_FLAG);
            medal.setColor(0xF0061518);
            float cy = band + (h - band) / 2f;
            c.drawCircle(w / 2f, cy, 34, medal);
            HryFaces.crescent(c, w / 2f, cy, 18, HryFaces.foil(w / 2f - 20, cy - 20, w / 2f + 20, cy + 20));
        } else {
            c.drawBitmap(mask, area.left, area.top, p);
        }
        if (d.bg == Bg.NIGHT)
            HryFaces.crescent(c, w - 40, band + 22, 12, HryFaces.foil(w - 54, band + 8, w - 26, band + 36));
        return new HryFaces.Face(b, HryFaces.clock(d.bg == Bg.SUNSET ? 0xFF1A0E0A : 0xFFFFFFFF, 1));
    }

    /** Renders the SVG into an ALPHA_8 mask where dark/opaque strokes are ink. */
    private static Bitmap inkMask(Context ctx, String file, int tw, int th, String aspect) {
        try {
            SVG svg = SVG.getFromAsset(ctx.getAssets(), "hryfaces/svg/" + file);
            if (svg.getDocumentViewBox() == null) {
                float dw = svg.getDocumentWidth(), dh = svg.getDocumentHeight();
                if (dw > 0 && dh > 0)
                    svg.setDocumentViewBox(0, 0, dw, dh);
            }
            Bitmap rgba = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            svg.renderToCanvas(new Canvas(rgba), new RenderOptions().viewPort(0, 0, tw, th)
                    .preserveAspectRatio(com.caverock.androidsvg.PreserveAspectRatio.of(aspect)));
            int[] px = new int[tw * th];
            rgba.getPixels(px, 0, tw, 0, 0, tw, th);
            for (int i = 0; i < px.length; i++) {
                int v = px[i];
                float a = Color.alpha(v) / 255f;
                float luma = (0.299f * Color.red(v) + 0.587f * Color.green(v) + 0.114f * Color.blue(v)) / 255f;
                float ink = a * Math.min(1f, Math.max(0f, (0.92f - luma) / 0.6f));
                px[i] = Color.argb(Math.round(ink * 255), 0, 0, 0);
            }
            rgba.setPixels(px, 0, tw, 0, 0, tw, th);
            Bitmap mask = rgba.extractAlpha();
            rgba.recycle();
            return mask;
        } catch (Exception e) {
            return null;
        }
    }

    private static void background(Canvas c, int w, int h, Bg bg) {
        switch (bg) {
            case EMERALD: HryFaces.radialBg(c, w, h, w * .5f, h * .6f, 0xFF136B4F, 0xFF021610); HryFaces.starPattern(c, w, h, 0x10E3C267, 34); break;
            case NAVY: HryFaces.radialBg(c, w, h, w * .5f, h * .6f, 0xFF1D3560, 0xFF040A18); break;
            case BLACK: HryFaces.radialBg(c, w, h, w * .5f, h * .62f, 0xFF2A2620, 0xFF050505); break;
            case BURGUNDY: HryFaces.radialBg(c, w, h, w * .5f, h * .6f, 0xFF6E1A2A, 0xFF16040A); break;
            case TEAL: HryFaces.radialBg(c, w, h, w * .5f, h * .6f, 0xFF0F5559, 0xFF041517); break;
            case MIDNIGHT: HryFaces.radialBg(c, w, h, w * .5f, h * .6f, 0xFF1B2447, 0xFF04060F); HryFaces.starPattern(c, w, h, 0x0CE3C267, 30); break;
            case PURPLE: HryFaces.radialBg(c, w, h, w * .5f, h * .6f, 0xFF43205E, 0xFF0C0514); break;
            case SUNSET: {
                Paint p = new Paint();
                p.setShader(new LinearGradient(0, 0, 0, h, new int[] { 0xFFF6C27A, 0xFFF08A4B, 0xFFB8454A, 0xFF4A1E3A },
                        new float[] { 0f, .45f, .75f, 1f }, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                Paint sun = new Paint(Paint.ANTI_ALIAS_FLAG);
                sun.setColor(0xFFFFE2A8);
                c.drawCircle(w * .5f, h * .62f, 26, sun);
                break;
            }
            default: { // NIGHT
                Paint p = new Paint();
                p.setShader(new LinearGradient(0, 0, 0, h, new int[] { 0xFF050B1F, 0xFF16285A, 0xFF3A5A8C },
                        new float[] { 0f, .6f, 1f }, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                java.util.Random r = new java.util.Random(7);
                Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
                for (int i = 0; i < 70; i++) {
                    star.setColor(Color.argb(90 + r.nextInt(150), 255, 255, 255));
                    c.drawCircle(r.nextFloat() * w, r.nextFloat() * h * .7f, r.nextFloat() * 1.1f + .3f, star);
                }
            }
        }
    }
}
