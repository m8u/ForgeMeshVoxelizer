package dev.m8u.meshvoxelizer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;

public class ReplaceMaterialsScreen extends Screen {
    protected VoxelizerScreen voxelizerScreen;

    public ReplaceMaterialsScreen(VoxelizerScreen caller, ArrayList<BlockState> materials) {
        super(new StringTextComponent("ReplaceMaterialsScreen"));

        this.voxelizerScreen = caller;
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void renderBackground(MatrixStack matrixStack) {
        super.renderBackground(matrixStack);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.voxelizerScreen.keyPressed(keyCode, scanCode, modifiers);
    }
}
