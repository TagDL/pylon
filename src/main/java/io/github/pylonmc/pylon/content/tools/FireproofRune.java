package io.github.pylonmc.pylon.content.tools;

import com.destroystokyo.paper.ParticleBuilder;
import io.github.pylonmc.pylon.content.tools.base.Rune;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent;
import io.github.pylonmc.rebar.event.RebarBlockDeserializeEvent;
import io.github.pylonmc.rebar.event.RebarBlockPlaceEvent;
import io.github.pylonmc.rebar.event.RebarBlockSerializeEvent;
import io.github.pylonmc.rebar.event.RebarBlockUnloadEvent;
import io.github.pylonmc.rebar.item.RebarItemSchema;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.RandomizedSound;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DamageResistant;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;
import io.papermc.paper.registry.tag.Tag;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static io.github.pylonmc.pylon.util.PylonUtils.pylonKey;

/**
 * @author balugaq
 */
@SuppressWarnings("UnstableApiUsage")
public class FireproofRune extends Rune {
    public static final Tag<DamageType> IS_FIRE_TAG = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).getTag(DamageTypeTagKeys.IS_FIRE);

    public static final Component SUCCESS = Component.translatable("pylon.message.fireproof_result.success");
    public static final Component TOOLTIP = Component.translatable("pylon.message.fireproof_result.tooltip");

    public static final NamespacedKey FIREPROOF_KEY = pylonKey("have_fireproof");

    private final RandomizedSound applySound = getSettingOrThrow("apply-sound", ConfigAdapter.RANDOMIZED_SOUND);

    public FireproofRune(@NotNull ItemStack stack) {
        super(stack);
    }

    /**
     * Fixes #156 - Fireproof rune can be applied multiple times
     * <p>
     * Checks if the rune is applicable to the target item.
     *
     * @param event  The event
     * @param rune   The rune item, amount may be > 1
     * @param target The item to handle, amount may be > 1
     * @return true if applicable, false otherwise
     */
    @Override
    public boolean isApplicableToTarget(@NotNull PlayerDropItemEvent event, @NotNull ItemStack rune, @NotNull ItemStack target) {
        if (hasRuneApplied(target)) return false;
        DamageResistant data = target.getData(DataComponentTypes.DAMAGE_RESISTANT);
        if (data == null) return true;
        return !data.types().equals(IS_FIRE_TAG);
    }

    /**
     * Handles contacting between an item and a rune.
     *
     * @param event  The event
     * @param rune   The rune item, amount may be > 1
     * @param target The item to handle, amount may be > 1
     */
    @Override
    public void onContactItem(@NotNull PlayerDropItemEvent event, @NotNull ItemStack rune, @NotNull ItemStack target) {
        // As many runes as possible to consume
        int consume = Math.min(rune.getAmount(), target.getAmount());

        Player player = event.getPlayer();
        ItemStack handle = applyRune(target, consume);

        // (N)Either left runes or targets
        int leftRunes = rune.getAmount() - consume;
        int leftTargets = target.getAmount() - consume;

        Location explodeLoc = event.getItemDrop().getLocation();
        World world = explodeLoc.getWorld();
        if (leftRunes > 0) {
            world.dropItemNaturally(explodeLoc, rune.asQuantity(leftRunes)).setGlowing(true);
        }
        if (leftTargets > 0) {
            world.dropItemNaturally(explodeLoc, target.asQuantity(leftTargets)).setGlowing(true);
        }
        world.dropItemNaturally(explodeLoc, handle).setGlowing(true);

        // simple particles
        spawnParticle(Particle.EXPLOSION, explodeLoc, 1);
        spawnParticle(Particle.FLAME, explodeLoc, 50);
        spawnParticle(Particle.SMOKE, explodeLoc, 40);
        world.playSound(applySound.create(), explodeLoc.x(), explodeLoc.y(), explodeLoc.z());

        target.setAmount(0);
        rune.setAmount(0);
        player.sendMessage(SUCCESS);
    }

    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count) {
        new ParticleBuilder(particle)
                .location(location)
                .offset(0, 0, 0)
                .count(count)
                .spawn();
    }

    public static ItemStack applyRune(@NotNull ItemStack itemStack, int amount) {
        return ItemStackBuilder.of(itemStack.asQuantity(amount))
                .set(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(IS_FIRE_TAG))
                .editPdc(pdc -> pdc.set(FIREPROOF_KEY, RebarSerializers.BOOLEAN, true))
                .lore(TOOLTIP)
                .build();
    }

    /**
     * Checks if the target already has the fireproof rune applied
     *
     * @return true if the fireproof rune has been used on the item, false otherwise
     */
    public static boolean hasRuneApplied(@NotNull ItemStack itemStack) {
        return itemStack.getPersistentDataContainer().getOrDefault(FIREPROOF_KEY, RebarSerializers.BOOLEAN, false);
    }

    public static class FireproofRuneListener implements Listener {
        private final List<RebarBlock> fireproofBlocks = new ArrayList<>();

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockPlace(RebarBlockPlaceEvent event) {
            ItemStack itemStack = event.getContext().getItem();
            if (itemStack == null || itemStack.isEmpty()) {
                return;
            }

            if (itemStack.getPersistentDataContainer().has(FIREPROOF_KEY)) {
                fireproofBlocks.add(event.getRebarBlock());
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeserialize(@NotNull RebarBlockDeserializeEvent event) {
            RebarBlock block = event.getRebarBlock();
            if (event.getPdc().has(FIREPROOF_KEY) && !fireproofBlocks.contains(block)) {
                fireproofBlocks.add(block);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSerialize(@NotNull RebarBlockSerializeEvent event) {
            if (fireproofBlocks.contains(event.getRebarBlock())) {
                event.getPdc().set(FIREPROOF_KEY, RebarSerializers.BOOLEAN, true);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockBreak(@NotNull RebarBlockBreakEvent event) {
            RebarBlock block = event.getRebarBlock();
            if (!fireproofBlocks.remove(block)) {
                return;
            }

            for (ItemStack itemStack : event.getDrops()) {
                RebarItemSchema dropSchema = RebarItemSchema.fromStack(itemStack);
                if (dropSchema == null || !block.getKey().equals(dropSchema.getRebarBlockKey())) {
                    continue;
                }

                ItemStack fireproofStack = applyRune(itemStack, 1);
                itemStack.subtract();
                event.getDrops().add(fireproofStack);
                break;
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockUnload(@NotNull RebarBlockUnloadEvent event) {
            fireproofBlocks.remove(event.getRebarBlock());
        }
    }
}
