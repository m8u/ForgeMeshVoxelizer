package dev.m8u.meshvoxelizer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.security.Key;

public class VoxelizerScreen extends Screen {
    GLRasterizer rasterizer;

    protected BlockPos originBlockPos;

    protected Button chooseModelButton;
    protected Button voxelizeButton;
    protected TextFieldWidget voxelResTextField;

    protected String filenameSelected;

    boolean shouldUnregisterFromEventBus = false;

    public VoxelizerScreen(BlockPos originBlockPos, String selected) {
        super(new StringTextComponent("VoxelizerScreen"));

        this.rasterizer = GLRasterizer.getInstance();

        this.originBlockPos = originBlockPos;
        this.filenameSelected = selected != null ? selected: "";
    }

    protected void init() {
        this.chooseModelButton = this.addButton(new Button(this.width / 2 - 64, this.height / 4, 128, 20,
                new StringTextComponent("Choose model file"), (p_214187_1_) -> {
            this.minecraft.displayGuiScreen(new ChooseModelFileScreen(this.originBlockPos, this.filenameSelected));
        }));

        this.voxelizeButton = this.addButton(new Button(this.width /  2 - 32, this.height / 4 * 3, 64, 20,
                new StringTextComponent("Voxelize"), (p_214187_1_) -> voxelizeInParallel()));

        this.voxelResTextField = new TextFieldWidget(this.font,
                this.width / 2 + 8, this.height / 2 + 32, 32, 16,
                new StringTextComponent("Voxel resolution"));
        this.voxelResTextField.setValidator(s -> s.matches("[0-9]+") || s.equals(""));
        this.voxelResTextField.setText("64");
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

    protected void voxelizeInParallel() {
        new Thread(() -> {
            MinecraftForge.EVENT_BUS.register(this);
            int voxelResolution = Integer.parseInt(this.voxelResTextField.getText());
            Color[][][] voxels = rasterizer.rasterizeMeshCuts(this.minecraft, this.originBlockPos, filenameSelected, voxelResolution);
            this.shouldUnregisterFromEventBus = true;
        }).start();
    }

    @SubscribeEvent
    public void build(final TickEvent event) {
        if (event.type == TickEvent.Type.RENDER) {
            if (!this.rasterizer.blocksStack.isEmpty()) {
                this.minecraft.world.setBlockState(this.rasterizer.blocksStack.get(0).blockPos,
                        this.rasterizer.blocksStack.get(0).blockState, 2);
                System.out.println(this.rasterizer.blocksStack.get(0).blockPos);
                this.rasterizer.blocksStack.remove(0);
            }

            if (shouldUnregisterFromEventBus && this.rasterizer.blocksStack.isEmpty()) {
                MinecraftForge.EVENT_BUS.unregister(this);
            }
        }
    }


}
