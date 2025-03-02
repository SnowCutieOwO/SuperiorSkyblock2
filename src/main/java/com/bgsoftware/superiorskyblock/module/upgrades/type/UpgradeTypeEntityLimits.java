package com.bgsoftware.superiorskyblock.module.upgrades.type;

import com.bgsoftware.common.reflection.ReflectMethod;
import com.bgsoftware.superiorskyblock.SuperiorSkyblockPlugin;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.commands.ISuperiorCommand;
import com.bgsoftware.superiorskyblock.core.LocationKey;
import com.bgsoftware.superiorskyblock.core.Materials;
import com.bgsoftware.superiorskyblock.core.PlayerHand;
import com.bgsoftware.superiorskyblock.core.ServerVersion;
import com.bgsoftware.superiorskyblock.core.collections.AutoRemovalMap;
import com.bgsoftware.superiorskyblock.core.formatting.Formatters;
import com.bgsoftware.superiorskyblock.core.key.Keys;
import com.bgsoftware.superiorskyblock.core.messages.Message;
import com.bgsoftware.superiorskyblock.world.BukkitEntities;
import com.bgsoftware.superiorskyblock.world.BukkitItems;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class UpgradeTypeEntityLimits implements IUpgradeType {

    private static final ReflectMethod<EquipmentSlot> INTERACT_GET_HAND = new ReflectMethod<>(
            PlayerInteractEvent.class, "getHand");

    private final Map<EntityType, Player> entityBreederPlayers = AutoRemovalMap.newHashMap(2, TimeUnit.SECONDS);
    private final Map<LocationKey, Player> vehiclesOwners = AutoRemovalMap.newHashMap(2, TimeUnit.SECONDS);
    private final Map<EntityType, Player> spawnEggPlayers = AutoRemovalMap.newHashMap(2, TimeUnit.SECONDS);

    private final SuperiorSkyblockPlugin plugin;

    public UpgradeTypeEntityLimits(SuperiorSkyblockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<Listener> getListeners() {
        List<Listener> listeners = new LinkedList<>();

        listeners.add(new EntityLimitsListener());

        checkEntityBreedListener().ifPresent(listeners::add);

        return listeners;
    }

    @Override
    public List<ISuperiorCommand> getCommands() {
        return Collections.emptyList();
    }

    private Optional<Listener> checkEntityBreedListener() {
        try {
            Class.forName("org.bukkit.event.entity.EntityBreedEvent");
            return Optional.of(new EntityLimitsBreedListener());
        } catch (ClassNotFoundException error) {
            return Optional.empty();
        }
    }

    private class EntityLimitsListener implements Listener {


        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onEntitySpawn(CreatureSpawnEvent e) {
            Entity entity = e.getEntity();
            EntityType entityType = entity.getType();

            if (BukkitEntities.canBypassEntityLimit(entity) || !BukkitEntities.canHaveLimit(entityType))
                return;

            Island island = plugin.getGrid().getIslandAt(e.getLocation());

            if (island == null)
                return;

            Player spawningPlayer;

            switch (e.getSpawnReason()) {
                case SPAWNER_EGG:
                    spawningPlayer = spawnEggPlayers.remove(entityType);
                    break;
                case BREEDING:
                    spawningPlayer = entityBreederPlayers.remove(entityType);
                    break;
                default:
                    spawningPlayer = null;
                    break;
            }

            boolean hasReachedLimit = island.hasReachedEntityLimit(Keys.of(entity)).join();

            if (hasReachedLimit) {
                e.setCancelled(true);
                if (spawningPlayer != null && spawningPlayer.isOnline()) {
                    Message.REACHED_ENTITY_LIMIT.send(spawningPlayer, Formatters.CAPITALIZED_FORMATTER.format(entityType.toString()));
                }
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onHangingPlace(HangingPlaceEvent e) {
            Entity entity = e.getEntity();
            EntityType entityType = entity.getType();

            if (BukkitEntities.canBypassEntityLimit(entity) || !BukkitEntities.canHaveLimit(entityType))
                return;

            Island island = plugin.getGrid().getIslandAt(entity.getLocation());

            if (island == null)
                return;

            boolean hasReachedLimit = island.hasReachedEntityLimit(Keys.of(entity)).join();

            if (hasReachedLimit) {
                e.setCancelled(true);
                Message.REACHED_ENTITY_LIMIT.send(e.getPlayer(), Formatters.CAPITALIZED_FORMATTER.format(entityType.toString()));
            }
        }

        @EventHandler
        public void onVehicleSpawn(PlayerInteractEvent e) {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getItem() == null ||
                    e.getPlayer().getGameMode() == GameMode.CREATIVE)
                return;

            if (INTERACT_GET_HAND.isValid() && INTERACT_GET_HAND.invoke(e) != EquipmentSlot.HAND)
                return;

            Material handType = e.getItem().getType();

            // Check if minecart or boat
            boolean isMinecart = Materials.isRail(e.getClickedBlock().getType()) && Materials.isMinecart(handType);
            boolean isBoat = Materials.isBoat(handType);
            if (!isMinecart && !isBoat)
                return;

            Location blockLocation = e.getClickedBlock().getLocation();
            Island island = plugin.getGrid().getIslandAt(blockLocation);

            if (island == null)
                return;

            vehiclesOwners.put(new LocationKey(blockLocation), e.getPlayer());
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onVehicleSpawn(VehicleCreateEvent e) {
            Entity entity = e.getVehicle();
            EntityType entityType = entity.getType();

            if (BukkitEntities.canBypassEntityLimit(entity) || !BukkitEntities.canHaveLimit(entityType))
                return;

            Location entityLocation = entity.getLocation();

            Island island = plugin.getGrid().getIslandAt(entityLocation);

            if (island == null)
                return;

            Player vehicleOwner = vehiclesOwners.remove(new LocationKey(entityLocation));

            boolean hasReachedLimit = island.hasReachedEntityLimit(Keys.of(entity)).join();

            if (hasReachedLimit) {
                entity.remove();
                if (vehicleOwner != null && vehicleOwner.isOnline()) {
                    Message.REACHED_ENTITY_LIMIT.send(vehicleOwner, Formatters.CAPITALIZED_FORMATTER.format(entityType.toString()));
                    BukkitItems.addItem(asItemStack(e.getVehicle()), vehicleOwner.getInventory(), vehicleOwner.getLocation());
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onSpawnEggUse(PlayerInteractEvent e) {
            if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getItem() == null)
                return;

            PlayerHand usedHand = BukkitItems.getHand(e);
            ItemStack usedItem = BukkitItems.getHandItem(e.getPlayer(), usedHand);
            EntityType spawnEggEntityType = usedItem == null ? EntityType.UNKNOWN : BukkitItems.getEntityType(usedItem);

            if (spawnEggEntityType == EntityType.UNKNOWN || !BukkitEntities.canHaveLimit(spawnEggEntityType))
                return;

            Island island = plugin.getGrid().getIslandAt(e.getClickedBlock().getLocation());

            if (island == null)
                return;

            spawnEggPlayers.put(spawnEggEntityType, e.getPlayer());
        }

        private ItemStack asItemStack(Entity entity) {
            if (entity instanceof Hanging) {
                switch (entity.getType()) {
                    case ITEM_FRAME:
                        return new ItemStack(Material.ITEM_FRAME);
                    case PAINTING:
                        return new ItemStack(Material.PAINTING);
                }
            } else if (entity instanceof Minecart) {
                Material material = Material.valueOf(plugin.getNMSAlgorithms().getMinecartBlock((Minecart) entity).getGlobalKey());
                switch (material.name()) {
                    case "HOPPER":
                        return new ItemStack(Material.HOPPER_MINECART);
                    case "COMMAND_BLOCK":
                        return new ItemStack(Material.valueOf("COMMAND_BLOCK_MINECART"));
                    case "COMMAND":
                        return new ItemStack(Material.COMMAND_MINECART);
                    case "TNT":
                        return new ItemStack(ServerVersion.isLegacy() ? Material.EXPLOSIVE_MINECART : Material.valueOf("TNT_MINECART"));
                    case "FURNACE":
                        return new ItemStack(ServerVersion.isLegacy() ? Material.POWERED_MINECART : Material.valueOf("FURNACE_MINECART"));
                    case "CHEST":
                        return new ItemStack(ServerVersion.isLegacy() ? Material.STORAGE_MINECART : Material.valueOf("CHEST_MINECART"));
                    default:
                        return new ItemStack(Material.MINECART);
                }
            }

            throw new IllegalArgumentException("Cannot find an item for " + entity.getType());
        }

    }

    private class EntityLimitsBreedListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onEntityBreed(EntityBreedEvent e) {
            Entity child = e.getEntity();
            EntityType childEntityType = child.getType();

            if (!(e.getBreeder() instanceof Player) || !BukkitEntities.canHaveLimit(childEntityType))
                return;

            Island island = plugin.getGrid().getIslandAt(child.getLocation());

            if (island == null)
                return;

            entityBreederPlayers.put(childEntityType, (Player) e.getBreeder());
        }

    }

}
