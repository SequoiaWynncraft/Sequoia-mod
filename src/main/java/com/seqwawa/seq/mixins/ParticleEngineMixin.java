package com.seqwawa.seq.mixins;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import com.seqwawa.seq.radiance.RadianceCheckerClient;
import com.seqwawa.seq.radiance.RadianceInvestigationProbe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
    @Inject(method = "createParticle", at = @At("HEAD"))
    private void onCreateParticle(
            ParticleOptions particle,
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            CallbackInfoReturnable<Particle> cir) {
        if (!RadianceCheckerClient.isEnabled()) {
            return;
        }

        RadianceInvestigationProbe.onParticle(particle, x, y, z);
    }
}
