package dev.m8u.meshvoxelizer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.VoxelShape;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.DimensionType;
import net.minecraft.world.IWorldReader;
import net.minecraft.world.IWorldWriter;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeManager;
import net.minecraft.world.border.WorldBorder;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.IChunk;
import net.minecraft.world.gen.Heightmap;
import net.minecraft.world.lighting.WorldLightManager;

import javax.annotation.Nullable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;


public class VoxelizerScreen extends Screen implements IWorldWriter, IWorldReader {
    World world;
    BlocksByAverageColor colorToBlockDict;

    GLRasterizer rasterizer;

    protected BlockPos originBlockPos;
    protected Direction originBlockDirection;

    protected Button chooseModelButton;
    protected Button voxelizeButton;
    protected Button undoButton;
    protected Button replaceMaterialsButton;
    protected TextFieldWidget voxelResTextField;
    protected int progress; // 0 to 100

    protected String filenameSelected;
    protected String voxelResolutionString;

    Map<BlockPos, BlockState> undoBuffer;

    ArrayList<BlockState> materialsForReplacing;

    public VoxelizerScreen(BlockPos originBlockPos, Direction originBlockDirection, String selected) {
        super(new StringTextComponent("VoxelizerScreen"));

        this.originBlockPos = originBlockPos;
        this.originBlockDirection = originBlockDirection;

        this.filenameSelected = selected != null ? selected: "";

        this.undoBuffer = new HashMap<>();

        this.materialsForReplacing = new ArrayList<>();
    }

