package io.github.pylonmc.pylon.content.tools;

import com.destroystokyo.paper.ParticleBuilder;
import io.github.pylonmc.pylon.content.tools.base.Rune;
import io.github.pylonmc.rebar.block.RebarBlock;
import io.github.pylonmc.rebar.block.context.BlockBreakContext.PlayerBreak;
import io.github.pylonmc.rebar.config.adapter.ConfigAdapter;
import io.github.pylonmc.rebar.datatypes.RebarSerializers;
import io.github.pylonmc.rebar.event.RebarBlockBreakEvent;
import io.github.pylonmc.rebar.event.RebarBlockDeserializeEvent;
import io.github.pylonmc.rebar.event.RebarBlockPlaceEvent;
import io.github.pylonmc.rebar.event.RebarBlockSerializeEvent;
import io.github.pylonmc.rebar.item.builder.ItemStackBuilder;
import io.github.pylonmc.rebar.util.RandomizedSound;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.DamageResistant;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.keys.tags.DamageTypeTagKeys;
import io.papermc.paper.registry.tag.Tag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;

import static io.github.pylonmc.pylon.util.PylonUtils.pylonKey;

import java.util.ArrayList;
import java.util.List;

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
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

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
     * Checks if the target is already fireproof.
     *
     * @param target The item to handle, amount may be > 1
     * @return true if is fireproof, false otherwise
     */
    public static boolean isFireproof(@NotNull ItemStack target) {
        return target.getPersistentDataContainer()
            .getOrDefault(FIREPROOF_KEY, RebarSerializers.BOOLEAN, false);
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
        // DamageResistant data = target.getData(DataComponentTypes.DAMAGE_RESISTANT);
        // if (data == null) return true;
        // return !data.types().equals(IS_FIRE_TAG);
        return !isFireproof(target);
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
        ItemStack handle = ItemStackBuilder.of(target.asQuantity(consume)) // Already cloned in `asQuantity`
                .set(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(IS_FIRE_TAG))
                .editPdc(pdc -> pdc.set(FIREPROOF_KEY, RebarSerializers.BOOLEAN, true))
                .lore(GlobalTranslator.render(TOOLTIP, player.locale()))
                .build();

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

    public static class FireproofRuneListener implements Listener {
        private List<RebarBlock> fireproof_blocks = new ArrayList<>();

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockPlace(RebarBlockPlaceEvent event) {
            ItemStack itemStack = event.getContext().getItem().asOne();
            RebarBlock block = event.getRebarBlock();
            if (itemStack.getPersistentDataContainer().has(FIREPROOF_KEY)) {
                fireproof_blocks.add(block);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBlockBreak(@NotNull RebarBlockBreakEvent event) {
            RebarBlock block = event.getRebarBlock();
            if (!(event.getContext() instanceof PlayerBreak playerBreak)) return;
            Player player = playerBreak.event().getPlayer();
            for (ItemStack itemStack : event.getDrops()) {
                List<Component> lore = new ArrayList<>(itemStack.lore());
                if (fireproof_blocks.contains(block)) {
                    fireproof_blocks.remove(block);
                    lore.add(GlobalTranslator.render(Component.translatable("pylon.message.fireproof_result.tooltip"), player.locale()));
                    itemStack.setData(DataComponentTypes.DAMAGE_RESISTANT, DamageResistant.damageResistant(FireproofRune.IS_FIRE_TAG));
                    itemStack.editPersistentDataContainer(pdc -> pdc.set(FIREPROOF_KEY, RebarSerializers.BOOLEAN, true));
                }
                itemStack.lore(lore);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSerialize(@NotNull RebarBlockSerializeEvent event) {
            RebarBlock block = event.getRebarBlock();
            PersistentDataContainer pdc = event.getPdc();
            if (fireproof_blocks.contains(block)) {
                pdc.set(FIREPROOF_KEY, RebarSerializers.BOOLEAN, true);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDeserialize(@NotNull RebarBlockDeserializeEvent event) {
            RebarBlock block = event.getRebarBlock();
            PersistentDataContainer pdc = event.getPdc();
            if (pdc.has(FIREPROOF_KEY) && !fireproof_blocks.contains(block)) fireproof_blocks.add(block);
        }
    }
}
