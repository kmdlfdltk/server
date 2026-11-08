package dev.champions.hyperscroll.mixin;

import dev.champions.hyperscroll.ScrollClient;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseWheelMixin {
    @Inject(method="onScroll",at=@At("HEAD"),cancellable=true)
    private void champions$originalBuilderPartyScroll(
            long windowHandle,double horizontal,double vertical,CallbackInfo callback) {
        if(ScrollClient.intercept(vertical)) callback.cancel();
    }
}
