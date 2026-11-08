package com.chunkfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * Butterfly Knife: a cosmetic look for the sword in your hand. Every sword is drawn as a pixel-art butterfly knife
 * (a cyan blade, two dark purple handles with glowing studs and a light pin) held in a fist that is drawn from your
 * own skin, so the hand matches whatever skin you use. The knife is built from plain coloured blocks in code, so no
 * texture pack is needed. Only the picture changes: damage, reach and hits stay exactly as they are, and nobody else
 * sees it.
 *
 * There is one animation. When you swing, the knife folds shut and drops to the bottom of the screen, where only a
 * little of its handle shows for a moment. Then it shoots up from the bottom and flips open and shut as it climbs
 * back into your hand, and settles. The rest of the time it is held still. While it flips, the turning blade and
 * handles leave a glowing trail behind them (it goes with the Glow switch). Hit again while it is still flipping
 * and it drops again right away from wherever it is, so quick hits chain one animation into the next, like in the
 * reference clip.
 *
 * All the shapes and the animation maths live in KnifeGeo; this class only decides when to draw and sends the
 * finished quads to the game.
 */
public final class ButterflyKnife {
    private ButterflyKnife() {}

    /** Full brightness for the hand (like the glowing knife), and "no tint" for the game's overlay value. */
    private static final int FULL_LIGHT = 0xF000F0;
    private static final int NO_OVERLAY = 0x000A0000;

    private static final KnifeGeo.Out OUT = new KnifeGeo.Out();
    private static KnifeGeo.Model model;
    private static String modelKey = "";

    /** When the current flip began (System.nanoTime), or 0 when it is not flipping. */
    private static long flipStart = 0L;
    private static float lastSwing = 0f;
    /**
     * How much of Minecraft's own "held item lowered" amount is used (0 = none, 1 = all of it). Minecraft lowers the
     * item you hold after every swing (that dip follows the attack cooldown, it is not only for switching items). On
     * top of the knife's own drop it would push the knife below the screen for most of the animation and hide the
     * start of it, so the dip is ignored while the knife animates and eased back in afterwards.
     */
    private static float equipBlend = 1f;
    private static long lastMainFrame = 0L;
    private static final float EQUIP_EASE_IN = 0.35f;
    /** Where the knife was when a new swing cut the previous flip short (only used while fromCut is true), and a scratch pose. */
    private static final KnifeGeo.Pose START = new KnifeGeo.Pose();
    private static final KnifeGeo.Pose NOW = new KnifeGeo.Pose();
    private static boolean fromCut = false;

    private static String materialKey(String id) {
        if (id.startsWith("wooden")) return "wooden";
        if (id.startsWith("stone")) return "stone";
        if (id.startsWith("golden")) return "golden";
        if (id.startsWith("diamond")) return "diamond";
        if (id.startsWith("netherite")) return "netherite";
        return "iron";
    }

    /** Window width divided by height; the hand is drawn with this shape, so the knife keeps its place on any window. */
    private static float aspect() {
        try {
            var w = MinecraftClient.getInstance().getWindow();
            float a = (float) w.getFramebufferWidth() / (float) Math.max(1, w.getFramebufferHeight());
            if (a > 0.5f && a < 4f) return a;
        } catch (Throwable ignored) {
        }
        return 16f / 9f;
    }

