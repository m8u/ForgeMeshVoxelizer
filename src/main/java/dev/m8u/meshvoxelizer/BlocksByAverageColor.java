package dev.m8u.meshvoxelizer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Direction;
import net.minecraftforge.registries.ForgeRegistries;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class BlocksByAverageColor {
    private Map<Color, Block> dictionary;

    public static BlocksByAverageColor getInstance(Minecraft minecraft) {
        return new BlocksByAverageColor(minecraft);
    }

    private BlocksByAverageColor(Minecraft minecraft) {
        dictionary = new HashMap<>();

        BlockRendererDispatcher blockRendererDispatcher = minecraft.getBlockRendererDispatcher();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (!block.getRegistryName().toString().contains("terracotta"))
                continue;
            try {
                TextureAtlasSprite sprite = blockRendererDispatcher.getModelForState(block.getDefaultState())
                        .getQuads(block.getDefaultState(), Direction.NORTH, minecraft.world.rand).get(0)
                        .getSprite();
                Color averageColor = colorFromABGR(sprite.getPixelRGBA(0, 0, 0));
                Color nextPixelColor;
                for (int y = 0; y < sprite.getHeight(); y++) {
                    for (int x = 0; x < sprite.getWidth(); x++) {
                        nextPixelColor = colorFromABGR(sprite.getPixelRGBA(0, x, y));
                        averageColor = average(averageColor, nextPixelColor);
                    }
                }
                this.dictionary.put(averageColor, block);
                System.out.println(block.getRegistryName());
            } catch (Exception e) {
                //e.printStackTrace();
            }
        }
    }

    private Color colorFromABGR(int abgr) {
        return new Color(abgr & 0x0000FF,
                        (abgr & 0x00FF00) >> 8,
                        (abgr & 0xFF0000) >> 16);
    }

    private Color average(Color first, Color second) {
        return new Color((int)Math.sqrt((Math.pow(first.getRed(), 2) + Math.pow(second.getRed(), 2)) / 2),
                (int)Math.sqrt((Math.pow(first.getGreen(), 2) + Math.pow(second.getGreen(), 2)) / 2),
                (int)Math.sqrt((Math.pow(first.getBlue(), 2) + Math.pow(second.getBlue(), 2)) / 2));
    }

    public Block getBlockClosestToColor(Color color) {
        //System.out.println((long) dictionary.keySet().size());
        Color closest = this.dictionary.keySet().stream().findAny().get();
        Color minColorDiff = new Color(255, 255, 255);
        for (Color colorKey : dictionary.keySet()) {
            if (Math.abs(colorKey.getRed() - color.getRed()) < minColorDiff.getRed()
            && Math.abs(colorKey.getGreen() - color.getGreen()) < minColorDiff.getGreen()
            && Math.abs(colorKey.getBlue() - color.getBlue()) < minColorDiff.getBlue()) {
                minColorDiff = new Color(Math.abs(colorKey.getRed() - color.getRed()),
                        Math.abs(colorKey.getGreen() - color.getGreen()),
                        Math.abs(colorKey.getBlue() - color.getBlue()));
                closest = colorKey;
            }
        }
        //System.out.println(closest);
        return dictionary.get(closest);
    }
}
