package io.github.pylonmc.pylon.content.tools;

import io.github.pylonmc.pylon.content.tools.base.Rune;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext.PlayerBreak;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent;
import io.github.pylonmc.rebar.event.RebarBlockDeserializeEvent;
import io.github.pylonmc.rebar.event.RebarBlockPlaceEvent;
import io.github.pylonmc.rebar.event.RebarBlockSerializeEvent;
import io.github.pylonmc.rebar.item.RebarItem;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
                .lore(GlobalTranslator.render(TOOLTIP, event.getPlayer().locale()))
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

        private List<RebarBlock> soulbound_blocks = new ArrayList<>();
        
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockPlace(RebarBlockPlaceEvent event) {
            ItemStack itemStack = event.getContext().getItem().asOne();
            RebarBlock block = event.getRebarBlock();

            if (itemStack.getPersistentDataContainer().has(SOULBOUND_KEY)) {
                soulbound_blocks.add(block);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockBreak(@NotNull RebarBlockBreakEvent event) {
            RebarBlock block = event.getRebarBlock();
            if (!(event.getContext() instanceof PlayerBreak playerBreak)) return;
            Player player = playerBreak.event().getPlayer();
            if (soulbound_blocks.contains(block)) {
                soulbound_blocks.remove(block);
                for (ItemStack itemStack : event.getDrops()) {
                    List<Component> lore = new ArrayList<>(itemStack.lore());
                    
                    lore.add(GlobalTranslator.render(Component.translatable("pylon.message.soulbound_rune.tooltip"), player.locale()));
                    itemStack.editPersistentDataContainer(pdc -> 
                        pdc.set(pylonKey("soulbound"), RebarSerializers.UUID, player.getUniqueId()));
                    
                    itemStack.lore(lore);
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSerialize(@NotNull RebarBlockSerializeEvent event) {
            RebarBlock block = event.getRebarBlock();
            PersistentDataContainer pdc = event.getPdc();
            if (soulbound_blocks.contains(block)) pdc.set(SOULBOUND_KEY, RebarSerializers.BOOLEAN, true);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeserialize(@NotNull RebarBlockDeserializeEvent event) {
            RebarBlock block = event.getRebarBlock();
            PersistentDataContainer pdc = event.getPdc();
            if (pdc.has(SOULBOUND_KEY) && !soulbound_blocks.contains(block)) soulbound_blocks.add(block);
        }
    }
}
