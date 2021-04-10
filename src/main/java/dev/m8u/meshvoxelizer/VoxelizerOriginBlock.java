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

import java.util.HashMap;
import java.util.Map;


public class VoxelizerOriginBlock extends Block {
    Map<BlockPos, VoxelizerScreen> voxelizerScreenInstances;

    public VoxelizerOriginBlock() {
        super(AbstractBlock.Properties.create(Material.MISCELLANEOUS));
        this.setRegistryName("meshvoxelizer","voxilizer_origin");

        this.voxelizerScreenInstances = new HashMap<>();
    }

    @Override
    public void onBlockAdded(BlockState state, World worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        LOGGER.info("onBlockAdded");
        this.voxelizerScreenInstances.put(pos, new VoxelizerScreen(pos, null));
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        LOGGER.info("onBlockActivated");

        if (!this.voxelizerScreenInstances.containsKey(pos))
            this.voxelizerScreenInstances.put(pos, new VoxelizerScreen(pos, null));

        Minecraft.getInstance().displayGuiScreen(this.voxelizerScreenInstances.get(pos));

        return ActionResultType.SUCCESS;
    }
}
