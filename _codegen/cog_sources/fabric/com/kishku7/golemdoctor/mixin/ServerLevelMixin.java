package com.kishku7.golemdoctor.mixin;

import com.kishku7.golemdoctor.log.GolemSpawnLog;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Counts iron golems the server adds fresh (never ones loaded back from a chunk). */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "addFreshEntity", at = @At("RETURN"))
    private void golemdoctor$countFreshGolem(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && entity instanceof IronGolem golem && !golem.isPlayerCreated()) {
            GolemSpawnLog.record((ServerLevel) (Object) this, golem);
        }
    }
}
