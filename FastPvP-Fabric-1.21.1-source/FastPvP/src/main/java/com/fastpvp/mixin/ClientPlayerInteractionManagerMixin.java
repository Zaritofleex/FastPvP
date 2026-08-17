package com.fastpvp.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    private static final ThreadLocal<Boolean> FASTPVP_ANCHOR_REENTRY =
            ThreadLocal.withInitial(() -> false);

    // Second right-click on a crystal = attack it.
    @Inject(method = "interactEntity", at = @At("HEAD"), cancellable = true)
    private void fastpvp$rightClickCrystal(ClientPlayerEntity player, Entity entity, Hand hand,
                                           CallbackInfoReturnable<ActionResult> cir) {
        if (!(entity instanceof EndCrystalEntity)) return;
        if (!player.getStackInHand(hand).isOf(Items.END_CRYSTAL)) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        ((ClientPlayerInteractionManager) (Object) this).attackEntity(player, entity);
        cir.setReturnValue(ActionResult.SUCCESS);
    }

    // One right-click on a partially charged anchor fills it to 4 charges.
    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void fastpvp$fillAnchor(ClientPlayerEntity player, Hand hand, BlockHitResult hit,
                                    CallbackInfoReturnable<ActionResult> cir) {
        if (FASTPVP_ANCHOR_REENTRY.get()) return;
        if (!cir.getReturnValue().isAccepted()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockPos pos = hit.getBlockPos();
        BlockState state = client.world.getBlockState(pos);
        if (!(state.getBlock() instanceof RespawnAnchorBlock)) return;

        // Only auto-fill when the player is using Glowstone.
        ItemStack held = player.getStackInHand(hand);
        if (!held.isOf(Items.GLOWSTONE)) return;

        int charges = state.get(RespawnAnchorBlock.CHARGES);
        if (charges <= 0 || charges >= RespawnAnchorBlock.MAX_CHARGES) return;

        FASTPVP_ANCHOR_REENTRY.set(true);
        try {
            while (charges < RespawnAnchorBlock.MAX_CHARGES
                    && player.getStackInHand(hand).isOf(Items.GLOWSTONE)) {
                ((ClientPlayerInteractionManager) (Object) this)
                        .interactBlock(player, hand, hit);
                charges = client.world.getBlockState(pos)
                        .get(RespawnAnchorBlock.CHARGES);
            }
        } finally {
            FASTPVP_ANCHOR_REENTRY.set(false);
        }
    }
}
