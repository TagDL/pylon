package io.github.pylonmc.pylon.content.tools.base;

import io.github.pylonmc.pylon.Pylon;
import io.github.pylonmc.pylon.PylonConfig;
import io.github.pylonmc.pylon.content.tools.FireproofRune;
import io.github.pylonmc.rebar.block.context.BlockBreakContext.PlayerBreak;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent;
import io.github.pylonmc.rebar.event.RebarBlockPlaceEvent;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.RebarItemSchema;
import io.github.pylonmc.rebar.item.interfaces.ArrowRebarItemHandler;
import io.github.pylonmc.rebar.item.interfaces.BlockBreakRebarItemHandler;
import io.github.pylonmc.rebar.item.interfaces.BowRebarItemHandler;
import io.github.pylonmc.rebar.item.interfaces.BucketRebarItemHandler;
import io.github.pylonmc.rebar.item.interfaces.EntityAttackRebarItemHandler;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DamageResistant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import static io.github.pylonmc.pylon.util.PylonUtils.pylonKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author balugaq
 */
public abstract class Rune extends RebarItem {
    // These can be applied with runes
    public static final List<Class<?>> DEFAULT_APPLICABLES = List.of(
            ArrowRebarItemHandler.class,
            BowRebarItemHandler.class,
            BucketRebarItemHandler.class,
            BlockBreakRebarItemHandler.class,
            EntityAttackRebarItemHandler.class
    );

    public Rune(@NotNull ItemStack stack) {
        super(stack);
    }

    /**
     * Checks if the rune is applicable to the target item.
     *
     * @param event  The event
     * @param rune   The rune item, amount may be > 1
     * @param target The item to handle, amount may be > 1
     * @return true if applicable, false otherwise
     */
    public boolean isApplicableToTarget(@NotNull PlayerDropItemEvent event, @NotNull ItemStack rune, @NotNull ItemStack target) {
        RebarItemSchema schema = RebarItemSchema.fromStack(target);
        if (schema == null) {
            // Non-Rebar items are always applicable
            return true;
        }

        RuneApplicable checker = RebarItem.fromStack(target, RuneApplicable.class);
        if (checker != null && checker.applicableToTarget(event, rune)) {
            return true;
        }

        return DEFAULT_APPLICABLES.stream().anyMatch(clazz -> clazz.isAssignableFrom(schema.getItemClass()));
    }

    /**
     * Handles contacting between an item and a rune.
     *
     * @param event  The event
     * @param rune   The rune item, amount may be > 1
     * @param target The item to handle, amount may be > 1
     */
    public abstract void onContactItem(@NotNull PlayerDropItemEvent event, @NotNull ItemStack rune, @NotNull ItemStack target);

    public static class RuneListener implements Listener {
        private static final String soulbound_prefix = "block_soulbound";
        private static final String fireproof_prefix = "block_fireproof";

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onRuneDrop(@NotNull PlayerDropItemEvent event) {
            Player player = event.getPlayer();
            Item runeEntity = event.getItemDrop();
            ItemStack runeStack = runeEntity.getItemStack();
            Rune rune = RebarItem.fromStack(runeStack, Rune.class);
            if (rune == null) {
                return;
            }

            // Fix #155 - Fireproof rune only checks proximity at the moment it's dropped
            // Force run synchronously for entity handling
            Bukkit.getScheduler().runTaskTimer(Pylon.getInstance(), task -> {
                if (runeEntity.isDead() || !runeEntity.isValid()) {
                    task.cancel();
                    return;
                }

                if (!runeEntity.isOnGround()) {
                    return;
                }

                Collection<Item> nearbyEntities = player.getWorld().getNearbyEntitiesByType(Item.class, runeEntity.getLocation(), PylonConfig.RUNE_CHECK_RANGE, item -> rune.isApplicableToTarget(event, runeStack, item.getItemStack()));
                Item targetEntity = nearbyEntities
                        .stream()
                        .findFirst()
                        .orElse(null);

                if (targetEntity == null) {
                    // No target, skip it.
                    return;
                }

                ItemStack target = targetEntity.getItemStack();

                // All actions are handled by devs
                rune.onContactItem(event, runeStack, target);
                runeEntity.setItemStack(runeStack);
                targetEntity.setItemStack(target);
            }, 1, 2);
        }
        
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockPlace(RebarBlockPlaceEvent event) {
            ItemStack itemStack = event.getContext().getItem().asOne();
            Block block = event.getBlock();
            if (itemStack.getPersistentDataContainer().has(pylonKey("soulbound"))) {
                block.getChunk().getPersistentDataContainer()
                    .set(newKey(soulbound_prefix, block), RebarSerializers.BOOLEAN, true);
            }
            if (itemStack.getData(DataComponentTypes.DAMAGE_RESISTANT) == null ? false
                        : itemStack.getData(DataComponentTypes.DAMAGE_RESISTANT).types().equals(FireproofRune.IS_FIRE_TAG)) {
                block.getChunk().getPersistentDataContainer()
                    .set(newKey(fireproof_prefix, block), RebarSerializers.BOOLEAN, true);
            }
        }
        
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockBreak(@NotNull RebarBlockBreakEvent event) {
            Block block = event.getBlock();
            if (!(event.getContext() instanceof PlayerBreak playerBreak)) return;
            Player player = playerBreak.event().getPlayer();
            Boolean soulbound = block.getChunk().getPersistentDataContainer()
                .getOrDefault(newKey(soulbound_prefix, block), RebarSerializers.BOOLEAN, false);
            Boolean fireproof = block.getChunk().getPersistentDataContainer()
                .getOrDefault(newKey(fireproof_prefix, block), RebarSerializers.BOOLEAN, false);
            for (ItemStack itemStack : event.getDrops()) {
                List<Component> lore = new ArrayList<>(itemStack.lore());
                if (soulbound) {
                    lore.add(GlobalTranslator.render(Component.translatable("pylon.message.soulbound_rune.tooltip"), player.locale()));
                    itemStack.editPersistentDataContainer(pdc -> 
                    pdc.set(pylonKey("soulbound"), RebarSerializers.UUID, player.getUniqueId()));
                    block.getChunk().getPersistentDataContainer().remove(newKey(soulbound_prefix, block));
                }
                if (fireproof) {
                    lore.add(GlobalTranslator.render(Component.translatable("pylon.message.fireproof_result.tooltip"), player.locale()));
                    itemStack.setData(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(FireproofRune.IS_FIRE_TAG));
                    block.getChunk().getPersistentDataContainer().remove(newKey(fireproof_prefix, block));
                }
                itemStack.lore(lore);
            }
        }
        private NamespacedKey newKey(String type, Block block) {
            return pylonKey(String.format("%s_%d_%d_%d",
                type, block.getLocation().getBlockX(), block.getLocation().getBlockY(), block.getLocation().getBlockZ()));
        }
    }
}
