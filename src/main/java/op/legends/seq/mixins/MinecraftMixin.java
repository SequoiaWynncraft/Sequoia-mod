package op.legends.seq.mixins;

import op.legends.seq.accessors.EventBusAccessor;
import op.legends.seq.client.SeqClient;
import op.legends.seq.events.MinecraftFinishedLoading;
import op.legends.seq.managers.AssetManager;
import op.legends.seq.utils.rendering.nvg.NVGContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.Minecraft.class)
public abstract class MinecraftMixin implements EventBusAccessor {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        NVGContext.init();
        SeqClient.gameManager.loadFonts();
        SeqClient.assetManager = new AssetManager();
        dispatch(new MinecraftFinishedLoading());
    }
}
