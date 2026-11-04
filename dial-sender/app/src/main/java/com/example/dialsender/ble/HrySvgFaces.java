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

    /** Where the watch draws its clock on vector faces: a ~200×88 px block, centred. */
    static final int TIME_X = 70, TIME_Y = 100, TIME_H = 88;

    /** Null if the SVG cannot be read. */
    public static HryFaces.Face render(Context ctx, int index, int w, int h) {
        return render(ctx, index, w, h, null, 0);
    }

    /** Blue and brown designs keep the soft band behind the clock; the others leave it clear. */
    private static boolean keepsBand(Bg bg) {
        return bg == Bg.NAVY || bg == Bg.MIDNIGHT || bg == Bg.NIGHT || bg == Bg.BLACK || bg == Bg.BURGUNDY;
    }

    /** With {@code times}, the face carries the next prayer, dates and Qibla above the clock. */
    public static HryFaces.Face render(Context ctx, int index, int w, int h, long[] times, long now) {
        Design d = DESIGNS[index];
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        background(c, w, h, d.bg);

        boolean info = times != null, band = keepsBand(d.bg);
        float bandTop = TIME_Y - 10, bandBottom = TIME_Y + TIME_H + 6;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        if (d.fill) {
            // A full pattern, faint behind everything.
            RectF area = new RectF(0, 0, w, h);
            Bitmap mask = inkMask(ctx, d.file, w, h, "xMidYMid slice");
            if (mask == null)
                return null;
            ink(p, d, area);
            p.setAlpha(120);
            Bitmap layer = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas lc = new Canvas(layer);
            lc.drawBitmap(mask, area.left, area.top, p);
            if (!band) {
                // No band here: clear the art itself behind the text and the clock.
                float top = info ? 26 : bandTop - 16;
                Paint clear = new Paint(Paint.ANTI_ALIAS_FLAG);
                clear.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
                clear.setShader(new LinearGradient(0, top, 0, bandBottom + 16,
                        new int[] { 0x00000000, 0xFF000000, 0xFF000000, 0x00000000 }, new float[] { 0f, 0.12f, 0.88f, 1f },
                        Shader.TileMode.CLAMP));
                lc.drawRect(0, top, w, bandBottom + 16, clear);
            }
            c.drawBitmap(layer, 0, 0, null);
            layer.recycle();
        } else {
            // Calligraphy, motif or silhouette: crisp, in the space under the clock.
            RectF area = d.bottom ? new RectF(0, bandBottom + 4, w, h)
                    : new RectF(16, bandBottom + 8, w - 16, h - 10);
            Bitmap mask = inkMask(ctx, d.file, Math.round(area.width()), Math.round(area.height()),
                    d.bottom ? "xMidYMax meet" : "xMidYMid meet");
            if (mask == null)
                return null;
            ink(p, d, area);
            c.drawBitmap(mask, area.left, area.top, p);
        }

        if (band) {
            Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
            glass.setShader(new LinearGradient(0, bandTop, 0, bandBottom,
                    new int[] { 0x00000000, 0x8C000000, 0x8C000000, 0x00000000 }, new float[] { 0f, 0.18f, 0.82f, 1f },
                    Shader.TileMode.CLAMP));
            c.drawRect(0, bandTop, w, bandBottom, glass);
            Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
            rule.setShader(HryFaces.foil(w * 0.25f, 0, w * 0.75f, 0));
            c.drawRect(w * 0.28f, bandTop + 2, w * 0.72f, bandTop + 3.2f, rule);
            c.drawRect(w * 0.28f, bandBottom - 3.2f, w * 0.72f, bandBottom - 2, rule);
        }
        if (info)
            HryPremiumFaces.overlayTop(ctx, c, w, times, now);
        else
            HryFaces.crescent(c, w / 2f, bandTop - 26, 11, HryFaces.foil(w / 2f - 14, bandTop - 40, w / 2f + 14, bandTop - 12));

        return new HryFaces.Face(b, new int[] { TIME_X, TIME_Y, HryFaces.rgb565(0xFFFFFFFF), 0, 0 });
    }

    private static void ink(Paint p, Design d, RectF area) {
        switch (d.ink) {
            case GOLD:
                p.setShader(HryFaces.foil(area.left, area.top, area.right, area.bottom));
                break;
            case IVORY:
                p.setShader(null);
                p.setColor(0xFFF3EAD3);
                break;
            default:
                p.setShader(null);
                p.setColor(d.bg == Bg.SUNSET ? 0xFF120A10 : 0xFF050A16);
        }
    }

    /** Renders the SVG into an ALPHA_8 mask where dark/opaque strokes are ink. */
    private static Bitmap inkMask(Context ctx, String file, int tw, int th, String aspect) {
        try {
            SVG svg = SVG.getFromAsset(ctx.getAssets(), "hryfaces/svg/" + file);
            // Size the document itself: a viewport alone left large documents unscaled and cropped.
            if (svg.getDocumentViewBox() == null) {
                float dw = svg.getDocumentWidth(), dh = svg.getDocumentHeight();
                if (dw > 0 && dh > 0)
                    svg.setDocumentViewBox(0, 0, dw, dh);
            }
            svg.setDocumentPreserveAspectRatio(com.caverock.androidsvg.PreserveAspectRatio.of(aspect));
            svg.setDocumentWidth(tw);
            svg.setDocumentHeight(th);
            Bitmap rgba = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
            svg.renderToCanvas(new Canvas(rgba));
            int[] px = new int[tw * th];
            rgba.getPixels(px, 0, tw, 0, 0, tw, th);
            // A drawing with a coloured backdrop: key that colour out (sampled at the corner).
            // "Backdrop" = the opaque pixels fill their bounding box; margins from fitting don't count.
            int minX = tw, minY = th, maxX = -1, maxY = -1, opaque = 0, corner = 0;
            for (int i = 0; i < px.length; i++) {
                if (Color.alpha(px[i]) <= 200)
                    continue;
                if (opaque++ == 0)
                    corner = px[i];
                int x = i % tw, y = i / tw;
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            long box = (long) (maxX - minX + 1) * (maxY - minY + 1);
            boolean keyed = opaque > 0 && opaque > box * 0.92
                    && (Color.red(corner) + Color.green(corner) + Color.blue(corner)) < 660;
            for (int i = 0; i < px.length; i++) {
                int v = px[i];
                float a = Color.alpha(v) / 255f;
                float ink;
                if (keyed) {
                    float dist = Math.abs(Color.red(v) - Color.red(corner)) + Math.abs(Color.green(v) - Color.green(corner))
                            + Math.abs(Color.blue(v) - Color.blue(corner));
                    ink = a * Math.min(1f, dist / 150f);
                } else {
                    float luma = (0.299f * Color.red(v) + 0.587f * Color.green(v) + 0.114f * Color.blue(v)) / 255f;
                    ink = a * Math.min(1f, Math.max(0f, (0.92f - luma) / 0.6f));
                }
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
                p.setShader(new LinearGradient(0, 0, 0, h, new int[] { 0xFF2A1740, 0xFF7A2F55, 0xFFD9683F, 0xFFF2A65A },
                        new float[] { 0f, .35f, .7f, 1f }, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, w, h, p);
                Paint sun = new Paint(Paint.ANTI_ALIAS_FLAG);
                sun.setColor(0xFFFFE2A8);
                c.drawCircle(w * .5f, h * .80f, 24, sun);
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
