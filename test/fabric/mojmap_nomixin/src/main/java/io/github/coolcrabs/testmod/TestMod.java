package io.github.coolcrabs.testmod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules.Category;
import io.github.coolcrabs.testmod.template.Template;

public class TestMod implements ModInitializer {
    @Override
    public void onInitialize() {
        System.out.println(Template.BOOLEAN);
        System.out.println(Template.BYTE);
        System.out.println(Template.SHORT);
        System.out.println(Template.INT);
        System.out.println(Template.LONG);
        System.out.println(Template.STRING);

        System.out.println("also check the file in the mod jar");

        Registry.register(Registry.ITEM, new ResourceLocation("brachyuratestmod", "epic"), new Item(new Item.Properties()));
    }
}
