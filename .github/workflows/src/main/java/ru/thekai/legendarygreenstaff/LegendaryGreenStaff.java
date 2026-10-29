package ru.thekai.legendarygreenstaff;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(LegendaryGreenStaff.MOD_ID)
public class LegendaryGreenStaff {
    public static final String MOD_ID = "legendarygreenstaff";

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    public static final RegistryObject<Item> STAFF = ITEMS.register(
            "legendary_staff",
            () -> new LegendaryStaffItem(
                    new Item.Properties()
                            .stacksTo(1)
                            .durability(500)
                            .rarity(Rarity.EPIC)
            )
    );

    public LegendaryGreenStaff() {
        IEventBus modBus =
                FMLJavaModLoadingContext.get().getModEventBus();

        ITEMS.register(modBus);
        modBus.addListener(this::addCreativeItems);
    }

    private void addCreativeItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.COMBAT)
                || event.getTabKey().equals(
                        CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(STAFF);
        }
    }
}