    /** Returns true when the knife was drawn, so the normal sword is skipped. */
    public static boolean render(AbstractClientPlayerEntity player, Hand hand, float swing, ItemStack stack,
                                 float equip, MatrixStack ms, VertexConsumerProvider vcp) {
        ChunkFinderConfig cfg = ChunkFinderConfig.INSTANCE;
        if (!cfg.knifeEnabled || stack == null || stack.isEmpty()) return false;
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        if (!id.endsWith("_sword")) return false;

        boolean neon = cfg.knifeNeon;
        String key = neon ? "neon" : materialKey(id);
        if (model == null || !key.equals(modelKey)) {
            model = KnifeGeo.model(neon ? KnifeGeo.neon() : KnifeGeo.material(id));
            modelKey = key;
        }

        // one flip per swing: it starts when a new swing begins and then runs by itself. A new swing while the knife
        // is already flipping makes it drop again right away from wherever it is (like your clip does when you hit
        // again quickly); a swing while it is still dropping, waiting at the bottom or just popping up changes nothing.
        long now = System.nanoTime();
        float t = -1f;
        if (hand == Hand.MAIN_HAND) {
            boolean newSwing = swing > 0f && (lastSwing <= 0f || swing < lastSwing - 0.2f);
            if (newSwing) {
                float since = flipStart == 0L ? -1f : (now - flipStart) / 1.0e9f;
                if (since < 0f || since >= KnifeGeo.TOTAL_T) {
                    flipStart = now;
                    fromCut = false;
                } else if (since >= KnifeGeo.CHAIN_FROM) {
                    KnifeGeo.animate(NOW, since, fromCut ? START : null);
                    START.set(NOW);
                    fromCut = true;
                    flipStart = now;
                }
            }
            lastSwing = swing;
            if (flipStart != 0L) {
                float elapsed = (now - flipStart) / 1.0e9f;
                if (elapsed < KnifeGeo.TOTAL_T) t = elapsed;
                else flipStart = 0L;
            }
            // Minecraft's own dip after a swing is left out while the knife animates, then eased back in
            float frame = lastMainFrame == 0L ? 0f : Math.min(0.1f, (now - lastMainFrame) / 1.0e9f);
            lastMainFrame = now;
            if (t >= 0f) equipBlend = 0f;
            else equipBlend = Math.min(1f, equipBlend + frame / EQUIP_EASE_IN);
        }
        float equipUsed = hand == Hand.MAIN_HAND ? equip * equipBlend : equip;

        Arm arm = hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        int side = arm == Arm.RIGHT ? 1 : -1;
        float size = cfg.knifeSize / 100f;
        float idle = (now / 1_000_000L % 1_000_000L) / 1000f;

        // the hand is cut from the player's own skin, so it always matches whatever skin they use
        Identifier skinTexture = null;
        boolean slim = false, sleeve = false;
        if (cfg.knifeHand) {
            try {
                var skin = player.getSkinTextures();
                skinTexture = skin.texture();
                slim = "SLIM".equals(skin.model().name());
                sleeve = player.isPartVisible(arm == Arm.RIGHT ? PlayerModelPart.RIGHT_SLEEVE : PlayerModelPart.LEFT_SLEEVE);
            } catch (Throwable ignored) {
                skinTexture = null;
            }
        }
        boolean showHand = skinTexture != null;

        KnifeGeo.build(OUT, model, t, fromCut ? START : null, equipUsed, side, size, aspect(), idle, showHand, cfg.knifeGlow, slim, sleeve);

        MatrixStack.Entry entry = ms.peek();
        Matrix4f m = entry.getPositionMatrix();

        // the hand first: it is solid, so the knife (drawn after it) sits in front of it and inside it properly
        if (showHand && OUT.hcount > 0) {
            VertexConsumer hv = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(skinTexture));
            for (int i = 0; i < OUT.hcount; i++) {
                float nx = OUT.hn[i * 3], ny = OUT.hn[i * 3 + 1], nz = OUT.hn[i * 3 + 2];
                for (int j = 0; j < 4; j++) {
                    int k = i * 12 + j * 3;
                    hv.vertex(m, OUT.hv[k], OUT.hv[k + 1], OUT.hv[k + 2])
                            .color(255, 255, 255, 255)
                            .texture(OUT.huv[i * 8 + j * 2], OUT.huv[i * 8 + j * 2 + 1])
                            .overlay(NO_OVERLAY)
                            .light(FULL_LIGHT)
                            .normal(entry, nx, ny, nz);
                }
            }
        }

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getDebugQuads());
        float[] v = OUT.v;
        for (int i = 0; i < OUT.n; i++) {
            int c = OUT.col[i];
            float a = ((c >>> 24) & 255) / 255f, r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
            int k = i * 12;
            for (int j = 0; j < 4; j++) {
                vc.vertex(m, v[k + j * 3], v[k + j * 3 + 1], v[k + j * 3 + 2]).color(r, g, b, a);
            }
        }
        return true;
    }
}