    protected void init() {
        this.world = this.minecraft.getIntegratedServer().getWorld(
                this.minecraft.player.world.getDimensionKey());
        this.rasterizer = GLRasterizer.getInstance();

        this.chooseModelButton = this.addButton(new Button(this.width / 2 - 64, this.height / 4, 128, 20,
                new StringTextComponent("Choose model file.."),
        (e) -> {
            this.minecraft.displayGuiScreen(new ChooseModelFileScreen(this, this.filenameSelected));
        }));

        this.voxelizeButton = this.addButton(new Button(this.width /  2 - 64, this.height / 5 * 3, 128, 20,
                new StringTextComponent("Voxelize"),
        (e) -> {
            this.voxelizeButton.active = false;
            this.progress = 0;
            voxelize();
            this.voxelizeButton.active = true;
        }));

        this.undoButton = this.addButton(new Button(this.width /  2 - 64, this.voxelizeButton.y + 24, 128, 20,
                new StringTextComponent("Undo"),
        (e) -> {
            this.undoBuffer.forEach((blockPos, blockState) -> {
                this.setBlockState(blockPos, blockState, 3);
            });
            this.undoBuffer = new HashMap<>();
        }));
        this.undoButton.active = !this.rasterizer.isWorking && this.undoBuffer.size() > 0;

        this.replaceMaterialsButton = this.addButton(new Button(this.width /  2 - 64, this.undoButton.y + 24, 128, 20,
                new StringTextComponent("Replace materials.."),
        (e) -> {
            this.minecraft.displayGuiScreen(new ReplaceMaterialsScreen(this, this.materialsForReplacing));
        }));
        this.replaceMaterialsButton.active = !this.rasterizer.isWorking && this.materialsForReplacing.size() > 0;

        this.voxelResTextField = new TextFieldWidget(this.font,
                this.width / 2 + ((this.font.getStringWidth("Voxel resolution:") + 8 + 32) / 2 - 32), this.chooseModelButton.y + this.height/8, 32, 16,
                new StringTextComponent("Voxel resolution"));
        this.voxelResTextField.setValidator(s -> s.matches("[0-9]+") || s.equals(""));
        this.voxelResTextField.setResponder(text -> {
            this.voxelResolutionString = text;
        });

        if (this.voxelResolutionString == null)
            this.voxelResTextField.setText("64");
        else
            this.voxelResTextField.setText(this.voxelResolutionString);

        this.children.add(this.voxelResTextField);
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void tick() {
        super.tick();
        this.voxelResTextField.tick();

        this.voxelizeButton.visible = !this.rasterizer.isWorking;
        this.undoButton.active = !this.rasterizer.isWorking && this.undoBuffer.size() > 0;
        this.replaceMaterialsButton.active = !this.rasterizer.isWorking && this.undoBuffer.size() > 0;
    }

    public void renderBackground(MatrixStack matrixStack) {
        super.renderBackground(matrixStack);
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(matrixStack);
        super.render(matrixStack, mouseX, mouseY, partialTicks);

        String selectedStateMessage = !this.filenameSelected.equals("") ? "Model selected: "+this.filenameSelected : "No model selected";
        this.font.drawString(matrixStack, selectedStateMessage, (int) (this.width / 2 - this.font.getStringWidth(selectedStateMessage) / 2),
                (int) (this.height / 4 - 32), 0xFFFFFF);

        this.font.drawString(matrixStack, "Voxel resolution:",
                this.voxelResTextField.x - this.font.getStringWidth("Voxel resolution:") - 8,
                this.voxelResTextField.y + (int)(this.font.FONT_HEIGHT / 2), 0xFFFFFF);
        this.voxelResTextField.render(matrixStack, mouseX, mouseY, partialTicks);
        if (this.voxelResTextField.isMouseOver(mouseX, mouseY)) {
            this.renderTooltip(matrixStack, new StringTextComponent("Resolution of the building box in voxels (blocks)"), mouseX, mouseY);
        }

        // progress bar
        if (this.rasterizer.isWorking) {
            fill(matrixStack, this.width / 2 - 100, this.voxelizeButton.y,
                    this.width / 2 - 100 + this.progress * 2, this.voxelizeButton.y + 12,
                    0xFFFFFFFF);
            this.font.drawString(matrixStack, this.progress+"%",
                    this.width / 2.0f - this.font.getStringWidth(this.progress+"%") / 2.0f,
                    this.voxelizeButton.y + 2, 0x00DA00);
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputMappings.Input key = InputMappings.getInputByCode(keyCode, scanCode);
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (this.minecraft.gameSettings.keyBindInventory.isActiveAndMatches(key) && !this.voxelResTextField.isFocused()) {
            this.closeScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    protected void voxelize() {
        this.colorToBlockDict = BlocksByAverageColor.getInstance(this.minecraft);
        this.minecraft.getIntegratedServer().runAsync(() -> {
            int voxelResolution = Integer.parseInt(this.voxelResTextField.getText());
            this.rasterizer.rasterizeMeshCuts(this, this.originBlockPos, this.originBlockDirection, filenameSelected, voxelResolution);
            this.world.calculateInitialSkylight(); // then recalculate sky light
        });
    }

    public void setBlockClosestToColor(BlockPos blockPos, Color color) {
        if (!this.undoBuffer.containsKey(blockPos))
            this.undoBuffer.put(blockPos, this.getBlockState(blockPos));
        BlockState blockState = colorToBlockDict.getBlockClosestToColor(color).getDefaultState();
        if (!this.materialsForReplacing.contains(blockState)) {
            this.materialsForReplacing.add(blockState);
        }
        this.setBlockState(blockPos, blockState, 3);
    }

    @Override
    public boolean setBlockState(BlockPos blockPos, BlockState blockState, int flag) {
        return this.world.setBlockState(blockPos, blockState, flag, 512);
    }

    @Override
    public boolean setBlockState(BlockPos blockPos, BlockState blockState, int flag, int i) {
        return this.world.setBlockState(blockPos, blockState, flag, i);
    }

    @Override
    public boolean removeBlock(BlockPos blockPos, boolean b) {
        return false;
    }

    @Override
    public boolean destroyBlock(BlockPos blockPos, boolean b, @Nullable Entity entity, int i) {
        return false;
    }


    @Nullable
    @Override
    public IChunk getChunk(int x, int z, ChunkStatus requiredStatus, boolean nonnull) {
        return null;
    }

    @Override
    public boolean chunkExists(int chunkX, int chunkZ) {
        return false;
    }

    @Override
    public int getHeight(Heightmap.Type heightmapType, int x, int z) {
        return 0;
    }

    @Override
    public int getSkylightSubtracted() {
        return 0;
    }

    @Override
    public BiomeManager getBiomeManager() {
        return null;
    }

    @Override
    public Biome getNoiseBiomeRaw(int x, int y, int z) {
        return null;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public DimensionType getDimensionType() {
        return null;
    }

    @Override
    public float func_230487_a_(Direction p_230487_1_, boolean p_230487_2_) {
        return 0;
    }

    @Override
    public WorldLightManager getLightManager() {
        return null;
    }

    @Override
    public WorldBorder getWorldBorder() {
        return null;
    }

    @Override
    public Stream<VoxelShape> func_230318_c_(@Nullable Entity p_230318_1_, AxisAlignedBB p_230318_2_, Predicate<Entity> p_230318_3_) {
        return null;
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.world.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return null;
    }
}
