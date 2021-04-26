package dev.m8u.meshvoxelizer;

import net.minecraft.block.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Direction;
import net.minecraftforge.registries.ForgeRegistries;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class BlocksByAverageColor {
    private Map<Color, Block> dictionary;

    public static BlocksByAverageColor getInstance(Minecraft minecraft) {
        return new BlocksByAverageColor(minecraft);
    }

    private BlocksByAverageColor(Minecraft minecraft) {
        this.dictionary = new HashMap<>();

        BlockRendererDispatcher blockRendererDispatcher = minecraft.getBlockRendererDispatcher();
        for (Block block : ForgeRegistries.BLOCKS) {
            if (!(block.getClass().equals(Block.class))) {
                //System.out.println("NOT A CUBE BLOCK");
                continue;
            }
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
                //System.out.println(block.getRegistryName() + averageColor.toString());
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
        return new Color((first.getRed() + second.getRed()) / 2,
                (first.getGreen() + second.getGreen()) / 2,
                (first.getBlue() + second.getBlue()) / 2);
    }

    public Block getBlockClosestToColor(Color color) {
        //System.out.println((long) dictionary.keySet().size());
        Color closest = this.dictionary.keySet().stream().findAny().get();
        double minColorDiff = weightedColorDiff(Color.BLACK, Color.WHITE);
        for (Color colorKey : dictionary.keySet()) {
            if (weightedColorDiff(colorKey, color) < minColorDiff) {
                minColorDiff = weightedColorDiff(colorKey, color);
                closest = colorKey;
            }
        }
        //System.out.println(dictionary.get(closest).getRegistryName()+" "+ closest.toString()+" for "+color.toString());
        return dictionary.get(closest);
    }

    public double weightedColorDiff(Color first, Color second) {
        return Math.sqrt(Math.pow(((first.getRed()-second.getRed())), 2)
                + Math.pow(((first.getGreen()-second.getGreen())), 2)
                + Math.pow(((first.getBlue()-second.getBlue())), 2));
    }
}
