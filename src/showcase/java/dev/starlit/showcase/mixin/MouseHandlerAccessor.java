package dev.starlit.showcase.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("xpos")
    void starlitShowcase$setX(double x);

    @Accessor("ypos")
    void starlitShowcase$setY(double y);
}
