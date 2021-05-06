package dev.m8u.meshvoxelizer;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItemUseContext;
import net.minecraft.state.DirectionProperty;
import net.minecraft.state.EnumProperty;
import net.minecraft.state.StateContainer;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;


public class VoxelizerOriginBlock extends Block {
    static final EnumProperty<Direction> FACING = DirectionProperty.create("facing", Direction.values());

    Map<BlockPos, VoxelizerScreen> voxelizerScreenInstances;

    public VoxelizerOriginBlock() {
        super(AbstractBlock.Properties.create(Material.MISCELLANEOUS));
        this.setRegistryName("meshvoxelizer","voxelizer_origin");
        this.setDefaultState(this.getDefaultState().with(FACING, Direction.NORTH));

        this.voxelizerScreenInstances = new HashMap<>();
    }

    @Override
    protected void fillStateContainer(StateContainer.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockItemUseContext context) {
        return this.getDefaultState().with(FACING, context.getPlacementHorizontalFacing());
    }

    @Override
    public void onBlockAdded(BlockState state, World worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        LOGGER.info("onBlockAdded");
        this.voxelizerScreenInstances.put(pos, new VoxelizerScreen(pos, state.get(FACING), null));
    }

    @Override
    public ActionResultType onBlockActivated(BlockState state, World worldIn, BlockPos pos, PlayerEntity player, Hand handIn, BlockRayTraceResult hit) {
        LOGGER.info("onBlockActivated");

        if (!this.voxelizerScreenInstances.containsKey(pos))
            this.voxelizerScreenInstances.put(pos, new VoxelizerScreen(pos, state.get(FACING), null));

        Minecraft.getInstance().displayGuiScreen(this.voxelizerScreenInstances.get(pos));

        return ActionResultType.SUCCESS;
    }
}
