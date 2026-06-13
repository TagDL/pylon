package io.github.pylonmc.pylon.content.tools;

import io.github.pylonmc.pylon.content.tools.base.Rune;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent;
import io.github.pylonmc.rebar.event.RebarBlockDeserializeEvent;
import io.github.pylonmc.rebar.event.RebarBlockPlaceEvent;
import io.github.pylonmc.rebar.event.RebarBlockSerializeEvent;
import io.github.pylonmc.rebar.event.RebarBlockUnloadEvent;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.RebarItemSchema;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.github.pylonmc.pylon.util.PylonUtils.pylonKey;

public class SoulboundRune extends Rune {
    private static final TranslatableComponent SOULBIND_MSG = Component.translatable("pylon.message.soulbound_rune.soulbind-message");
    private static final TranslatableComponent TOOLTIP = Component.translatable("pylon.message.soulbound_rune.tooltip");
    private static final NamespacedKey SOULBOUND_KEY = pylonKey("soulbound");

    public SoulboundRune(ItemStack stack) {
        super(stack);
    }

    @Override
    public boolean isApplicableToTarget(@NotNull PlayerDropItemEvent event, @NotNull ItemStack rune, @NotNull ItemStack target) {
        return !RebarItem.isRebarItem(target, SoulboundRune.class) && !target.getPersistentDataContainer().has(SOULBOUND_KEY);
    }

    @Override
    public void onContactItem(@NotNull PlayerDropItemEvent event, @NotNull ItemStack rune, @NotNull ItemStack target) {
        int consume = Math.min(rune.getAmount(), target.getAmount());

        ItemStack soulboundItem = ItemStackBuilder.of(target.asQuantity(consume))
                .lore(TOOLTIP.decoration(TextDecoration.ITALIC, true))
                .build();
        soulboundItem.editPersistentDataContainer(pdc -> {
            pdc.set(SOULBOUND_KEY, RebarSerializers.UUID, event.getPlayer().getUniqueId());
        });

        // (N)Either left runes or targets
        int leftRunes = rune.getAmount() - consume;
        int leftTargets = target.getAmount() - consume;

        Location dropLocation = event.getItemDrop().getLocation();
        World world = dropLocation.getWorld();
        if (leftRunes > 0) {
            world.dropItemNaturally(dropLocation, rune.asQuantity(leftRunes)).setGlowing(true);
        }
        if (leftTargets > 0) {
            world.dropItemNaturally(dropLocation, target.asQuantity(leftTargets)).setGlowing(true);
        }
        world.dropItemNaturally(dropLocation, soulboundItem).setGlowing(true);

        target.setAmount(0);
        rune.setAmount(0);
        event.getPlayer().sendMessage(SOULBIND_MSG);
    }

    public static class SoulboundRuneListener implements Listener {
        private final Map<RebarBlock, UUID> soulboundBlocks = new HashMap<>(); //use map to store uuid

        @EventHandler
        public void onPlayerDeath(PlayerDeathEvent event) { // exception being generated
            Iterator<ItemStack> curItem = event.getDrops().iterator();
            while (curItem.hasNext()) {
                ItemStack curStack = curItem.next();
                if (curStack == null || !curStack.hasItemMeta()) continue;
                if (curStack.getItemMeta().getPersistentDataContainer().has(SOULBOUND_KEY)) {
                    event.getItemsToKeep().add(curStack);
                    curItem.remove();
                }
            }
        }
        
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockPlace(RebarBlockPlaceEvent event) {
            ItemStack itemStack = event.getContext().getItem();
            if (itemStack == null || itemStack.isEmpty()) return; //for safety
            RebarBlock block = event.getRebarBlock();
            PersistentDataContainerView pdcView = itemStack.getPersistentDataContainer();
            if (pdcView.has(SOULBOUND_KEY)) {
                soulboundBlocks.put(block, pdcView.get(SOULBOUND_KEY, RebarSerializers.UUID));
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockBreak(@NotNull RebarBlockBreakEvent event) {
            RebarBlock block = event.getRebarBlock();
            if (soulboundBlocks.containsKey(block)) {
                UUID uuid = soulboundBlocks.remove(block);
                for (ItemStack itemStack : event.getDrops()) {
                    RebarItemSchema dropSchema = RebarItemSchema.fromStack(itemStack);  
                    if (dropSchema == null) continue; //use schema to make sure as same as itself

                    NamespacedKey blockItemKey = block.getSchema().getKey();
                    if (blockItemKey == null || !dropSchema.getKey().equals(blockItemKey)) continue;

                    List<Component> lore = new ArrayList<>(itemStack.lore());
                    lore.add(Component.translatable("pylon.message.soulbound_rune.tooltip")
                        .decoration(TextDecoration.ITALIC, true)); //make sure lore is italic
                    itemStack.lore(lore);

                    itemStack.editPersistentDataContainer(pdc -> 
                        pdc.set(pylonKey("soulbound"), RebarSerializers.UUID, uuid));
                    break;
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSerialize(@NotNull RebarBlockSerializeEvent event) {
            RebarBlock block = event.getRebarBlock();
            PersistentDataContainer pdc = event.getPdc();
            if (soulboundBlocks.containsKey(block)) {
                UUID uuid = soulboundBlocks.get(block);
                pdc.set(SOULBOUND_KEY, RebarSerializers.UUID, uuid);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeserialize(@NotNull RebarBlockDeserializeEvent event) {
            RebarBlock block = event.getRebarBlock();
            PersistentDataContainer pdc = event.getPdc();
            if (pdc.has(SOULBOUND_KEY) && !soulboundBlocks.containsKey(block)) {
                soulboundBlocks.put(block, pdc.get(SOULBOUND_KEY, RebarSerializers.UUID));
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockUnload(@NotNull RebarBlockUnloadEvent event) { //add unload event
            soulboundBlocks.remove(event.getRebarBlock());
        }
    }
}
