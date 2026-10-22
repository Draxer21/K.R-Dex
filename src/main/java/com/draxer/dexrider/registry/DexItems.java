package com.draxer.dexrider.registry;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.item.DexArmorItem;
import com.draxer.dexrider.item.DexCard;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.item.DexWeaponItem;
import com.draxer.dexrider.item.KTouchItem;
import com.draxer.dexrider.item.DexWeaponItem.WeaponKind;
import com.draxer.dexrider.item.RiderCardItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class DexItems {
    private DexItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(DexRider.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DexRider.MODID);

    // ---------- Armadura de Dex ----------
    // El nombre "dex" apunta a assets/dexrider/equipment/dex.json
    public static final ResourceKey<EquipmentAsset> DEX_ASSET =
            ResourceKey.create(EquipmentAssets.ROOT_ID, DexRider.id("dex"));

    // Traje de Dex: por encima de netherite (24 de armadura, dureza 3.5), para aguantar peleas de Rider contra Rider.
    public static final ArmorMaterial DEX_ARMOR_MATERIAL = new ArmorMaterial(
            40,
            Map.of(
                    ArmorType.HELMET, 4,
                    ArmorType.CHESTPLATE, 9,
                    ArmorType.LEGGINGS, 7,
                    ArmorType.BOOTS, 4,
                    ArmorType.BODY, 9),
            1,                                  // minimo permitido (26.1 exige > 0); la armadura nunca llega a una mesa
            SoundEvents.ARMOR_EQUIP_NETHERITE,
            3.5F,                               // dureza
            0.25F,                              // resistencia al empuje
            ItemTags.REPAIRS_NETHERITE_ARMOR,
            DEX_ASSET);

    public static final DeferredItem<DexArmorItem> DEX_HELMET = armor("dex_helmet", ArmorType.HELMET);
    public static final DeferredItem<DexArmorItem> DEX_CHESTPLATE = armor("dex_chestplate", ArmorType.CHESTPLATE);
    public static final DeferredItem<DexArmorItem> DEX_LEGGINGS = armor("dex_leggings", ArmorType.LEGGINGS);
    public static final DeferredItem<DexArmorItem> DEX_BOOTS = armor("dex_boots", ArmorType.BOOTS);

    /** Armadura con modelo 3D de GeckoLib (ver DexArmorItem / client.DexArmorRenderer). */
    private static DeferredItem<DexArmorItem> armor(String name, ArmorType type) {
        return ITEMS.registerItem(name, props -> new DexArmorItem(props
                .humanoidArmor(DEX_ARMOR_MATERIAL, type)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .rarity(Rarity.EPIC)));
    }

    // ---------- Driver ----------
    public static final DeferredItem<DexDriverItem> DEX_DRIVER = ITEMS.registerItem("dex_driver",
            props -> new DexDriverItem(props.stacksTo(1).rarity(Rarity.EPIC)));

    // ---------- Guia del Rider System (el cuaderno de Takumi) ----------
    public static final DeferredItem<com.draxer.dexrider.item.RiderGuideItem> RIDER_GUIDE = ITEMS.registerItem("rider_guide",
            props -> new com.draxer.dexrider.item.RiderGuideItem(props.stacksTo(1)));

    // ---------- K-Touch y carta de Complete Form (los forja el Driver con nueve Kamen Ride) ----------
    public static final DeferredItem<KTouchItem> K_TOUCH = ITEMS.registerItem("k_touch",
            props -> new KTouchItem(props.stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<Item> COMPLETE_FORM_CARD = ITEMS.registerItem("complete_form_card",
            props -> new Item(props.stacksTo(1).rarity(Rarity.EPIC)));

    // ---------- Equipo de Cipher (del fanfic): el reloj Accel (Takumi) y el arma Blaster (la guarda Genzo) ----------
    public static final DeferredItem<com.draxer.dexrider.item.CipherGearItem> CIPHER_ACCEL = ITEMS.registerItem("cipher_accel",
            props -> new com.draxer.dexrider.item.CipherGearItem(props.stacksTo(1).rarity(Rarity.EPIC),
                    com.draxer.dexrider.item.CipherGearItem.Kind.ACCEL));
    public static final DeferredItem<com.draxer.dexrider.item.CipherGearItem> CIPHER_BLASTER = ITEMS.registerItem("cipher_blaster",
            props -> new com.draxer.dexrider.item.CipherGearItem(props.stacksTo(1).rarity(Rarity.EPIC)
                    .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 9, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.6, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build()),
                    com.draxer.dexrider.item.CipherGearItem.Kind.BLASTER));

    // ---------- Cartas (una por cada valor de DexCard) ----------
    public static final Map<DexCard, DeferredItem<RiderCardItem>> CARDS = new EnumMap<>(DexCard.class);

    static {
        for (DexCard card : DexCard.values()) {
            Rarity rarity = card.family() == DexCard.Family.FINAL_ATTACK_RIDE ? Rarity.EPIC : Rarity.RARE;
            CARDS.put(card, ITEMS.registerItem(card.itemName(),
                    props -> new RiderCardItem(props.stacksTo(1).rarity(rarity), card)));
        }
    }

    public static DeferredItem<RiderCardItem> card(DexCard card) {
        return CARDS.get(card);
    }

    // ---------- Armas Armed Ride ----------
    // dano = dano EXTRA sobre el punio (1). velocidad = modificador sobre 4.0 (espada vanilla = -2.4).
    public static final DeferredItem<DexWeaponItem> RIDE_BOOKER = weapon("ride_booker", WeaponKind.RIDE_BOOKER, 7, -2.4);
    public static final DeferredItem<DexWeaponItem> SHOCK_BATON = weapon("shock_baton", WeaponKind.SHOCK_BATON, 5, -2.0);
    public static final DeferredItem<DexWeaponItem> BLAST_SHOT = weapon("blast_shot", WeaponKind.BLAST_SHOT, 2, -2.0);
    public static final DeferredItem<DexWeaponItem> DEX_SWORD = weapon("dex_sword", WeaponKind.SWORD, 9, -2.6);
    public static final DeferredItem<DexWeaponItem> VOLT_CHAIN = weapon("volt_chain", WeaponKind.VOLT_CHAIN, 4, -2.2);
    public static final DeferredItem<DexWeaponItem> POWER_KNUCKLE = weapon("power_knuckle", WeaponKind.POWER_KNUCKLE, 10, -2.8);
    // armas de los Riders
    public static final DeferredItem<DexWeaponItem> CIPHER_EDGE = weapon("cipher_edge", WeaponKind.CIPHER_EDGE, 8, -2.3);
    public static final DeferredItem<DexWeaponItem> PRISM_EDGE = weapon("prism_edge", WeaponKind.PRISM_EDGE, 7, -2.1);
    public static final DeferredItem<DexWeaponItem> MODULE_DRIVER = weapon("module_driver", WeaponKind.MODULE_DRIVER, 3, -2.2);
    public static final DeferredItem<DexWeaponItem> FORGE_LANCE = weapon("forge_lance", WeaponKind.FORGE_LANCE, 9, -2.7);
    public static final DeferredItem<DexWeaponItem> WARDEN_BATON = weapon("warden_baton", WeaponKind.WARDEN_BATON, 6, -2.0);
    public static final DeferredItem<DexWeaponItem> ORGA_HALBERD = weapon("orga_halberd", WeaponKind.ORGA_HALBERD, 13, -3.0);
    public static final DeferredItem<DexWeaponItem> SPORE_ROD = weapon("spore_rod", WeaponKind.SPORE_ROD, 2, -2.0);

    private static DeferredItem<DexWeaponItem> weapon(String name, WeaponKind kind, double damage, double speed) {
        return ITEMS.registerItem(name, props -> new DexWeaponItem(props
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .attributes(ItemAttributeModifiers.builder()
                        .add(Attributes.ATTACK_DAMAGE,
                                new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .add(Attributes.ATTACK_SPEED,
                                new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE),
                                EquipmentSlotGroup.MAINHAND)
                        .build()),
                kind));
    }

    /** Que arma materializa cada carta Armed Ride. */
    public static Item weaponFor(DexCard card) {
        return switch (card) {
            case ARMED_RIDE_RIDE_BOOKER -> RIDE_BOOKER.get();
            case ARMED_RIDE_SHOCK_BATON -> SHOCK_BATON.get();
            case ARMED_RIDE_BLAST_SHOT -> BLAST_SHOT.get();
            case ARMED_RIDE_SWORD -> DEX_SWORD.get();
            case ARMED_RIDE_VOLT_CHAIN -> VOLT_CHAIN.get();
            case ARMED_RIDE_POWER_KNUCKLE -> POWER_KNUCKLE.get();
            case ARMED_RIDE_CIPHER_EDGE -> CIPHER_EDGE.get();
            case ARMED_RIDE_PRISM_EDGE -> PRISM_EDGE.get();
            case ARMED_RIDE_MODULE_DRIVER -> MODULE_DRIVER.get();
            case ARMED_RIDE_FORGE_LANCE -> FORGE_LANCE.get();
            case ARMED_RIDE_WARDEN_BATON -> WARDEN_BATON.get();
            case ARMED_RIDE_ORGA_HALBERD -> ORGA_HALBERD.get();
            case ARMED_RIDE_SPORE_ROD -> SPORE_ROD.get();
            default -> null;
        };
    }

    // ---------- Pestana del modo creativo ----------
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEX_TAB = TABS.register("dex_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.dexrider"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> DEX_DRIVER.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(DEX_DRIVER.get());
                        output.accept(RIDER_GUIDE.get());
                        for (DexCard card : DexCard.values()) {
                            output.accept(CARDS.get(card).get());
                        }
                        output.accept(K_TOUCH.get());
                        output.accept(COMPLETE_FORM_CARD.get());
                        output.accept(CIPHER_ACCEL.get());
                        output.accept(CIPHER_BLASTER.get());
                    })
                    .build());

    public static boolean isDexArmor(Item item) {
        return item == DEX_HELMET.get() || item == DEX_CHESTPLATE.get()
                || item == DEX_LEGGINGS.get() || item == DEX_BOOTS.get();
    }

    public static boolean isDexWeapon(Item item) {
        return item instanceof DexWeaponItem;
    }

    /** Objetos que solo existen mientras Dex esta transformado. */
    public static boolean isRiderBound(Item item) {
        return isDexArmor(item) || isDexWeapon(item);
    }
}
