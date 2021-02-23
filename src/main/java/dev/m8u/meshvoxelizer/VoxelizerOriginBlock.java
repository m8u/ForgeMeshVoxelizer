package dev.m8u.meshvoxelizer;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;


public class VoxelizerOriginBlock extends Block {
    public VoxelizerOriginBlock() {
        super(AbstractBlock.Properties.create(Material.MISCELLANEOUS));
        this.setRegistryName("meshvoxelizer","voxilizer_origin");

    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        LOGGER.info("onBlockActivated");
        Minecraft.getInstance().displayGuiScreen(new VoxelizerScreen(null));
        return ActionResultType.SUCCESS;
    }


}
