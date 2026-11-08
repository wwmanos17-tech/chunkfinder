package com.chunkfinder.mixin;

import com.chunkfinder.ChunkFinderConfig;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Style;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * When "Pharaoh Font In All Menus And Chat" is on, text that would use Minecraft's normal font is drawn with the
 * Pharaoh font instead (letters the Pharaoh font does not have still come from the normal font). Only the font used
 * for drawing is swapped; the text itself is not changed or stored. No texture pack is needed.
 */
@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
    private static final Identifier CHUNKFINDER_GLOBAL = Identifier.of("chunkfinder", "global");

    @ModifyVariable(
        method = "getFontStorage(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/font/FontStorage;",
        at = @At("HEAD"),
        argsOnly = true,
        require = 0)
    private Identifier chunkfinder$font(Identifier id) {
        if (ChunkFinderConfig.INSTANCE.globalFont && Style.DEFAULT_FONT_ID.equals(id)) return CHUNKFINDER_GLOBAL;
        return id;
    }
}
