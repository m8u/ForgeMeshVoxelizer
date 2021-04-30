package dev.m8u.meshvoxelizer;

import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.list.ExtendedList;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.util.InputMappings;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;


public class ReplaceMaterialsScreen extends Screen {
    protected VoxelizerScreen voxelizerScreen;

    Map<UniqueNumberedBlockState, ArrayList<BlockPos>> posListsByMaterials;
    ArrayList<UniqueNumberedBlockState> originalBlockStates;

    MaterialsList materialsList;
    TextFieldWidget newMaterialTextField;
    Button applyButton;
    Button backButton;

    public ReplaceMaterialsScreen(VoxelizerScreen caller, Map<BlockState, ArrayList<BlockPos>> posListsByMaterials) {
        super(new StringTextComponent("ReplaceMaterialsScreen"));

        this.voxelizerScreen = caller;
        this.posListsByMaterials = new HashMap<>();
        posListsByMaterials.forEach((blockState, blockPos) -> {
            this.posListsByMaterials.put(new UniqueNumberedBlockState(blockState, 0), blockPos);
        });
    }

    protected void init() {
        this.materialsList = new MaterialsList(this.minecraft, this, 240, this.height/2,
                this.height/12, this.height/4*3, 16);
        this.originalBlockStates = new ArrayList<>();
        this.originalBlockStates.addAll(this.posListsByMaterials.keySet());
        for (int i = 0; i < this.originalBlockStates.size(); i++) {
            this.materialsList.addEntry(this.materialsList.new MaterialToReplaceEntry(this.originalBlockStates.get(i), i));
        }

        this.applyButton = this.addButton(new Button(this.width/2 - 32, height - (int) (this.height/5), 64, 20,
                new StringTextComponent("Apply"),
                (e) -> {
                    this.posListsByMaterials.forEach(((uniqueNumberedBlockState, blockPoses) -> {
                        if (!this.originalBlockStates.contains(uniqueNumberedBlockState.blockState)) {
                            blockPoses.forEach((blockPos -> {
                                this.voxelizerScreen.setBlockState(blockPos, uniqueNumberedBlockState.blockState, 3);
                            }));
                        }
                    }));
                }));

        this.backButton = this.addButton(new Button(this.width/2 - 32, this.applyButton.y + 24, 64, 20,
                new StringTextComponent("Back"),
                (e) -> {
                    this.minecraft.displayGuiScreen(this.voxelizerScreen);
                }));
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void tick() {
        super.tick();
        if (this.newMaterialTextField != null)
            this.newMaterialTextField.tick();
    }

    public void renderBackground(MatrixStack matrixStack) {
        super.renderBackground(matrixStack);
    }

    public void render(MatrixStack matrixStack, int mouseX, int mouseY, float partialTicks) {
        this.materialsList.render(matrixStack, mouseX, mouseY, partialTicks);
        if (this.newMaterialTextField != null)
            this.newMaterialTextField.render(matrixStack, mouseX, mouseY, partialTicks);
        super.render(matrixStack, mouseX, mouseY, partialTicks);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.materialsList.getSelected() != null)
            this.applyMaterialTextField();
        super.mouseScrolled(mouseX, mouseY, delta);
        return this.materialsList.mouseScrolled(mouseX, mouseY, delta);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverScrollbar(mouseX)) {
            if (this.materialsList.getSelected() != null)
                this.applyMaterialTextField();
            this.materialsList.setScrollAmount((mouseY - this.materialsList.getTop())
                    * (this.materialsList.getMaxScroll() / (float) (this.materialsList.getBottom() - this.materialsList.getTop())));
            return true;
        }

