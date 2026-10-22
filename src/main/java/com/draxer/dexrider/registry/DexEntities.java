package com.draxer.dexrider.registry;

import com.draxer.dexrider.DexRider;
import com.draxer.dexrider.entity.RiderAllyEntity;
import com.draxer.dexrider.entity.RiderEchoEntity;
import com.draxer.dexrider.rider.KamenRide;
import com.draxer.dexrider.vehicle.DexMachineEntity;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Entidades del mod (por ahora, la Dex Machine). */
public final class DexEntities {
    private DexEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, DexRider.MODID);

    /** Motos (la Dex Machine conserva su id "dex_machine"). */
    public static final java.util.Map<com.draxer.dexrider.vehicle.BikeKind, DeferredHolder<EntityType<?>, EntityType<DexMachineEntity>>> BIKES =
            new java.util.EnumMap<>(com.draxer.dexrider.vehicle.BikeKind.class);

    static {
        for (com.draxer.dexrider.vehicle.BikeKind kind : com.draxer.dexrider.vehicle.BikeKind.values()) {
            BIKES.put(kind, ENTITIES.register(kind.id(), () -> EntityType.Builder.<DexMachineEntity>of(
                            (type, level) -> new DexMachineEntity(type, level, kind), MobCategory.MISC)
                    .sized(1.0F, 0.9F)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, DexRider.id(kind.id())))));
        }
    }

    public static DeferredHolder<EntityType<?>, EntityType<DexMachineEntity>> bike(com.draxer.dexrider.vehicle.BikeKind kind) {
        return BIKES.get(kind);
    }

    /** Riders con eco y amigo (los que ya tienen armadura y poderes). */
    public static final KamenRide[] NPC_RIDERS = {KamenRide.NINE, KamenRide.CIPHER, KamenRide.SPORE,
            KamenRide.FORGE, KamenRide.MIRAGE, KamenRide.PROTO, KamenRide.WARDEN, KamenRide.PSYGA, KamenRide.ORGA};

    public static final java.util.Map<KamenRide, DeferredHolder<EntityType<?>, EntityType<RiderEchoEntity>>> ECHOES = new java.util.EnumMap<>(KamenRide.class);
    public static final java.util.Map<KamenRide, DeferredHolder<EntityType<?>, EntityType<RiderAllyEntity>>> ALLIES = new java.util.EnumMap<>(KamenRide.class);

    static {
        for (KamenRide rider : NPC_RIDERS) {
            String echoName = "echo_" + rider.id();
            ECHOES.put(rider, ENTITIES.register(echoName, () -> EntityType.Builder.<RiderEchoEntity>of(
                            (type, level) -> new RiderEchoEntity(type, level, rider), MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, DexRider.id(echoName)))));
            String allyName = "ally_" + rider.id();
            ALLIES.put(rider, ENTITIES.register(allyName, () -> EntityType.Builder.<RiderAllyEntity>of(
                            (type, level) -> new RiderAllyEntity(type, level, rider), MobCategory.MISC)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, DexRider.id(allyName)))));
        }
    }

    @org.jetbrains.annotations.Nullable
    public static DeferredHolder<EntityType<?>, EntityType<RiderAllyEntity>> ally(KamenRide rider) {
        return ALLIES.get(rider);
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        BIKES.values().forEach(h -> event.put(h.get(), DexMachineEntity.createAttributes().build()));
        ECHOES.values().forEach(h -> event.put(h.get(), RiderEchoEntity.createAttributes().build()));
        ALLIES.values().forEach(h -> event.put(h.get(), RiderAllyEntity.createAttributes().build()));
    }

    /** Los ecos aparecen como cualquier monstruo: en el suelo y con poca luz. */
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        for (var holder : ECHOES.values()) {
            event.register(holder.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    RiderEchoEntity::checkSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
        }
    }
}
