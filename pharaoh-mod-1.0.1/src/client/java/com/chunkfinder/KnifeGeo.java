package com.chunkfinder;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Geometry and animation of the Butterfly Knife, with no Minecraft classes in it, so the maths can be tested anywhere.
 *
 * The knife is a small pixel-art model (blade, two handles, a light pin) that is pushed out into thin blocks, the same
 * way Minecraft turns a flat item picture into a 3D item. It is held in a fist that is drawn from the player's own skin
 * (the arm part of the skin picture), so the hand always matches whatever skin they use. Everything is built here in
 * code, so no texture pack is needed.
 *
 * Camera space: x to the right, y up, the camera looks down the negative z axis (objects in front have a negative z).
 */
final class KnifeGeo {
    private KnifeGeo() {}

    // ------------------------------------------------------------------ layout (matched to the clip)

    /** Size of one knife pixel in blocks. */
    static final float U = 0.01475f;
    /** Distance of the knife pivot in front of the camera. */
    static final float PIVOT_Z = 0.60f;
    /** Where the pivot sits on the screen at rest (−1..1 each way). */
    static final float PIVOT_NDC_X = 0.454f, PIVOT_NDC_Y = -0.316f;
    /** Distance of the fist in front of the camera, and where its end face sits on the screen. */
    static final float HAND_Z = 0.50f;
    static final float HAND_NDC_X = 0.524f, HAND_NDC_Y = -0.725f;
    /** tan(35 degrees): Minecraft draws the hand with a 70 degree vertical view. */
    static final float TAN_HALF_FOV = 0.70021f;
    /** How many rows of the arm (counted back from the hand) the fist shows behind its end face. */
    static final int FIST_LEN = 3;
    /** Size of one fist pixel in blocks. */
    static final float HU = 0.039f;

    /**
     * Seconds the dip takes, how long the knife then waits at the bottom edge of the screen, the flip takes, and all
     * three together. Timed against the clip: the knife folds and drops in about 0.09 s, waits at the bottom for about 0.2 s,
     * then shoots back up (about 0.3 s after the hit) and flips shut while it climbs to its place, all done about 0.87 s after the hit.
     */
    static final float DIP_T = 0.09f, HIDE_T = 0.21f, FLIP_T = 0.57f, TOTAL_T = DIP_T + HIDE_T + FLIP_T;
    /** A new swing only cuts the animation short from this many seconds in, once the knife has popped up and is flipping. */
    static final float CHAIN_FROM = 0.40f;

    // ------------------------------------------------------------------ colours

    static final class Palette {
        int tip, edge, lite, main, shade, base;          // blade
        int hBase, hEdge, hHole, hStud, pin;             // handles
        int glow;                                        // outline
    }

    static Palette neon() {
        Palette p = new Palette();
        // colours read off the clip's calm frames: white edge, teal light strip, cyan body, dark teal right strip, steel base
        p.tip = 0xFFFFFF; p.edge = 0xE2F4FD; p.lite = 0x68D2DE; p.main = 0x56C9E4; p.shade = 0x3392B0; p.base = 0x3D86AB;
        p.hBase = 0x2C2042; p.hEdge = 0x3E2D5C; p.hHole = 0x181224; p.hStud = 0x52E6FF; p.pin = 0xC8B8DC;
        p.glow = 0xA23CFF;
        return p;
    }

    /** Colours taken from the sword material (used when the neon look is switched off). */
    static Palette material(String id) {
        int blade, handle;
        if (id.startsWith("wooden")) { blade = 0xB88B52; handle = 0x4A3320; }
        else if (id.startsWith("stone")) { blade = 0xA3A3A3; handle = 0x4A3A28; }
        else if (id.startsWith("golden")) { blade = 0xF6D24A; handle = 0x6A4A1A; }
        else if (id.startsWith("diamond")) { blade = 0x5ADCF3; handle = 0x1F3D4A; }
        else if (id.startsWith("netherite")) { blade = 0x5A5360; handle = 0x2A2227; }
        else { blade = 0xD2D8DE; handle = 0x363A42; }
        Palette p = new Palette();
        p.tip = 0xFFFFFF; p.edge = mix(blade, 0xFFFFFF, 0.65f); p.lite = mix(blade, 0xFFFFFF, 0.3f); p.main = blade;
        p.shade = scale(blade, 0.82f); p.base = scale(blade, 0.6f);
        p.hBase = handle; p.hEdge = scale(handle, 1.35f); p.hHole = scale(handle, 0.5f);
        p.hStud = mix(blade, 0xFFFFFF, 0.35f); p.pin = mix(blade, 0xFFFFFF, 0.6f);
        p.glow = mix(blade, 0xFFFFFF, 0.4f);
        return p;
    }

