package com.draxer.dexrider.rider;

import com.draxer.dexrider.entity.RiderAllyEntity;
import com.draxer.dexrider.item.AllySkills;
import com.draxer.dexrider.item.DexDriverItem;
import com.draxer.dexrider.registry.DexComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Los amigos Rider aprenden mirandote trabajar y luego lo hacen ellos.
 *
 *  Aprender : si picas, talas, cultivas o fabricas a 16 bloques o menos de un amigo que te ve, gana experiencia en ese
 *             trabajo (mas o menos segun su personalidad: Mei fabrica, Kinoko cultiva, Genzo pica...). Con 100 lo sabe.
 *             Recuerda que bloques picaste (solo naturales) y la forma exacta de las recetas que fabricaste.
 *  Ayudar   : si ya sabe hacerlo y te ve hacerlo, se pone a tu lado un rato haciendo lo mismo (lo que saca te lo da).
 *  Encargo  : clic derecho sobre el con un pico / hacha / azada / mesa de crafteo = trabaja junto a su casa
 *             (otra vez con lo mismo = para). Lo que saca va a un cofre a 6 bloques de su casa.
 */
public final class AllyWork {
    private AllyWork() {}

    public enum Job {
        MINE, CHOP, FARM, CRAFT;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public Component displayName() { return Component.translatable("job.dexrider." + id()); }

        @Nullable
        public static Job byId(String id) {
            for (Job j : values()) if (j.id().equals(id)) return j;
            return null;
        }

        /** Que herramienta encarga este trabajo. */
        @Nullable
        public static Job forTool(ItemStack stack) {
            if (stack.is(net.minecraft.tags.ItemTags.PICKAXES)) return MINE;
            if (stack.is(net.minecraft.tags.ItemTags.AXES)) return CHOP;
            if (stack.is(net.minecraft.tags.ItemTags.HOES)) return FARM;
            if (stack.is(Items.CRAFTING_TABLE)) return CRAFT;
            return null;
        }
    }

    public static final double WATCH_RANGE = 16;
    public static final int CHEST_RANGE = 6;
    public static final int WORK_RADIUS = 10;

    // ------------------------------------------------------------------ personalidad

    /** Cuanto aprende cada uno de cada trabajo (1 = normal). */
    private static final Map<KamenRide, Map<Job, Float>> AFFINITY = new EnumMap<>(KamenRide.class);

    private static void aff(KamenRide r, float mine, float chop, float farm, float craft) {
        Map<Job, Float> m = new EnumMap<>(Job.class);
        m.put(Job.MINE, mine);
        m.put(Job.CHOP, chop);
        m.put(Job.FARM, farm);
        m.put(Job.CRAFT, craft);
        AFFINITY.put(r, m);
    }

    static {
        aff(KamenRide.NINE, 1.0F, 1.0F, 0.8F, 1.3F);    // Izuku: lo anota todo
        aff(KamenRide.CIPHER, 1.0F, 0.8F, 0.6F, 2.0F);  // Takumi: ingeniero
        aff(KamenRide.SPORE, 0.7F, 1.4F, 2.2F, 0.8F);   // Kinoko: setas y plantas
        aff(KamenRide.FORGE, 1.0F, 0.8F, 0.8F, 2.2F);   // Momo: su Quirk es crear
        aff(KamenRide.MIRAGE, 0.8F, 1.3F, 1.3F, 0.8F);  // Toru: ligera y rapida al aire libre
        aff(KamenRide.PROTO, 1.5F, 0.6F, 0.5F, 2.6F);   // Mei: materiales y bebes
        aff(KamenRide.WARDEN, 1.3F, 1.0F, 0.8F, 0.8F);  // Isamu: disciplina
        aff(KamenRide.PSYGA, 0.6F, 0.8F, 1.9F, 1.1F);   // Nagisa: cuida la casa
        aff(KamenRide.ORGA, 2.1F, 1.6F, 1.0F, 0.7F);    // Genzo: la montania
        aff(KamenRide.KAIXA, 1.2F, 0.8F, 0.6F, 1.7F);   // Tetsuya: termino el Side Basshar sin planos
    }

    public static float affinity(KamenRide rider, Job job) {
        Map<Job, Float> m = AFFINITY.get(rider);
        return m == null ? 1.0F : m.getOrDefault(job, 1.0F);
    }

    /** Experiencia por cada vez que te ve hacerlo (fabricar cuenta mas: se ve menos). */
    public static int gain(KamenRide rider, Job job) {
        int base = job == Job.CRAFT ? 20 : 8;
        return Math.max(1, Math.round(base * affinity(rider, job)));
    }

