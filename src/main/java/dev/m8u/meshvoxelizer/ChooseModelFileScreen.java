package dev.m8u.meshvoxelizer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.list.ExtendedList;
import net.minecraft.util.text.StringTextComponent;

import java.io.File;


public class ChooseModelFileScreen extends Screen {
    protected final VoxelizerScreen voxelizerScreen;

    protected ModelsList modelsList;
    protected Button useThisModelButton;
    protected final String selectedBefore;

    protected ChooseModelFileScreen(VoxelizerScreen caller, String selectedBefore) {
        super(new StringTextComponent("ChooseModelFileScreen"));
        this.voxelizerScreen = caller;
        this.selectedBefore = selectedBefore;
    }

    public boolean isPauseScreen() {
        return false;
    }

    protected void init() {
        this.modelsList = new ModelsList(this.minecraft, this,384, this.height/2,
                (int) ((this.height / 12) + (this.font.FONT_HEIGHT+4) * 2.5) + this.height/10, this.height/4*3, 16);
        listModelsDirectory();

        useThisModelButton = this.addButton(new Button(this.width / 2 - 64, this.height / 10 * 9, 128, 20,
                new StringTextComponent("Use this model"),
                (e) ->  {
            this.voxelizerScreen.filenameSelected = this.modelsList.getSelected().modelName;
            this.minecraft.displayGuiScreen(this.voxelizerScreen);
        }));
    }

    // Fill the gui list with .obj filenames
    protected void listModelsDirectory() {
        File f = new File(this.minecraft.gameDir.getAbsolutePath()+"/mods/MeshVoxelizer");

        for (String fileName : f.list()) {
            if (!fileName.substring(fileName.lastIndexOf('.')).equals(".obj"))
                continue;
            ModelsList.ModelsListEntry entry = this.modelsList.new ModelsListEntry(fileName);
            this.modelsList.addEntry(entry);
            if (fileName.equals(this.selectedBefore)) {
                this.modelsList.setSelected(entry);
            }
        }
        if (this.modelsList.getSelected() == null) {
            this.modelsList.setSelected(this.modelsList.getEntry(0));
        }
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.modelsList.render(matrixStack, mouseX, mouseY, partialTicks);
        super.render(matrixStack, mouseX, mouseY, partialTicks);

        String[] modelsDirectoryMessage = { "Please put your .obj files into 'mods/MeshVoxelizer/' directory",
                "alongside with your .mtl libs and textures (if there any)",
                "Models must be triangulated!" };
        this.font.drawString(matrixStack, modelsDirectoryMessage[0],
                (int) (this.width / 2 - this.font.getStringWidth(modelsDirectoryMessage[0]) / 2),
                (int) (this.height / 12), 0xFFFFFF);
        this.font.drawString(matrixStack, modelsDirectoryMessage[1],
                (int) (this.width / 2 - this.font.getStringWidth(modelsDirectoryMessage[1]) / 2),
                (int) (this.height / 12) + this.font.FONT_HEIGHT+4, 0xFFFFFF);
        this.font.drawString(matrixStack, modelsDirectoryMessage[2],
                (int) (this.width / 2 - this.font.getStringWidth(modelsDirectoryMessage[2]) / 2),
                (int) ((this.height / 12) + (this.font.FONT_HEIGHT+4) * 2.5), 0xFFFF55);
        //this.renderTooltip(matrixStack, new StringTextComponent("Kek"), mouseX, mouseY);

        this.font.drawString(matrixStack, this.modelsList.getSelected().modelName,
                (int) (this.width / 2 - this.font.getStringWidth(this.modelsList.getSelected().modelName) / 2),
                this.useThisModelButton.y - this.font.FONT_HEIGHT*2, 0x55FF55);

    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        super.mouseScrolled(mouseX, mouseY, delta);
        return this.modelsList.mouseScrolled(mouseX, mouseY, delta);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverScrollbar(mouseX)) {
            this.modelsList.setScrollAmount((mouseY - this.modelsList.getTop())
                    * (this.modelsList.getMaxScroll() / (float) (this.modelsList.getBottom() - this.modelsList.getTop())));
            return true;
        }

        ModelsList.ModelsListEntry entry = getEntryUnderMousePos((int) mouseX, (int) mouseY);
        if (entry != null && entry.mouseClicked(mouseX, mouseY, button)) {
            entry.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isMouseOverScrollbar(mouseX)) {
            this.modelsList.setScrollAmount((mouseY - this.modelsList.getTop())
                    * (this.modelsList.getMaxScroll() / (float) (this.modelsList.getBottom() - this.modelsList.getTop())));
            return true;
        }
        return false;
    }

    private boolean isMouseOverScrollbar(double mouseX) {
        return mouseX >= this.modelsList.getScrollbarPosition() - 8 && mouseX < this.modelsList.getScrollbarPosition() + 32;
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
        return this.voxelizerScreen.keyPressed(keyCode, scanCode, modifiers);
    }


    class ModelsList extends ExtendedList<ModelsList.ModelsListEntry> {
        final ChooseModelFileScreen chooseModelFileScreen;

        public ModelsList(Minecraft mcIn, ChooseModelFileScreen caller, int widthIn, int heightIn, int topIn, int bottomIn, int itemHeightIn) {
            super(mcIn, widthIn, heightIn, topIn, bottomIn, itemHeightIn);
            this.func_244605_b(false);
            this.func_244606_c(false);
            this.setLeftPos(mcIn.currentScreen.width / 2 - widthIn / 2);

            this.chooseModelFileScreen = caller;
        }

        protected int getItemCount() {
            return super.getItemCount();
        }

        protected int getScrollbarPosition() {
            return this.chooseModelFileScreen.width / 2 + this.getRowWidth() / 2;
        }

        protected int addEntry(ModelsListEntry entry) {
            return super.addEntry(entry);
        }

        protected ModelsListEntry getEntry(int index) {
            return super.getEntry(index);
        }

        protected void renderBackground(MatrixStack matrixStack) {
            this.chooseModelFileScreen.renderBackground(matrixStack);
        }

        public boolean mouseClicked(double p_mouseClicked_1_, double p_mouseClicked_3_, int p_mouseClicked_5_) {
            return false;
        }


        class ModelsListEntry extends ExtendedList.AbstractListEntry<ModelsList.ModelsListEntry> {
            final String modelName;

            ModelsListEntry(String modelName) {
                this.modelName = modelName;
            }

            public void render(MatrixStack matrixStack, int entryIdx, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean p_194999_5_, float partialTicks) {
                ChooseModelFileScreen.this.font.drawString(matrixStack, this.modelName, left, top + (int) ((16 - font.FONT_HEIGHT) / 2), 0xFFFFFF);
            }

            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (mouseY < ModelsList.this.getBottom() && mouseY > ModelsList.this.getTop()
                        && mouseX < ModelsList.this.getScrollbarPosition() - 8)
                    ModelsList.this.setSelected(this);
                return false;
            }
        }
    }
}
