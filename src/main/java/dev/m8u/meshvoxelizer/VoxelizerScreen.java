package dev.m8u.meshvoxelizer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.Entity;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.IWorldWriter;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.awt.*;


public class VoxelizerScreen extends Screen implements IWorldWriter {
    World world;
    BlocksByAverageColor colorToBlockDict;

    GLRasterizer rasterizer;

    protected BlockPos originBlockPos;
    protected Direction originBlockDirection;

    protected Button chooseModelButton;
    protected Button voxelizeButton;
    protected TextFieldWidget voxelResTextField;
    protected int progress; // 0 to 100

    protected String filenameSelected;
    protected String voxelResolutionString;


    public VoxelizerScreen(BlockPos originBlockPos, Direction originBlockDirection, String selected) {
        super(new StringTextComponent("VoxelizerScreen"));

        this.originBlockPos = originBlockPos;
        this.originBlockDirection = originBlockDirection;

        this.filenameSelected = selected != null ? selected: "";
    }

    protected void init() {
        this.world = this.minecraft.getIntegratedServer().getWorld(
                this.minecraft.player.world.getDimensionKey());
        this.rasterizer = GLRasterizer.getInstance();

        this.chooseModelButton = this.addButton(new Button(this.width / 2 - 64, this.height / 4, 128, 20,
                new StringTextComponent("Choose model file"), (p_214187_1_) -> {
            this.minecraft.displayGuiScreen(new ChooseModelFileScreen(this, this.filenameSelected));
        }));

        this.voxelizeButton = this.addButton(new Button(this.width /  2 - 32, this.height / 4 * 3, 64, 20,
                new StringTextComponent("Voxelize"),
                (p_214187_1_) -> {
            this.voxelizeButton.active = false;
            this.progress = 0;
            voxelize();
            this.voxelizeButton.active = true;
        }));

        this.voxelResTextField = new TextFieldWidget(this.font,
                this.width / 2 + 8, this.height / 2 + 32, 32, 16,
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

    public boolean isPauseScreen() {
        return false;
    }

    public void tick() {
        super.tick();
        this.voxelResTextField.tick();

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

        // progress bar
        if (this.rasterizer.isWorking) {
            fill(matrixStack, this.width / 2 - 100, this.height / 4 * 3 + 36,
                    this.width / 2 - 100 + this.progress * 2, this.height / 4 * 3 + 36 + 12,
                    0xFFFFFFFF);
            this.font.drawString(matrixStack, this.progress+"%",
                    this.width / 2.0f - this.font.getStringWidth(this.progress+"%") / 2.0f,
                    this.height / 4.0f * 3 + 36 + 2, 0x00DD00);
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
        System.out.println("BLOCK DICTIONARY DEFINED");
        this.minecraft.getIntegratedServer().runAsync(() -> {
            int voxelResolution = Integer.parseInt(this.voxelResTextField.getText());
            rasterizer.rasterizeMeshCuts(this, this.originBlockPos, this.originBlockDirection, filenameSelected, voxelResolution);
            this.world.calculateInitialSkylight(); // then recalculate sky light
        });
    }

    public void setBlockClosestToColor(BlockPos blockPos, Color color) {
        BlockState blockState = colorToBlockDict.getBlockClosestToColor(color).getDefaultState();
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

    public void setProgress(int progress) {
        this.progress = progress;
    }
}
