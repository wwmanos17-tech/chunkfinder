package com.chunkfinder.mixin;

import com.chunkfinder.NameProtect;
import net.minecraft.text.TextVisitFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every piece of text the game draws goes through here, so this is where your name gets swapped. */
@Mixin(TextVisitFactory.class)
public abstract class TextVisitFactoryMixin {
    @ModifyVariable(
        method = "visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        require = 0)
    private static String chunkfinder$protectName(String text) {
        return NameProtect.apply(text);
    }
}
