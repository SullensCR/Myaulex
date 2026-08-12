package myau.mixin;

import myau.Myau;
import myau.module.modules.BedNuker;
import myau.module.modules.Bedplates;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockBed.EnumPartType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SideOnly(Side.CLIENT)
@Mixin(value = {BlockRendererDispatcher.class}, priority = 9999)
public abstract class MixinBlockRendererDispatcher {
    @Inject(
            method = {"renderBlock"},
            at = {@At("HEAD")}
    )
    private void renderBlock(
            IBlockState iBlockState,
            BlockPos blockPos,
            IBlockAccess iBlockAccess,
            WorldRenderer worldRenderer,
            CallbackInfoReturnable<Boolean> callbackInfoReturnable
    ) {
        if (Myau.moduleManager != null) {
            BedNuker bedBreaker = (BedNuker) Myau.moduleManager.modules.get(BedNuker.class);
            if (bedBreaker != null && iBlockState.getBlock() instanceof BlockBed
                    && iBlockState.getValue(BlockBed.PART) == EnumPartType.HEAD) {
                bedBreaker.observeBedForEsp(blockPos);
            }
            Bedplates bedplates = (Bedplates) Myau.moduleManager.modules.get(Bedplates.class);
            if (bedplates != null && bedplates.isEnabled() && iBlockState.getBlock() instanceof BlockBed
                    && iBlockState.getValue(BlockBed.PART) == EnumPartType.HEAD) {
                bedplates.observeBed(new BlockPos(blockPos));
            }
        }
    }
}