    /** Velocidad de trabajo segun la experiencia: 1 al aprender, hasta 3 al dominarlo. */
    public static float speed(int xp) {
        if (xp < AllySkills.LEARNED) return 0.0F;
        return 1.0F + 2.0F * (xp - AllySkills.LEARNED) / (float) (AllySkills.MASTER - AllySkills.LEARNED);
    }

    // ------------------------------------------------------------------ datos en el Driver

    public static AllySkills skills(ServerPlayer owner) {
        ItemStack driver = DexDriverItem.findDriver(owner);
        AllySkills s = driver.isEmpty() ? null : driver.get(DexComponents.ALLY_SKILLS.get());
        return s == null ? AllySkills.EMPTY : s;
    }

    public static AllySkills.Entry entry(ServerPlayer owner, KamenRide rider) {
        return skills(owner).get(rider.id());
    }

    public static void save(ServerPlayer owner, KamenRide rider, AllySkills.Entry e) {
        ItemStack driver = DexDriverItem.findDriver(owner);
        if (!driver.isEmpty()) driver.set(DexComponents.ALLY_SKILLS.get(), skills(owner).with(rider.id(), e));
    }

    // ------------------------------------------------------------------ clasificar bloques

    /** Lo que un amigo puede picar sin tocar tus construcciones: piedra natural, minerales, tierra, grava, arena. */
    public static boolean isNaturalMineable(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES)
                || s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.GRAVEL) || s.is(Blocks.SAND)
                || s.is(Blocks.RED_SAND) || s.is(Blocks.CLAY) || s.is(Blocks.COARSE_DIRT);
    }

    public static boolean isOre(BlockState s) {
        return s.is(net.neoforged.neoforge.common.Tags.Blocks.ORES);
    }

    /** Hallazgos que les hacen ilusion. */
    public static boolean isRareFind(BlockState s) {
        return s.is(Blocks.DIAMOND_ORE) || s.is(Blocks.DEEPSLATE_DIAMOND_ORE) || s.is(Blocks.EMERALD_ORE)
                || s.is(Blocks.DEEPSLATE_EMERALD_ORE) || s.is(Blocks.ANCIENT_DEBRIS);
    }

    /** Tronco de un arbol de verdad: tiene hojas naturales cerca. */
    public static boolean isTreeLog(ServerLevel level, BlockPos pos, BlockState s) {
        if (!s.is(BlockTags.LOGS)) return false;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-2, 0, -2), pos.offset(2, 4, 2))) {
            BlockState l = level.getBlockState(p);
            if (l.getBlock() instanceof LeavesBlock && l.hasProperty(LeavesBlock.PERSISTENT) && !l.getValue(LeavesBlock.PERSISTENT)) return true;
        }
        return false;
    }

    public static boolean isRipeCrop(BlockState s) {
        return s.getBlock() instanceof CropBlock crop && crop.isMaxAge(s);
    }

    /** Que trabajo es romper este bloque (para aprender), o null si no ensenia nada. */
    @Nullable
    public static Job jobForBreak(ServerLevel level, BlockPos pos, BlockState s) {
        if (s.getBlock() instanceof CropBlock) return Job.FARM;
        if (s.is(BlockTags.LOGS)) return Job.CHOP;
        if (isNaturalMineable(s)) return Job.MINE;
        return null;
    }

    public static String blockId(BlockState s) {
        return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    // ------------------------------------------------------------------ observar al jugador

    private static final Map<String, Long> LAST_REACTION = new HashMap<>();

    /** Reaccion con su propia espera por amigo y situacion (para no llenar el chat). */
    public static void react(ServerLevel level, RiderAllyEntity ally, RiderDialogue.Line line, int gap, float chance, Object... args) {
        String k = ally.getUUID() + "|" + line.name();
        long now = level.getGameTime();
        Long last = LAST_REACTION.get(k);
        if (last != null && now - last < gap) return;
        if (level.getRandom().nextFloat() > chance) return;
        if (RiderDialogue.say(level, ally, ally.rider(), line, 60, args)) LAST_REACTION.put(k, now);
    }

    /** Amigos tuyos que te estan mirando ahora mismo. */
    public static List<RiderAllyEntity> watchers(ServerPlayer owner) {
        if (!(owner.level() instanceof ServerLevel level)) return List.of();
        UUID id = owner.getUUID();
        return level.getEntitiesOfClass(RiderAllyEntity.class, owner.getBoundingBox().inflate(WATCH_RANGE),
                a -> id.equals(a.owner()) && a.canLearn() && a.hasLineOfSight(owner));
    }

    /** Suma experiencia; avisa cuando lo aprende. Devuelve la entrada nueva. */
    private static AllySkills.Entry learn(ServerLevel level, ServerPlayer owner, RiderAllyEntity ally, AllySkills.Entry e, Job job) {
        int before = e.xp(job.id());
        int after = Math.min(AllySkills.MASTER, before + gain(ally.rider(), job));
        e = e.withXp(job.id(), after);
        if (before < AllySkills.LEARNED && after >= AllySkills.LEARNED) {
            RiderDialogue.force(level, ally, ally.rider(), RiderDialogue.Line.LEARN, job.displayName());
            owner.sendSystemMessage(Component.translatable("message.dexrider.ally_learned", ally.rider().displayName(), job.displayName())
                    .withStyle(ChatFormatting.AQUA));
        }
        return e;
    }

    /** Rompiste un bloque. */
    public static void onBreak(ServerPlayer owner, BlockPos pos, BlockState state) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        Job job = jobForBreak(level, pos, state);
        if (job == null) return;
        if (job == Job.FARM && !isRipeCrop(state)) return;
        for (RiderAllyEntity ally : watchers(owner)) {
            AllySkills.Entry e = entry(owner, ally.rider());
            e = learn(level, owner, ally, e, job);
            if (job == Job.MINE) e = e.withBlock(blockId(state));
            save(owner, ally.rider(), e);
            RiderDialogue.Line see = switch (job) {
                case MINE -> RiderDialogue.Line.SEE_MINE;
                case CHOP -> RiderDialogue.Line.SEE_CHOP;
                default -> RiderDialogue.Line.SEE_FARM;
            };
            if (e.knows(job.id()) && ally.startHelping(job, pos)) {
                react(level, ally, RiderDialogue.Line.HELP, 1200, 1.0F);
            } else {
                react(level, ally, see, 2400, 0.35F);
            }
        }
    }

    /** Pusiste un bloque: sembrar ensenia a cultivar; lo demas es construir (solo reaccionan). */
    public static void onPlace(ServerPlayer owner, BlockPos pos, BlockState placed) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        for (RiderAllyEntity ally : watchers(owner)) {
            if (placed.getBlock() instanceof CropBlock) {
                AllySkills.Entry e = learn(level, owner, ally, entry(owner, ally.rider()), Job.FARM);
                save(owner, ally.rider(), e);
                react(level, ally, RiderDialogue.Line.SEE_FARM, 2400, 0.3F);
            } else {
                react(level, ally, RiderDialogue.Line.SEE_BUILD, 3000, 0.12F);
            }
        }
    }

    /** Fabricaste algo en una mesa: aprenden la receta con su forma. */
    public static void onCraft(ServerPlayer owner, @Nullable AllySkills.Pattern pattern) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        for (RiderAllyEntity ally : watchers(owner)) {
            AllySkills.Entry e = learn(level, owner, ally, entry(owner, ally.rider()), Job.CRAFT);
            if (pattern != null) e = e.withPattern(pattern);
            save(owner, ally.rider(), e);
            react(level, ally, RiderDialogue.Line.SEE_CRAFT, 2400, 0.4F);
        }
    }

    /** La forma de la mesa en el momento de fabricar (null si no hay nada). */
    @Nullable
    public static AllySkills.Pattern patternOf(Container grid) {
        if (!(grid instanceof net.minecraft.world.inventory.CraftingContainer cc)) return null;
        CraftingInput in = cc.asCraftInput();
        if (in.isEmpty()) return null;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            ids.add(s.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
        }
        return new AllySkills.Pattern(in.width(), in.height(), ids);
    }

    // ------------------------------------------------------------------ cofres

    @Nullable
    public static Container chestNear(ServerLevel level, @Nullable BlockPos center) {
        if (center == null) return null;
        Container best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-CHEST_RANGE, -3, -CHEST_RANGE), center.offset(CHEST_RANGE, 3, CHEST_RANGE))) {
            BlockState s = level.getBlockState(p);
            if (!s.is(Blocks.CHEST) && !s.is(Blocks.BARREL) && !s.is(Blocks.TRAPPED_CHEST)) continue;
            BlockEntity be = level.getBlockEntity(p);
            if (be instanceof Container c) {
                double d = p.distSqr(center);
                if (d < bestD) { bestD = d; best = c; }
            }
        }
        return best;
    }

    /** Mete lo que pueda en el contenedor; devuelve lo que sobra. */
    public static ItemStack insert(Container c, ItemStack stack) {
        ItemStack rest = stack.copy();
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            ItemStack in = c.getItem(i);
            if (!in.isEmpty() && ItemStack.isSameItemSameComponents(in, rest) && c.canPlaceItem(i, rest)) {
                int max = Math.min(c.getMaxStackSize(), in.getMaxStackSize());
                int move = Math.min(rest.getCount(), max - in.getCount());
                if (move > 0) { in.grow(move); rest.shrink(move); }
            }
        }
        for (int i = 0; i < c.getContainerSize() && !rest.isEmpty(); i++) {
            if (c.getItem(i).isEmpty() && c.canPlaceItem(i, rest)) {
                c.setItem(i, rest.copy());
                rest = ItemStack.EMPTY;
            }
        }
        c.setChanged();
        return rest;
    }

    // ------------------------------------------------------------------ fabricar

    /** Fabrica una vez la receta con lo que haya en el contenedor; devuelve false si faltan cosas o no hay sitio. */
    public static boolean craftOnce(ServerLevel level, AllySkills.Pattern pattern, Container chest) {
        List<ItemStack> grid = new ArrayList<>();
        Map<Item, Integer> need = new HashMap<>();
        for (String id : pattern.items()) {
            if (id.isEmpty()) { grid.add(ItemStack.EMPTY); continue; }
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(id));
            if (item.isEmpty()) return false;
            grid.add(new ItemStack(item.get()));
            need.merge(item.get(), 1, Integer::sum);
        }
        for (Map.Entry<Item, Integer> n : need.entrySet()) if (count(chest, n.getKey()) < n.getValue()) return false;
        CraftingInput input = CraftingInput.of(pattern.width(), pattern.height(), grid);
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isEmpty()) return false;
        ItemStack result = recipe.get().value().assemble(input);
        if (result.isEmpty() || !fits(chest, result)) return false;
        NonNullList<ItemStack> remain = recipe.get().value().getRemainingItems(input);
        for (Map.Entry<Item, Integer> n : need.entrySet()) take(chest, n.getKey(), n.getValue());
        insert(chest, result);
        for (ItemStack r : remain) if (!r.isEmpty()) insert(chest, r);
        return true;
    }

    public static int count(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    private static void take(Container c, Item item, int amount) {
        for (int i = 0; i < c.getContainerSize() && amount > 0; i++) {
            ItemStack s = c.getItem(i);
            if (s.is(item)) {
                int t = Math.min(amount, s.getCount());
                s.shrink(t);
                amount -= t;
                if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
            }
        }
        c.setChanged();
    }

    private static boolean fits(Container c, ItemStack stack) {
        int room = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) room += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(s, stack)) room += s.getMaxStackSize() - s.getCount();
            if (room >= stack.getCount()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ romper como un jugador

    /** Lo que suelta el bloque con una herramienta de hierro (pico, hacha o pala segun el bloque). */
    public static List<ItemStack> drops(ServerLevel level, BlockPos pos, BlockState s, RiderAllyEntity ally) {
        ItemStack tool;
        if (s.is(BlockTags.MINEABLE_WITH_AXE)) tool = new ItemStack(Items.IRON_AXE);
        else if (s.is(BlockTags.MINEABLE_WITH_SHOVEL)) tool = new ItemStack(Items.IRON_SHOVEL);
        else tool = new ItemStack(Items.IRON_PICKAXE);
        return Block.getDrops(s, level, pos, level.getBlockEntity(pos), ally, tool);
    }

    /** Ticks que tarda en romperlo (segun dureza y su soltura con el trabajo). */
    public static int breakTicks(ServerLevel level, BlockPos pos, BlockState s, int xp) {
        float hard = Math.max(0.3F, s.getDestroySpeed(level, pos));
        return Math.max(4, Math.round(hard * 14.0F / Math.max(1.0F, speed(xp))));
    }

    /** Arbolito para el tronco que se talo ("oak_log" -> "oak_sapling"). */
    @Nullable
    public static BlockState saplingFor(BlockState log) {
        String id = BuiltInRegistries.BLOCK.getKey(log.getBlock()).getPath();
        String base = id.replace("stripped_", "").replace("_log", "").replace("_wood", "");
        Identifier sap = Identifier.withDefaultNamespace(base.equals("mangrove") ? "mangrove_propagule" : base + "_sapling");
        Optional<Block> b = BuiltInRegistries.BLOCK.getOptional(sap);
        return b.map(Block::defaultBlockState).orElse(null);
    }

    public static boolean canPlantOn(BlockState below) {
        return below.is(BlockTags.DIRT);
    }

    public static boolean exposed(ServerLevel level, BlockPos p) {
        for (Direction d : Direction.values()) if (level.getBlockState(p.relative(d)).isAir()) return true;
        return false;
    }

    /** Entregar al jugador (o tirar a sus pies si no le cabe). */
    public static void give(Player p, ItemStack s) {
        if (!p.getInventory().add(s) && !s.isEmpty()) p.drop(s, false);
    }
}
