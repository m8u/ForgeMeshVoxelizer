package dev.m8u.meshvoxelizer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.list.ExtendedList;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.text.StringTextComponent;

import java.io.File;


public class ChooseModelFileScreen extends Screen {
    protected ModelsList modelsList;
    protected Button useThisModelButton;

    protected ChooseModelFileScreen() {
        super(new StringTextComponent("ChooseModelFileScreen"));
    }

    public boolean isPauseScreen() {
        return false;
    }

    protected void init() {
        this.modelsList = new ModelsList(this.minecraft, this.width/5, this.height/2, this.height/6, 256, 16);
        listModelsDirectory();
        //this.modelsList.setSelected(this.modelsList.getEntry(1));

        useThisModelButton = this.addButton(new Button(this.width / 2 - 64, this.height / 3 * 2, 128, 20,
                new StringTextComponent("Use this model"), (p_214187_1_) -> {
            this.minecraft.displayGuiScreen(new VoxelizerScreen());
        }));
    }

    protected void listModelsDirectory() {
        File f = new File(this.minecraft.gameDir.getAbsolutePath()+"/mods/MeshVoxelizer");

        try {
            for (String fileName : f.list()) {
                ModelsList.ModelsListEntry entry = modelsList.new ModelsListEntry(fileName);
                this.modelsList.addEntry(entry);
            }
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.modelsList.render(matrixStack, mouseX, mouseY, partialTicks);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        super.mouseScrolled(mouseX, mouseY, delta);
        return modelsList.mouseScrolled(mouseX, mouseY, delta);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ModelsList.ModelsListEntry entry = getEntryUnderMousePos((int) mouseX, (int) mouseY);
        if (entry != null) {
            System.out.println(entry.modelName);
            entry.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ModelsList.ModelsListEntry getEntryUnderMousePos(final int mouseX, final int mouseY) {
        if (mouseX <= this.modelsList.getLeft() || mouseX >= this.modelsList.getRight()) {
            return null;
        }
        double yOffset = (mouseY - this.modelsList.getTop()) + this.modelsList.getScrollAmount() - 2;
        if (yOffset <= 0)
            return null;

        int index = (int) (yOffset / 16);
        if (index >= this.modelsList.getItemCount() || index < 0)
            return null;

        return modelsList.getEntry(index);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        System.out.println("keyPressed");
        InputMappings.Input mouseKey = InputMappings.getInputByCode(keyCode, scanCode);
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (this.minecraft.gameSettings.keyBindInventory.isActiveAndMatches(mouseKey)) {
            this.closeScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    class ModelsList extends ExtendedList<ModelsList.ModelsListEntry> {


        public ModelsList(Minecraft mcIn, int widthIn, int heightIn, int topIn, int bottomIn, int itemHeightIn) {
            super(mcIn, widthIn, heightIn, topIn, bottomIn, itemHeightIn);
            this.func_244605_b(false);
            this.func_244606_c(false);
            this.setLeftPos(mcIn.currentScreen.width/2 - widthIn/2);
        }

        protected int getItemCount() {
            return super.getItemCount();
        }

        @Override
        protected int getScrollbarPosition() {
            return this.getRight()+16;
        }

        protected int addEntry(ModelsListEntry entry) {
            return super.addEntry(entry);
        }

        protected ModelsListEntry getEntry(int index) {
            return super.getEntry(index);
        }

        protected void renderBackground(MatrixStack matrixStack) {
            ChooseModelFileScreen.this.renderBackground(matrixStack);
        }

        public boolean mouseClicked(double p_mouseClicked_1_, double p_mouseClicked_3_, int p_mouseClicked_5_) {
            System.out.println("[list] mouseClicked");
            return false;
        }


        class ModelsListEntry extends ExtendedList.AbstractListEntry<ModelsList.ModelsListEntry> {
            String modelName;

            ModelsListEntry(String modelName) {
                this.modelName = modelName;
            }

            public void render(MatrixStack matrixStack, int entryIdx, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean p_194999_5_, float partialTicks) {
                ChooseModelFileScreen.this.font.drawString(matrixStack, this.modelName, left, top + (int) ((16 - font.FONT_HEIGHT) / 2), 0xFFFFFF);
            }

            public void mouseMoved(double xPos, double mouseY) {
                System.out.println("mouseMoved");
            }

            public boolean mouseClicked(double p_mouseClicked_1_, double p_mouseClicked_3_, int p_mouseClicked_5_) {
                System.out.println("[entry] mouseClicked");
                ModelsList.this.setSelected(this);
                return false;
            }
        }
    }
}