    static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int scale(int c, float f) {
        int r = Math.min(255, Math.round(((c >> 16) & 255) * f));
        int g = Math.min(255, Math.round(((c >> 8) & 255) * f));
        int b = Math.min(255, Math.round((c & 255) * f));
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ pixel pictures
    // Rows run from the top of the part to the bottom. A dot is empty. Every picture is 5 pixels wide.

    /**
     * The blade, tip at the top. T tip, W bright edge, L light, C main, M shade, D dark base. Redrawn from the clip: a
     * one-pixel tip that widens by one pixel at a time, with a bright white pixel at the end of every step so the
     * slanted edge reads as one clean light line, a straight white edge down the left and a darker strip down the right.
     */
    private static final String[] BLADE = {
            ".T...",
            ".T...",
            ".TW..",
            "WLW..",
            "WLW..",
            "WLCW.",
            "WLCW.",
            "WLCW.",
            "WLCCW",
            "WLCCM",
            "WLCCM",
            "WLCCM",
            "WLCCM",
            "WLCCM",
            "WLCCM",
            "WLCCM",
            "WLCCM",
            "WLCCM",
            "WLCCM",
            "DDDDD",
    };

    /** A handle, pivot end at the top. N body, E edge, K hole, S glowing stud, P light pin. */
    private static final String[] HANDLE = {
            "ENNNN",
            "ENNNN",
            "ENPNN",
            "ENNNN",
            "ENNNN",
            "EKKKN",
            "EKKKN",
            "ENNNN",
            "ENNNN",
            "ESSSN",
            "ESSSN",
            "ENNNN",
            "EKKKN",
            "EKKKN",
            "ENNNN",
            "ESSSN",
            "ESSSN",
    };

    /** Same handle without the pin (the one behind). */
    private static final String[] HANDLE_BACK = HANDLE.clone();
    static {
        HANDLE_BACK[2] = "ENNNN";
    }

    // y of the top edge of row 0 for the blade and the handles, x of the left edge (all in knife pixels, pivot at 0,0)
    private static final float BLADE_TOP = 22.5f, HANDLE_TOP = 2.5f, COL_LEFT = -2.5f;

    /**
     * How much bigger the blade is drawn than its pixel picture (1 = as drawn). The handles and the fist are not
     * touched. BLADE_LEN makes it longer and BLADE_WID makes it wider; it grows away from the middle of its base edge,
     * so the base stays exactly where it was, attached to the handles. The picture itself is already the right shape
     * (20 pixels long and 5 wide, the same pixel size as the handles, the same proportions as in the clip), so both
     * stay at 1.
     */
    static final float BLADE_LEN = 1.0f, BLADE_WID = 1.0f;
    /** Height of the blade's base edge in knife pixels (the bottom of its last row). */
    private static final float BLADE_BASE_Y = BLADE_TOP - BLADE.length;
    /** Applied to the blade (and its glow) before it is turned: grows it from its base edge. */
    private static final float[] BLADE_FIT = mul(tr(0f, BLADE_BASE_Y, 0f),
            mul(sc(BLADE_WID, BLADE_LEN, 1f), tr(0f, -BLADE_BASE_Y, 0f)));
    // thickness layers (knife pixels): handle behind, blade in the middle, handle in front
    // (everything is kept thin and close together so it reads as flat pictures like in the clip: no pale sliver down the
    // blade's left side, and no stack of purple slabs where the handles meet the blade)
    private static final float BACK_Z0 = -0.95f, BACK_Z1 = -0.4f, MID_Z0 = -0.3f, MID_Z1 = 0.3f, FRONT_Z0 = 0.4f, FRONT_Z1 = 0.95f;

    // ------------------------------------------------------------------ meshes

    /** A list of coloured quads: 4 corners (12 numbers), a facing direction (3 numbers) and an ARGB colour. */
    static final class Mesh {
        private final ArrayList<float[]> list = new ArrayList<>();
        private final ArrayList<Integer> cols = new ArrayList<>();
        float[] v = new float[0];
        float[] nm = new float[0];
        int[] col = new int[0];
        int n;

        void add(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2,
                 float x3, float y3, float z3, float nx, float ny, float nz, int argb) {
            list.add(new float[]{x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, nx, ny, nz});
            cols.add(argb);
        }

        Mesh freeze() {
            n = list.size();
            v = new float[n * 12];
            nm = new float[n * 3];
            col = new int[n];
            for (int i = 0; i < n; i++) {
                float[] q = list.get(i);
                System.arraycopy(q, 0, v, i * 12, 12);
                System.arraycopy(q, 12, nm, i * 3, 3);
                col[i] = cols.get(i);
            }
            list.clear();
            cols.clear();
            return this;
        }
    }

    private static int colour(char ch, Palette p) {
        switch (ch) {
            case 'T': return p.tip;
            case 'W': return p.edge;
            case 'L': return p.lite;
            case 'C': return p.main;
            case 'M': return p.shade;
            case 'D': return p.base;
            case 'N': return p.hBase;
            case 'E': return p.hEdge;
            case 'K': return p.hHole;
            case 'S': return p.hStud;
            case 'P': return p.pin;
            default: return -1;
        }
    }

    private static int shade(int rgb, float f) {
        return 0xFF000000 | scale(rgb, f);
    }

    private static boolean filled(String[] rows, int r, int c) {
        if (r < 0 || r >= rows.length) return false;
        String row = rows[r];
        return c >= 0 && c < row.length() && row.charAt(c) != '.';
    }

    /** Pushes a flat picture out into thin blocks: a front face for every pixel, and side faces on the outer edges. */
    private static Mesh extrude(String[] rows, Palette p, float yTop, float z0, float z1) {
        Mesh m = new Mesh();
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                int base = colour(rows[r].charAt(c), p);
                if (base < 0) continue;
                float xl = COL_LEFT + c, xr = xl + 1f, yt = yTop - r, yb = yt - 1f;
                m.add(xl, yb, z1, xr, yb, z1, xr, yt, z1, xl, yt, z1, 0, 0, 1, shade(base, 1f));
                if (!filled(rows, r, c - 1))
                    m.add(xl, yb, z0, xl, yb, z1, xl, yt, z1, xl, yt, z0, -1, 0, 0, shade(base, 0.82f));
                if (!filled(rows, r, c + 1))
                    m.add(xr, yb, z1, xr, yb, z0, xr, yt, z0, xr, yt, z1, 1, 0, 0, shade(base, 0.68f));
                if (!filled(rows, r - 1, c))
                    m.add(xl, yt, z1, xr, yt, z1, xr, yt, z0, xl, yt, z0, 0, 1, 0, shade(base, 0.96f));
                if (!filled(rows, r + 1, c))
                    m.add(xl, yb, z0, xr, yb, z0, xr, yb, z1, xl, yb, z1, 0, -1, 0, shade(base, 0.55f));
            }
        }
        return m.freeze();
    }

    /**
     * A thin glowing outline hugging the picture, flat on the plane of its front face so it only shows around the edge.
     * Two rings: a bright thin one and a soft wider one.
     */
    private static Mesh outline(String[] rows, Palette p, float yTop, float z) {
        Mesh m = new Mesh();
        float w1 = 0.30f, w2 = 0.72f;
        int a1 = 0x70000000 | p.glow, a2 = 0x28000000 | p.glow;
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                if (!filled(rows, r, c)) continue;
                float xl = COL_LEFT + c, xr = xl + 1f, yt = yTop - r, yb = yt - 1f;
                // left and right edges: exactly as tall as the pixel
                if (!filled(rows, r, c - 1)) {
                    strip(m, xl - w1, yb, xl, yt, z, a1);
                    strip(m, xl - w2, yb, xl - w1, yt, z, a2);
                }
                if (!filled(rows, r, c + 1)) {
                    strip(m, xr, yb, xr + w1, yt, z, a1);
                    strip(m, xr + w1, yb, xr + w2, yt, z, a2);
                }
                // top and bottom edges: also cover the corner when the pixel diagonally beyond is empty
                // (never over a pixel that is filled, so the knife itself is not tinted)
                for (int dir = -1; dir <= 1; dir += 2) {
                    if (filled(rows, r + dir, c)) continue;
                    float l1 = filled(rows, r + dir, c - 1) ? 0f : w1, r1 = filled(rows, r + dir, c + 1) ? 0f : w1;
                    float l2 = filled(rows, r + dir, c - 1) ? 0f : w2, r2 = filled(rows, r + dir, c + 1) ? 0f : w2;
                    if (dir < 0) {   // top edge
                        strip(m, xl - l1, yt, xr + r1, yt + w1, z, a1);
                        strip(m, xl - l2, yt + w1, xr + r2, yt + w2, z, a2);
                    } else {         // bottom edge
                        strip(m, xl - l1, yb - w1, xr + r1, yb, z, a1);
                        strip(m, xl - l2, yb - w2, xr + r2, yb - w1, z, a2);
                    }
                }
            }
        }
        return m.freeze();
    }

    private static void strip(Mesh m, float x0, float y0, float x1, float y1, float z, int argb) {
        m.add(x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, 0, 0, 1, argb);
    }

    // ------------------------------------------------------------------ the hand (drawn from the player's own skin)

    /** Where the skin's arm pictures start, in skin pixels (64 by 64 skin): the arm itself, and the sleeve layer over it. */
    static final int RIGHT_ARM_U = 40, RIGHT_ARM_V = 16, RIGHT_SLEEVE_U = 40, RIGHT_SLEEVE_V = 32;
    static final int LEFT_ARM_U = 32, LEFT_ARM_V = 48, LEFT_SLEEVE_U = 48, LEFT_SLEEVE_V = 48;
    static final float SKIN_SIZE = 64f;

    /**
     * Quads that take their colours from the player's skin: 4 corners (12 numbers), 4 points in the skin picture
     * (8 numbers, in skin pixels counted from where the arm's picture starts) and a facing direction (3 numbers).
     */
    static final class TexMesh {
        private final ArrayList<float[]> list = new ArrayList<>();
        float[] v = new float[0];
        float[] uv = new float[0];
        float[] nm = new float[0];
        int n;

        void add(float[] a, float[] b, float[] c, float[] d, float u0, float v0, float u1, float v1,
                 float u2, float v2, float u3, float v3, float[] nrm) {
            // pull each point a hair towards the middle of its skin pixel, so a neighbouring pixel never bleeds in
            float cu = (u0 + u1 + u2 + u3) * 0.25f, cv = (v0 + v1 + v2 + v3) * 0.25f;
            float e = 0.03f;
            list.add(new float[]{a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], d[0], d[1], d[2],
                    u0 < cu ? u0 + e : u0 - e, v0 < cv ? v0 + e : v0 - e,
                    u1 < cu ? u1 + e : u1 - e, v1 < cv ? v1 + e : v1 - e,
                    u2 < cu ? u2 + e : u2 - e, v2 < cv ? v2 + e : v2 - e,
                    u3 < cu ? u3 + e : u3 - e, v3 < cv ? v3 + e : v3 - e,
                    nrm[0], nrm[1], nrm[2]});
        }

        TexMesh freeze() {
            n = list.size();
            v = new float[n * 12];
            uv = new float[n * 8];
            nm = new float[n * 3];
            for (int i = 0; i < n; i++) {
                float[] q = list.get(i);
                System.arraycopy(q, 0, v, i * 12, 12);
                System.arraycopy(q, 12, uv, i * 8, 8);
                System.arraycopy(q, 20, nm, i * 3, 3);
            }
            list.clear();
            return this;
        }
    }

    /** A point of the arm box (x across, y along it with the hand at 12, z front to back) put into fist space. */
    private static float[] fp(boolean right, float w, float x, float y, float z) {
        // a slim arm is one pixel narrower: it stays on the side the knife handle ends on, so the handle stays hidden in it
        float keep = (4f - w) * 0.5f;
        return new float[]{(right ? w * 0.5f - x : x - w * 0.5f) + keep, z - 2f, y - 12f};
    }

    /** The same for a facing direction. */
    private static float[] fn(boolean right, float nx, float ny, float nz) {
        return new float[]{right ? -nx : nx, nz, ny};
    }

    private static float between(float a, float b, int i, int n) {
        return a + (b - a) * i / n;
    }

    /**
     * The end of a player's arm, laid out the way the game lays out the arm box and cut out of the arm's picture in the
     * skin: the end face is the bottom of the hand and the four sides are the last FIST_LEN rows of the arm. One quad
     * per skin pixel. grow pushes the box outwards (0 for the arm, 0.25 for the sleeve layer, as in the game).
     */
    private static TexMesh hand(boolean right, boolean slim, float grow) {
        TexMesh m = new TexMesh();
        int iw = slim ? 3 : 4;                        // arm width in skin pixels (slim arms are 3 wide)
        float w = iw;
        float x0 = -grow, x1 = w + grow, y0 = -grow, y1 = 12f + grow, z0 = -grow, z1 = 4f + grow;
        int r0 = 12 - FIST_LEN;

        // the end face: the bottom of the hand
        for (int ix = 0; ix < iw; ix++) {
            for (int iz = 0; iz < 4; iz++) {
                float xa = between(x0, x1, ix, iw), xb = between(x0, x1, ix + 1, iw);
                float za = between(z0, z1, iz, 4), zb = between(z0, z1, iz + 1, 4);
                float u = 4 + iw + ix, v = 3 - iz;
                m.add(fp(right, w, xa, y1, za), fp(right, w, xb, y1, za), fp(right, w, xb, y1, zb), fp(right, w, xa, y1, zb),
                        u, v + 1, u + 1, v + 1, u + 1, v, u, v, fn(right, 0, 1, 0));
            }
        }
        // the four sides: the rows nearest the hand
        for (int r = r0; r < 12; r++) {
            float ya = between(y0, y1, r, 12), yb = between(y0, y1, r + 1, 12);
            float v = 4 + r;
            for (int ix = 0; ix < iw; ix++) {
                float xa = between(x0, x1, ix, iw), xb = between(x0, x1, ix + 1, iw);
                float uf = 4 + ix;                    // front
                m.add(fp(right, w, xa, ya, z0), fp(right, w, xb, ya, z0), fp(right, w, xb, yb, z0), fp(right, w, xa, yb, z0),
                        uf, v, uf + 1, v, uf + 1, v + 1, uf, v + 1, fn(right, 0, 0, -1));
                float ub = 8 + iw + (iw - 1 - ix);    // back (its picture runs the other way round)
                m.add(fp(right, w, xa, ya, z1), fp(right, w, xb, ya, z1), fp(right, w, xb, yb, z1), fp(right, w, xa, yb, z1),
                        ub + 1, v, ub, v, ub, v + 1, ub + 1, v + 1, fn(right, 0, 0, 1));
            }
            for (int iz = 0; iz < 4; iz++) {
                float za = between(z0, z1, iz, 4), zb = between(z0, z1, iz + 1, 4);
                float uw = 3 - iz;                    // the side at x = 0
                m.add(fp(right, w, x0, ya, za), fp(right, w, x0, ya, zb), fp(right, w, x0, yb, zb), fp(right, w, x0, yb, za),
                        uw + 1, v, uw, v, uw, v + 1, uw + 1, v + 1, fn(right, -1, 0, 0));
                float ue = 4 + iw + iz;               // the side at x = the arm's width
                m.add(fp(right, w, x1, ya, za), fp(right, w, x1, ya, zb), fp(right, w, x1, yb, zb), fp(right, w, x1, yb, za),
                        ue, v, ue + 1, v, ue + 1, v + 1, ue, v + 1, fn(right, 1, 0, 0));
            }
        }
        return m.freeze();
    }

    /** A slightly bigger box round the hand; only its far faces are drawn, which leaves a thin glowing rim round it. */
    private static Mesh handRim(Palette p, boolean slim, float grow) {
        Mesh m = new Mesh();
        float hx = (slim ? 1.5f : 2f) + grow + 0.14f, ey = 2f + grow + 0.14f;
        float keep = slim ? 0.5f : 0f;                // the same sideways move as the hand itself (see fp)
        float xa = keep - hx, xb = keep + hx;
        float z0 = -FIST_LEN - 0.2f, z1 = grow + 0.2f;
        int a = 0x66000000 | p.glow;
        m.add(xa, -ey, z1, xb, -ey, z1, xb, ey, z1, xa, ey, z1, 0, 0, 1, a);
        m.add(xa, -ey, z0, xa, ey, z0, xb, ey, z0, xb, -ey, z0, 0, 0, -1, a);
        m.add(xa, ey, z1, xb, ey, z1, xb, ey, z0, xa, ey, z0, 0, 1, 0, a);
        m.add(xa, -ey, z0, xb, -ey, z0, xb, -ey, z1, xa, -ey, z1, 0, -1, 0, a);
        m.add(xb, -ey, z1, xb, -ey, z0, xb, ey, z0, xb, ey, z1, 1, 0, 0, a);
        m.add(xa, -ey, z0, xa, -ey, z1, xa, ey, z1, xa, ey, z0, -1, 0, 0, a);
        return m.freeze();
    }

    /** All the meshes of one look. */
    static final class Model {
        Mesh blade, handleFront, handleBack, bladeGlow, handleGlowFront, handleGlowBack;
        /** Colour (RGB) of the motion trail: the same glow colour as the outline. */
        int trail;
        /** The hand cut from the skin: [left 0, right 1][wide 0, slim 1][arm 0, sleeve layer 1]. */
        final TexMesh[][][] hand = new TexMesh[2][2][2];
        /** The glowing rim round the hand: [wide 0, slim 1][without 0, with 1 the sleeve layer]. */
        final Mesh[][] handRim = new Mesh[2][2];
    }

    static Model model(Palette p) {
        Model md = new Model();
        md.trail = p.glow;
        md.blade = extrude(BLADE, p, BLADE_TOP, MID_Z0, MID_Z1);
        md.handleFront = extrude(HANDLE, p, HANDLE_TOP, FRONT_Z0, FRONT_Z1);
        md.handleBack = extrude(HANDLE_BACK, p, HANDLE_TOP, BACK_Z0, BACK_Z1);
        md.bladeGlow = outline(BLADE, p, BLADE_TOP, MID_Z1 + 0.01f);
        md.handleGlowFront = outline(HANDLE, p, HANDLE_TOP, FRONT_Z1 + 0.01f);
        md.handleGlowBack = outline(HANDLE_BACK, p, HANDLE_TOP, BACK_Z1 + 0.01f);
        for (int arm = 0; arm < 2; arm++)
            for (int slim = 0; slim < 2; slim++)
                for (int layer = 0; layer < 2; layer++)
                    md.hand[arm][slim][layer] = hand(arm == 1, slim == 1, layer == 1 ? 0.25f : 0f);
        for (int slim = 0; slim < 2; slim++)
            for (int sleeve = 0; sleeve < 2; sleeve++)
                md.handRim[slim][sleeve] = handRim(p, slim == 1, sleeve == 1 ? 0.25f : 0f);
        return md;
    }

    // ------------------------------------------------------------------ 4x4 matrices (row-major, column vectors)

    static float[] id() {
        return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[i * 4 + k] * b[k * 4 + j];
                r[i * 4 + j] = s;
            }
        return r;
    }

    static float[] tr(float x, float y, float z) {
        return new float[]{1, 0, 0, x, 0, 1, 0, y, 0, 0, 1, z, 0, 0, 0, 1};
    }

    static float[] sc(float x, float y, float z) {
        return new float[]{x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1};
    }

    static float[] rz(float deg) {
        float r = (float) Math.toRadians(deg), c = (float) Math.cos(r), s = (float) Math.sin(r);
        return new float[]{c, -s, 0, 0, s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    static float[] rx(float deg) {
        float r = (float) Math.toRadians(deg), c = (float) Math.cos(r), s = (float) Math.sin(r);
        return new float[]{1, 0, 0, 0, 0, c, -s, 0, 0, s, c, 0, 0, 0, 0, 1};
    }

    static float[] ry(float deg) {
        float r = (float) Math.toRadians(deg), c = (float) Math.cos(r), s = (float) Math.sin(r);
        return new float[]{c, 0, s, 0, 0, 1, 0, 0, -s, 0, c, 0, 0, 0, 0, 1};
    }

    // ------------------------------------------------------------------ output buffer

    /** The finished quads in camera space, ready to hand to a vertex consumer. */
    static final class Out {
        float[] v = new float[12 * 1024];
        int[] col = new int[1024];
        int n;
        /** The hand: quads coloured by the player's skin (4 corners, 4 points in the skin picture as 0..1, a direction). */
        float[] hv = new float[12 * 256];
        float[] huv = new float[8 * 256];
        float[] hn = new float[3 * 256];
        int hcount;

        void clear() {
            n = 0;
            hcount = 0;
        }

        private void room() {
            if (n >= col.length) {
                v = Arrays.copyOf(v, v.length * 2);
                col = Arrays.copyOf(col, col.length * 2);
            }
        }

        private void handRoom() {
            if (hcount >= hn.length / 3) {
                hv = Arrays.copyOf(hv, hv.length * 2);
                huv = Arrays.copyOf(huv, huv.length * 2);
                hn = Arrays.copyOf(hn, hn.length * 2);
            }
        }
    }

    /** Adds the quads of a skin-coloured mesh, moved by t. (u0, v0) is where that arm's picture starts in the skin. */
    private static void emitTex(Out o, TexMesh m, float[] t, int u0, int v0) {
        for (int i = 0; i < m.n; i++) {
            o.handRoom();
            int b = i * 12, w = o.hcount * 12, k = o.hcount * 8;
            for (int j = 0; j < 4; j++) {
                float x = m.v[b + j * 3], y = m.v[b + j * 3 + 1], z = m.v[b + j * 3 + 2];
                o.hv[w + j * 3] = t[0] * x + t[1] * y + t[2] * z + t[3];
                o.hv[w + j * 3 + 1] = t[4] * x + t[5] * y + t[6] * z + t[7];
                o.hv[w + j * 3 + 2] = t[8] * x + t[9] * y + t[10] * z + t[11];
                o.huv[k + j * 2] = (u0 + m.uv[i * 8 + j * 2]) / SKIN_SIZE;
                o.huv[k + j * 2 + 1] = (v0 + m.uv[i * 8 + j * 2 + 1]) / SKIN_SIZE;
            }
            float nx = m.nm[i * 3], ny = m.nm[i * 3 + 1], nz = m.nm[i * 3 + 2];
            float qx = t[0] * nx + t[1] * ny + t[2] * nz;
            float qy = t[4] * nx + t[5] * ny + t[6] * nz;
            float qz = t[8] * nx + t[9] * ny + t[10] * nz;
            float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (len < 1e-6f) len = 1f;
            o.hn[o.hcount * 3] = qx / len;
            o.hn[o.hcount * 3 + 1] = qy / len;
            o.hn[o.hcount * 3 + 2] = qz / len;
            o.hcount++;
        }
    }

    /** cull: 0 = keep quads facing the camera, 1 = keep quads facing away (the rim), 2 = keep all. */
    private static void emit(Out o, Mesh m, float[] t, int cull) {
        for (int i = 0; i < m.n; i++) {
            int b = i * 12;
            float[] v = m.v;
            float x0 = t[0] * v[b] + t[1] * v[b + 1] + t[2] * v[b + 2] + t[3];
            float y0 = t[4] * v[b] + t[5] * v[b + 1] + t[6] * v[b + 2] + t[7];
            float z0 = t[8] * v[b] + t[9] * v[b + 1] + t[10] * v[b + 2] + t[11];
            float x1 = t[0] * v[b + 3] + t[1] * v[b + 4] + t[2] * v[b + 5] + t[3];
            float y1 = t[4] * v[b + 3] + t[5] * v[b + 4] + t[6] * v[b + 5] + t[7];
            float z1 = t[8] * v[b + 3] + t[9] * v[b + 4] + t[10] * v[b + 5] + t[11];
            float x2 = t[0] * v[b + 6] + t[1] * v[b + 7] + t[2] * v[b + 8] + t[3];
            float y2 = t[4] * v[b + 6] + t[5] * v[b + 7] + t[6] * v[b + 8] + t[7];
            float z2 = t[8] * v[b + 6] + t[9] * v[b + 7] + t[10] * v[b + 8] + t[11];
            float x3 = t[0] * v[b + 9] + t[1] * v[b + 10] + t[2] * v[b + 11] + t[3];
            float y3 = t[4] * v[b + 9] + t[5] * v[b + 10] + t[6] * v[b + 11] + t[7];
            float z3 = t[8] * v[b + 9] + t[9] * v[b + 10] + t[10] * v[b + 11] + t[11];
            if (cull != 2) {
                int k = i * 3;
                float nx = t[0] * m.nm[k] + t[1] * m.nm[k + 1] + t[2] * m.nm[k + 2];
                float ny = t[4] * m.nm[k] + t[5] * m.nm[k + 1] + t[6] * m.nm[k + 2];
                float nz = t[8] * m.nm[k] + t[9] * m.nm[k + 1] + t[10] * m.nm[k + 2];
                float cx = (x0 + x1 + x2 + x3) * 0.25f, cy = (y0 + y1 + y2 + y3) * 0.25f, cz = (z0 + z1 + z2 + z3) * 0.25f;
                float facing = -(nx * cx + ny * cy + nz * cz);
                if (cull == 0 && facing <= 0f) continue;
                if (cull == 1 && facing >= 0f) continue;
            }
            o.room();
            int w = o.n * 12;
            // keep the corners counter-clockwise on screen, so nothing is lost if the layer culls back faces
            float turn = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
            if (turn >= 0f) {
                o.v[w] = x0; o.v[w + 1] = y0; o.v[w + 2] = z0;
                o.v[w + 3] = x1; o.v[w + 4] = y1; o.v[w + 5] = z1;
                o.v[w + 6] = x2; o.v[w + 7] = y2; o.v[w + 8] = z2;
                o.v[w + 9] = x3; o.v[w + 10] = y3; o.v[w + 11] = z3;
            } else {
                o.v[w] = x3; o.v[w + 1] = y3; o.v[w + 2] = z3;
                o.v[w + 3] = x2; o.v[w + 4] = y2; o.v[w + 5] = z2;
                o.v[w + 6] = x1; o.v[w + 7] = y1; o.v[w + 8] = z1;
                o.v[w + 9] = x0; o.v[w + 10] = y0; o.v[w + 11] = z0;
            }
            o.col[o.n++] = m.col[i];
        }
    }

    // ------------------------------------------------------------------ animation

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    /** Screen angles of the three parts, in degrees clockwise from straight up; and how far the whole thing is moved. */
    static final class Pose {
        float blade, handleA, handleB;
        float lift;      // vertical move in blocks (negative = lower)
        float hand;      // how much lower the fist hangs than the knife (blocks, 0 or negative): it follows the knife a moment later

        void set(Pose p) {
            blade = p.blade; handleA = p.handleA; handleB = p.handleB; lift = p.lift; hand = p.hand;
        }
    }

    // At rest the handles sit in one straight line with the blade, like in the clip (the blade points up and a little
    // to the left, so a handle straight behind it points 169 degrees round). The back handle is turned 4 degrees
    // further, which opens a thin wedge on the right of the handle like the clip shows.
    static final float REST_BLADE = -11f, REST_A = 169f, REST_B = 165f;
    // while it waits at the bottom the knife is folded shut and points straight down: the blade tucked in between the
    // handles, so only the top of a handle (with its pin and a stud) shows above the bottom edge, like in the clip
    private static final float DIP_BLADE = -180f, DIP_A = 180f, DIP_B = 180f;
    /** The fist moves with the back handle; this keeps its swing the same as before the handles were straightened (30 degrees at the dip). */
    private static final float HAND_TURN_GAIN = 30f / (DIP_B - REST_B);

    // The knife's journey after a hit, read off the clip (seconds since the hit; the last time is TOTAL_T). It folds shut and
    // drops to the bottom edge, creeps up a little while it waits there, then flicks open: the blade and the handle swing
    // out while the whole knife pops up a little, sinks a touch, and climbs to its resting place while it finishes turning.
    // It flips as it rises, not after it has arrived.
    // LIFT is how far below its resting place the whole knife is (blocks), BLADE is where the blade points (degrees clockwise
    // from up), and A is how far the front handle has turned since it was folded (degrees, once round, clockwise).
    private static final float[] LIFT_T = {0.09f, 0.22f, 0.30f, 0.345f, 0.40f, 0.49f, 0.57f, 0.63f, 0.69f, 0.75f, 0.81f, 0.87f};
    private static final float[] LIFT_V = {-0.178f, -0.153f, -0.134f, -0.05f, -0.012f, -0.075f, -0.12f, -0.115f, -0.088f, -0.055f, -0.024f, 0f};
    private static final float[] BLADE_T = {0.30f, 0.345f, 0.40f, 0.45f, 0.50f, 0.60f, 0.70f, 0.80f, 0.87f};
    private static final float[] BLADE_V = {-180f, -157f, -150f, -134f, -116f, -86f, -52f, -27f, -11f};
    private static final float[] A_T = {0.30f, 0.345f, 0.40f, 0.45f, 0.50f, 0.54f, 0.58f, 0.63f, 0.70f, 0.78f, 0.87f};
    private static final float[] A_V = {0f, 82f, 108f, 123f, 145f, 180f, 212f, 240f, 272f, 310f, 349f};
    // the fist drops out of sight with the knife (nothing of it shows while the knife waits at the bottom, like in the clip),
    // follows a moment after the knife pops up, and only catches up completely as the flip finishes
    private static final float[] HAND_T = {0.09f, 0.30f, 0.345f, 0.40f, 0.50f, 0.57f, 0.70f, 0.80f, 0.87f};
    private static final float[] HAND_V = {-0.22f, -0.22f, -0.15f, -0.11f, -0.08f, -0.055f, -0.025f, -0.008f, 0f};

    // The first moments after a hit, read off the clip: for the first 0.075 s the knife only sags (the blade stays up), then the
    // blade snaps shut and the knife drops to the bottom edge in a blink (the clip never shows a half-folded knife). DROP is how
    // far down it has got (0 = where it was, 1 = the bottom edge) by each time since the hit (the last time is DIP_T); the
    // fold starts at FOLD_AT.
    private static final float[] DROP_T = {0f, 0.02f, 0.04f, 0.06f, 0.075f, 0.09f};
    private static final float[] DROP_V = {0f, 0.10f, 0.22f, 0.40f, 0.70f, 1f};
    private static final float FOLD_AT = 0.075f;
    /** How long the thin purple streak the blade leaves behind lingers after the knife folds shut. */
    private static final float GHOST_T = 0.13f;
    private static final int GHOST_BANDS = 6;

    /** The angle equal to {@code target} (a whole number of turns away) that lies closest to {@code near}. */
    private static float nearest(float target, float near) {
        return target + 360f * Math.round((near - target) / 360f);
    }

    /** Slope of the curve through the points at point k: flat at both ends and at every turning point. */
    private static float slope(float[] ts, float[] vs, int k) {
        if (k <= 0 || k >= ts.length - 1) return 0f;
        float d0 = (vs[k] - vs[k - 1]) / (ts[k] - ts[k - 1]);
        float d1 = (vs[k + 1] - vs[k]) / (ts[k + 1] - ts[k]);
        return d0 * d1 <= 0f ? 0f : 0.5f * (d0 + d1);
    }

    /**
     * A smooth curve through the given (time, value) points. It never swings past a point, so a knife that is
     * rising does not wobble, and it is flat at the start and the end so nothing jumps there.
     */
    private static float curve(float t, float[] ts, float[] vs) {
        int n = ts.length;
        if (t <= ts[0]) return vs[0];
        if (t >= ts[n - 1]) return vs[n - 1];
        int i = 0;
        while (t > ts[i + 1]) i++;
        float h = ts[i + 1] - ts[i];
        float d = (vs[i + 1] - vs[i]) / h;
        float m0 = slope(ts, vs, i), m1 = slope(ts, vs, i + 1);
        if (d == 0f) {
            m0 = 0f; m1 = 0f;
        } else {
            float a = m0 / d, b = m1 / d;                      // keep both slopes pointing the same way as the piece, and not too steep
            if (a < 0f) a = 0f;
            if (b < 0f) b = 0f;
            float s = a * a + b * b;
            if (s > 9f) { float k = 3f / (float) Math.sqrt(s); a *= k; b *= k; }
            m0 = a * d; m1 = b * d;
        }
        float x = (t - ts[i]) / h, x2 = x * x, x3 = x2 * x;
        return (2f * x3 - 3f * x2 + 1f) * vs[i] + (x3 - 2f * x2 + x) * h * m0
                + (-2f * x3 + 3f * x2) * vs[i + 1] + (x3 - x2) * h * m1;
    }

    /**
     * The one animation: the knife drops to the bottom edge of the screen and waits there with only a little of it
     * showing, then shoots up from the bottom and flips shut as it climbs back to its place, then settles.
     * t is in seconds. {@code from} is where the knife was when this animation began: null for the normal held
     * pose, or the pose it had when a new swing cut the previous animation short, so that the drop starts from
     * wherever the knife is, with no jump. (Hitting again while it is still flipping does exactly that, like in the
     * clip.) {@code o} may be the same object as {@code from}.
     */
    static void animate(Pose o, float t, Pose from) {
        float b0 = REST_BLADE, a0 = REST_A, c0 = REST_B, l0 = 0f, h0 = 0f;
        if (from != null) { b0 = from.blade; a0 = from.handleA; c0 = from.handleB; l0 = from.lift; h0 = from.hand; }
        o.blade = REST_BLADE; o.handleA = REST_A; o.handleB = REST_B; o.lift = 0f; o.hand = 0f;
        if (t < 0f || t >= TOTAL_T) return;
        // the handle folds down the short way round from wherever it is, then turns once round from there
        float aFold = nearest(DIP_A, a0);
        if (t < DIP_T) {
            // like the clip: the knife sags with its blade still pointing up (it does not swing sideways), then snaps shut
            // and drops to the bottom edge in a blink
            float p = curve(t, DROP_T, DROP_V);
            float x = clamp01((t - FOLD_AT) / (DIP_T - FOLD_AT)), f = x * x * (3f - 2f * x);
            o.blade = lerp(b0, DIP_BLADE, f);
            o.handleA = lerp(a0, aFold, f);
            o.handleB = lerp(c0, DIP_B, f);
            o.lift = lerp(l0, LIFT_V[0], p);
            o.hand = lerp(h0, HAND_V[0], p);
        } else if (t < DIP_T + HIDE_T) {
            // waiting at the bottom edge, folded shut, creeping up a little
            o.blade = DIP_BLADE; o.handleA = aFold; o.handleB = DIP_B;
            o.lift = curve(t, LIFT_T, LIFT_V);
            o.hand = curve(t, HAND_T, HAND_V);
        } else {
            // flicked open: the blade swings round while the whole knife pops up and then climbs to its place
            float e = clamp01((curve(t, BLADE_T, BLADE_V) - DIP_BLADE) / (REST_BLADE - DIP_BLADE));
            o.blade = lerp(DIP_BLADE, REST_BLADE, e);
            o.handleA = aFold + curve(t, A_T, A_V);
            o.handleB = lerp(DIP_B, REST_B, 1f - (float) Math.pow(1f - e, 1.25));
            o.lift = curve(t, LIFT_T, LIFT_V);
            o.hand = curve(t, HAND_T, HAND_V);
        }
    }

    // ------------------------------------------------------------------ motion trail

    /** The trail is made of this many steps back in time, this far apart, and each step is split into this many bands. */
    private static final int TRAIL_STEPS = 14, TRAIL_BANDS = 8;
    private static final float TRAIL_DT = 0.014f;
    /** How strong the trail starts (0..1) behind the blade and behind the handles. */
    private static final float TRAIL_BLADE_ALPHA = 0.85f, TRAIL_HANDLE_ALPHA = 0.55f;

    /** Adds one flat coloured quad, with its corners turned counter-clockwise on screen (like emit does). */
    private static void addQuad(Out o, float[] a, float[] b, float[] c, float[] d, int argb) {
        float t1 = (b[0] - a[0]) * (c[1] - a[1]) - (c[0] - a[0]) * (b[1] - a[1]);
        float t2 = (c[0] - a[0]) * (d[1] - a[1]) - (d[0] - a[0]) * (c[1] - a[1]);
        if (Math.abs(t1) + Math.abs(t2) < 1e-9f) return;           // no area, nothing to draw
        float[] p0 = a, p1 = b, p2 = c, p3 = d;
        if (t1 + t2 < 0f) { p0 = d; p1 = c; p2 = b; p3 = a; }
        o.room();
        int w = o.n * 12;
        System.arraycopy(p0, 0, o.v, w, 3);
        System.arraycopy(p1, 0, o.v, w + 3, 3);
        System.arraycopy(p2, 0, o.v, w + 6, 3);
        System.arraycopy(p3, 0, o.v, w + 9, 3);
        o.col[o.n++] = argb;
    }

    /**
     * The motion trail. While a part of the knife turns it leaves a soft glowing fan behind it: the area it has just
     * swept through, strongest right behind the part and fading with age and towards the pivot. The fan is made of flat
     * translucent quads just behind the knife (so the knife always draws on top). It only follows the turning (not
     * the knife dropping or rising) and only exists while the knife flips, so a knife that is held still has none.
     */
    private static void trail(Out o, int rgb, float t, Pose from, float equip, int side, float size, float aspect) {
        float px = side * PIVOT_NDC_X * PIVOT_Z * TAN_HALF_FOV * aspect;
        float py0 = PIVOT_NDC_Y * PIVOT_Z * TAN_HALF_FOV - 0.85f * equip;
        float s = U * size;
        // everything is pushed straight away from the camera, so it keeps its place on screen but sits behind the knife
        float push = (PIVOT_Z + 2.3f * U * size) / PIVOT_Z;
        float bladeEnd = BLADE_BASE_Y + BLADE.length * BLADE_LEN, handleEnd = HANDLE.length - HANDLE_TOP;
        Pose p = new Pose();
        animate(p, t, from);
        float cy = py0 + p.lift;                                    // only the turning leaves a trail, not the knife moving
        float endLeft = clamp01((TOTAL_T - t) / 0.12f);             // the trail dies away over the last moments, it does not vanish in one frame
        float endFade = endLeft * endLeft * (3f - 2f * endLeft);
        // [part: 0 blade, 1 front handle, 2 back handle][step back in time][band edge] -> a point in camera space
        float[][][][] pt = new float[3][TRAIL_STEPS + 1][TRAIL_BANDS + 1][];
        for (int k = 0; k <= TRAIL_STEPS; k++) {
            animate(p, Math.max(DIP_T + HIDE_T, t - k * TRAIL_DT), from);   // nothing before the flip itself starts
            for (int part = 0; part < 3; part++) {
                float deg = part == 0 ? -p.blade : -((part == 1 ? p.handleA : p.handleB) - 180f);
                float r = (float) Math.toRadians(deg), c = (float) Math.cos(r), sn = (float) Math.sin(r);
                float d0 = part == 0 ? 6f : 3f, d1 = part == 0 ? bladeEnd : handleEnd;
                float k2 = push * (1f + 0.002f * part);              // the blade's fan nearest, the back handle's furthest
                for (int j = 0; j <= TRAIL_BANDS; j++) {
                    float d = d0 + (d1 - d0) * j / TRAIL_BANDS;
                    float ly = part == 0 ? d : -d;                   // along the middle of the part: blade up, handles down
                    float rx = -sn * ly, ry = c * ly;
                    pt[part][k][j] = new float[]{(px + side * s * rx) * k2, (cy + s * ry) * k2, -PIVOT_Z * k2};
                }
            }
        }
        for (int part = 0; part < 3; part++) {
            float top = part == 0 ? TRAIL_BLADE_ALPHA : TRAIL_HANDLE_ALPHA;
            for (int k = 0; k < TRAIL_STEPS; k++) {
                float fresh = 1f - (k + 0.5f) / TRAIL_STEPS;          // 1 right behind the part, 0 at the far end of the trail
                float age = (float) Math.pow(fresh, 1.1);
                int tint = mix(rgb, 0xFFFFFF, 0.30f * fresh * fresh);  // a little lighter right behind the part
                for (int j = 0; j < TRAIL_BANDS; j++) {
                    float q = (j + 0.5f) / TRAIL_BANDS;                 // 0 at the pivot end of the part, 1 at its far end
                    float radial = Math.min(1f, q / 0.35f) * (1f - 0.45f * clamp01((q - 0.7f) / 0.3f));   // fades in, then softens at the end
                    int a = Math.round(255f * top * age * radial * endFade);
                    if (a < 4) continue;
                    addQuad(o, pt[part][k][j], pt[part][k][j + 1], pt[part][k + 1][j + 1], pt[part][k + 1][j],
                            (a << 24) | (tint & 0xFFFFFF));
                }
            }
        }
    }

    /** One corner of the streak: lx across it and ly along it (knife pixels, turned with the blade), pushed back like the trail. */
    private static float[] ghostPoint(float px, float py, float s, int side, float k, float c, float sn, float lx, float ly) {
        return new float[]{(px + side * s * (c * lx - sn * ly)) * k, (py + s * (sn * lx + c * ly)) * k, -PIVOT_Z * k};
    }

    /**
     * The blade's afterimage. The moment the knife folds shut the blade leaves a thin purple streak where it was (the clip
     * shows this as a line above the little stub at the bottom); it fades away in a fraction of a second. It is a thin bright
     * core inside a wider soft glow, sits just behind everything like the flip trail, and does not move with the knife.
     * Only drawn for a hit from the held pose (a hit that cuts into a flip has no streak in the clip).
     */
    private static void ghost(Out o, int rgb, float t, float equip, int side, float size, float aspect) {
        float age = (t - FOLD_AT) / GHOST_T;
        if (age <= 0f || age >= 1f) return;
        float fade = (1f - age) * (1f - age);
        Pose p = new Pose();
        animate(p, FOLD_AT, null);                                   // where the knife was when it began to fold
        float px = side * PIVOT_NDC_X * PIVOT_Z * TAN_HALF_FOV * aspect;
        float py = PIVOT_NDC_Y * PIVOT_Z * TAN_HALF_FOV - 0.85f * equip + p.lift;
        float s = U * size;
        float push = (PIVOT_Z + 2.3f * U * size) / PIVOT_Z;
        float bladeEnd = BLADE_BASE_Y + BLADE.length * BLADE_LEN;
        float r = (float) Math.toRadians(-p.blade), c = (float) Math.cos(r), sn = (float) Math.sin(r);
        for (int layer = 0; layer < 2; layer++) {
            float half = layer == 0 ? 2.2f : 0.8f;                    // soft glow first (further back), thin core in front of it
            float top = layer == 0 ? 0.18f : 0.50f;
            int tint = layer == 0 ? mix(rgb, 0xFFFFFF, 0.15f) : mix(rgb, 0xFFFFFF, 0.55f);
            float k = push * (layer == 0 ? 1.002f : 1f);
            for (int j = 0; j < GHOST_BANDS; j++) {
                float q0 = (float) j / GHOST_BANDS, q1 = (float) (j + 1) / GHOST_BANDS, qm = 0.5f * (q0 + q1);
                float radial = Math.min(1f, qm / 0.25f) * (1f - 0.4f * clamp01((qm - 0.8f) / 0.2f));   // fades in from the pivot, softens at the tip
                int a = Math.round(255f * top * fade * radial);
                if (a < 4) continue;
                float d0 = lerp(-1f, bladeEnd, q0), d1 = lerp(-1f, bladeEnd, q1);
                addQuad(o, ghostPoint(px, py, s, side, k, c, sn, -half, d0), ghostPoint(px, py, s, side, k, c, sn, half, d0),
                        ghostPoint(px, py, s, side, k, c, sn, half, d1), ghostPoint(px, py, s, side, k, c, sn, -half, d1),
                        (a << 24) | (tint & 0xFFFFFF));
            }
        }
    }

    /**
     * Builds the whole picture in camera space.
     *
     * @param t       seconds since the flip started (negative when it is not flipping)
     * @param from    the pose the flip started from when a new swing cut the last one short; null = the held pose
     * @param equip   0 = fully raised, 1 = fully lowered (Minecraft's own equip progress)
     * @param side    +1 for the right hand, -1 for the left
     * @param size    1 = normal size
     * @param aspect  window width divided by window height
     * @param idle    seconds, only used for a tiny sway while held still
     * @param slim    the player's skin has slim (3 pixel) arms
     * @param sleeve  also draw the skin's sleeve layer over the hand
     */
    static void build(Out o, Model md, float t, Pose from, float equip, int side, float size, float aspect, float idle,
                      boolean showHand, boolean glow, boolean slim, boolean sleeve) {
        o.clear();
        Pose pose = new Pose();
        animate(pose, t, from);
        float sway = (t < 0f || t >= TOTAL_T) ? (float) Math.sin(idle * 1.6f) : 0f;
        float lift = pose.lift - 0.85f * equip + 0.003f * sway;

        float px = side * PIVOT_NDC_X * PIVOT_Z * TAN_HALF_FOV * aspect;
        float py = PIVOT_NDC_Y * PIVOT_Z * TAN_HALF_FOV + lift;
        float[] root = mul(tr(px, py, -PIVOT_Z), sc(side * U * size, U * size, U * size));

        float aBlade = pose.blade + 0.8f * sway, aA = pose.handleA, aB = pose.handleB;
        float[] mBlade = mul(mul(root, rz(-aBlade)), BLADE_FIT);
        float[] mA = mul(root, rz(-(aA - 180f)));
        float[] mB = mul(root, rz(-(aB - 180f)));

        // the trail goes first (it sits behind everything), and only while the animation is playing
        if (glow && t > DIP_T + HIDE_T && t < TOTAL_T) trail(o, md.trail, t, from, equip, side, size, aspect);
        if (glow && from == null && t > FOLD_AT && t < FOLD_AT + GHOST_T) ghost(o, md.trail, t, equip, side, size, aspect);

        emit(o, md.blade, mBlade, 0);
        emit(o, md.handleFront, mA, 0);
        emit(o, md.handleBack, mB, 0);
        if (glow) {
            emit(o, md.bladeGlow, mBlade, 0);
            emit(o, md.handleGlowFront, mA, 0);
            emit(o, md.handleGlowBack, mB, 0);
        }

        if (showHand) {
            // where the fist sits relative to the pivot (as for a right hand), then it turns with the handle it holds
            float vx = (HAND_NDC_X * HAND_Z - PIVOT_NDC_X * PIVOT_Z) * TAN_HALF_FOV * aspect;
            float vy = (HAND_NDC_Y * HAND_Z - PIVOT_NDC_Y * PIVOT_Z) * TAN_HALF_FOV;
            float vz = PIVOT_Z - HAND_Z;
            float turn = (aB - REST_B) * HAND_TURN_GAIN;
            float[] aim = mul(rz(HAND_ROLL), mul(rx(HAND_PITCH), ry(HAND_YAW)));
            float[] fistM = mul(mul(tr(px, py + pose.hand, -PIVOT_Z), sc(side, 1, 1)),
                    mul(rz(-turn), mul(tr(vx * size, vy * size, vz * size),
                            mul(aim, sc(HU * size, HU * size, HU * size)))));
            int arm = side > 0 ? 1 : 0, sl = slim ? 1 : 0;
            emitTex(o, md.hand[arm][sl][0], fistM, arm == 1 ? RIGHT_ARM_U : LEFT_ARM_U, arm == 1 ? RIGHT_ARM_V : LEFT_ARM_V);
            if (sleeve) {
                emitTex(o, md.hand[arm][sl][1], fistM, arm == 1 ? RIGHT_SLEEVE_U : LEFT_SLEEVE_U,
                        arm == 1 ? RIGHT_SLEEVE_V : LEFT_SLEEVE_V);
            }
            if (glow) emit(o, md.handRim[sl][sleeve ? 1 : 0], fistM, 1);
        }
    }

    // how the fist is turned (tuned against the clip): rolled a quarter-ish turn so its end face shows as a diamond
    static final float HAND_ROLL = -45f, HAND_PITCH = 0f, HAND_YAW = 0f;
}