        MaterialsList.MaterialToReplaceEntry entry = getEntryUnderMousePos((int) mouseX, (int) mouseY);
        if (entry != null && entry.mouseClicked(mouseX, mouseY, button)) {
            entry.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isMouseOverScrollbar(mouseX)) {
            if (this.materialsList.getSelected() != null)
                this.applyMaterialTextField();
            this.materialsList.setScrollAmount((mouseY - this.materialsList.getTop())
                    * (this.materialsList.getMaxScroll() / (float) (this.materialsList.getBottom() - this.materialsList.getTop())));
            return true;
        }
        return false;
    }

    private boolean isMouseOverScrollbar(double mouseX) {
        return mouseX >= this.materialsList.getScrollbarPosition() - 8 && mouseX < this.materialsList.getScrollbarPosition() + 32;
    }

    private MaterialsList.MaterialToReplaceEntry getEntryUnderMousePos(final int mouseX, final int mouseY) {
        if (mouseX <= this.materialsList.getLeft() || mouseX >= this.materialsList.getRight()) {
            return null;
        }
        double yOffset = (mouseY - this.materialsList.getTop()) + this.materialsList.getScrollAmount() - 2;
        if (yOffset <= 0)
            return null;

        int index = (int) (yOffset / 16);
        if (index >= this.materialsList.getItemCount() || index < 0)
            return null;

        return materialsList.getEntry(index);
    }
    
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        InputMappings.Input key = InputMappings.getInputByCode(keyCode, scanCode);
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        } else if (this.minecraft.gameSettings.keyBindInventory.isActiveAndMatches(key) && this.newMaterialTextField == null) {
            this.closeScreen();
            return true;
        } else if (keyCode == 257) {
            if (this.materialsList.getSelected() != null)
                this.applyMaterialTextField();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        if (this.children.size() > 2)
            this.children.remove(this.children.size() - 1);
        this.newMaterialTextField = null;
    }

    public void createMaterialTextField(int left, int top, int width, int height, String originalRegistryName) {
        this.newMaterialTextField = new TextFieldWidget(this.font, left, top, width, height,
                new StringTextComponent("New material"));
        this.children.add(this.newMaterialTextField);
        this.newMaterialTextField.setMaxStringLength(256);
        this.newMaterialTextField.setText(originalRegistryName);
        this.newMaterialTextField.setFocused2(true);
        this.minecraft.keyboardListener.enableRepeatEvents(true);
    }

    public void applyMaterialTextField() {
        if (!String.valueOf(this.materialsList.getSelected().uniqueNumberedBlockState.blockState.getBlock().getRegistryName())
                .equals(this.newMaterialTextField.getText())) {
            try {
                BlockState newBlockDefaultState = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(this.newMaterialTextField.getText())).getDefaultState();
                UniqueNumberedBlockState newUniqueNumberedBlockState = new UniqueNumberedBlockState(newBlockDefaultState, 0);
                for (; ; newUniqueNumberedBlockState.number++) {
                    boolean contains = false;
                    for (Map.Entry<UniqueNumberedBlockState, ArrayList<BlockPos>> entry : this.posListsByMaterials.entrySet()) {
                        if (String.valueOf(entry.getKey().blockState.getBlock().getRegistryName())
                                .equals(String.valueOf(newUniqueNumberedBlockState.blockState.getBlock().getRegistryName()))
                            && entry.getKey().number == newUniqueNumberedBlockState.number) {
                            contains = true;
                            break;
                        }
                    }
                    if (!contains)
                        break;
                }
                this.posListsByMaterials.put(newUniqueNumberedBlockState,
                        this.posListsByMaterials.get(this.materialsList.getSelected().uniqueNumberedBlockState));
                this.posListsByMaterials.remove(this.materialsList.getSelected().uniqueNumberedBlockState);
                this.materialsList.getSelected().uniqueNumberedBlockState = newUniqueNumberedBlockState;
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }

        if (this.children.size() > 2)
            this.children.remove(this.children.size() - 1);
        this.newMaterialTextField = null;
        this.materialsList.setSelected(null);
    }


    class UniqueNumberedBlockState {
        BlockState blockState;
        int number;

        UniqueNumberedBlockState(BlockState blockState, int number) {
            this.blockState = blockState;
            this.number = number;
        }
    }


    class MaterialsList extends ExtendedList<MaterialsList.MaterialToReplaceEntry> {
        Minecraft minecraft;
        ReplaceMaterialsScreen replaceMaterialsScreen;
        BlockRendererDispatcher blockRendererDispatcher;

        int actualWidth;

        public MaterialsList(Minecraft mcIn, ReplaceMaterialsScreen caller, int widthIn, int heightIn, int topIn, int bottomIn, int itemHeightIn) {
            super(mcIn, widthIn, heightIn, topIn, bottomIn, itemHeightIn);
            this.func_244605_b(false);
            this.func_244606_c(false);
            this.setLeftPos(mcIn.currentScreen.width / 2 - widthIn / 2);

            this.replaceMaterialsScreen = caller;
            this.minecraft = mcIn;
            this.blockRendererDispatcher = mcIn.getBlockRendererDispatcher();

            this.actualWidth = widthIn;
        }

        protected int getItemCount() {
            return super.getItemCount();
        }

        protected int getScrollbarPosition() {
            return this.replaceMaterialsScreen.width / 2 + this.getRowWidth() / 2;
        }

        public int getRowLeft() {
            return (int) (this.minecraft.currentScreen.width / 2.0 - this.actualWidth / 2.0);
        }

        public int getRowWidth() {
            return this.actualWidth;
        }

        public int getRowTop(int index) {
            return super.getRowTop(index);
        }

        protected int addEntry(MaterialToReplaceEntry entry) {
            return super.addEntry(entry);
        }

        protected MaterialToReplaceEntry getEntry(int index) {
            return super.getEntry(index);
        }

        protected void renderBackground(MatrixStack matrixStack) {
            this.replaceMaterialsScreen.renderBackground(matrixStack);
        }

        public boolean mouseClicked(double p_mouseClicked_1_, double p_mouseClicked_3_, int p_mouseClicked_5_) {
            return false;
        }


        class MaterialToReplaceEntry extends ExtendedList.AbstractListEntry<MaterialsList.MaterialToReplaceEntry> {
            int index;
            UniqueNumberedBlockState uniqueNumberedBlockState;

            MaterialToReplaceEntry(UniqueNumberedBlockState originalBlockState, int index) {
                this.index = index;
                this.uniqueNumberedBlockState = originalBlockState;
            }

            public void render(MatrixStack matrixStack, int entryIdx, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean p_194999_5_, float partialTicks) {
                minecraft.getItemRenderer().renderItemIntoGUI(this.uniqueNumberedBlockState.blockState.getBlock().asItem().getDefaultInstance(), left - 20, top);
                if (MaterialsList.this.getSelected() != this) {
                    ReplaceMaterialsScreen.this.font.drawString(matrixStack,
                            (this.uniqueNumberedBlockState.number > 0 ? "("+this.uniqueNumberedBlockState.number+") " : "")
                                    + this.uniqueNumberedBlockState.blockState.getBlock().getRegistryName(),
                            left + 1, top + (int) ((16 - font.FONT_HEIGHT) / 2), 0xFFFFFF);
                }
            }

            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (mouseY < MaterialsList.this.getBottom() && mouseY > MaterialsList.this.getTop()
                        && mouseX < MaterialsList.this.getScrollbarPosition() - 8
                        && MaterialsList.this.getSelected() != this) {
                    if (MaterialsList.this.getSelected() != null)
                        ReplaceMaterialsScreen.this.applyMaterialTextField();
                    MaterialsList.this.setSelected(this);
                    ReplaceMaterialsScreen.this.createMaterialTextField(MaterialsList.this.getRowLeft()-1, MaterialsList.this.getRowTop(this.index)-1,
                            MaterialsList.this.getRowWidth(), 14,
                            String.valueOf(this.uniqueNumberedBlockState.blockState.getBlock().getRegistryName()));
                }
                return false;
            }
        }
    }
}
