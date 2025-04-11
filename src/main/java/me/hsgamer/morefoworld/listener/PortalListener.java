package me.hsgamer.morefoworld.listener;

import io.github.projectunified.minelib.plugin.base.BasePlugin;
import io.github.projectunified.minelib.plugin.listener.ListenerComponent;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import me.hsgamer.morefoworld.DebugComponent;
import me.hsgamer.morefoworld.config.PortalConfig;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PortalListener implements ListenerComponent {
    private final ConcurrentHashMap<UUID, Material> portalTeleportCache = new ConcurrentHashMap<>();
    private final BasePlugin plugin;
    private DebugComponent debug;

    public PortalListener(BasePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public BasePlugin getPlugin() {
        return plugin;
    }

    @Override
    public void load() {
        debug = plugin.get(DebugComponent.class);
    }

    @EventHandler
public void onPortalReady(EntityPortalReadyEvent event) {
    // ネザーポータルの場合はイベントをキャンセルして無効化
    if (event.getPortalType() == PortalType.NETHER) {
        debug.debug("Nether portal usage is disabled.");
        event.setCancelled(true);
    }
}

    private CompletableFuture<Void> constructEndPlatform(Location location) {
        return CompletableFuture.runAsync(() -> {
            Block block = location.getBlock();
            for (int x = block.getX() - 2; x <= block.getX() + 2; x++) {
                for (int z = block.getZ() - 2; z <= block.getZ() + 2; z++) {
                    Block platformBlock = block.getWorld().getBlockAt(x, block.getY() - 1, z);
                    if (platformBlock.getType() != Material.OBSIDIAN) {
                        platformBlock.setType(Material.OBSIDIAN);
                    }
                    for (int yMod = 1; yMod <= 3; yMod++) {
                        Block b = platformBlock.getRelative(BlockFace.UP, yMod);
                        if (b.getType() != Material.AIR) {
                            b.setType(Material.AIR);
                        }
                    }
                }
            }
        }, runnable -> Bukkit.getRegionScheduler().execute(plugin, location, runnable));
    }

    private CompletableFuture<Void> constructNetherPortal(Location location) {
        return CompletableFuture.runAsync(() -> {
            // TODO: Construct the nether portal
        }, runnable -> Bukkit.getRegionScheduler().execute(plugin, location, runnable));
    }

    private CompletableFuture<Boolean> teleport(Entity entity, Location location, boolean runInScheduler) {
        if (runInScheduler) {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            entity.getScheduler().execute(plugin, () -> entity.teleportAsync(location).thenAccept(future::complete), null, 1L);
            return future;
        } else {
            return entity.teleportAsync(location);
        }
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.END_PORTAL) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        debug.debug("Portal Cause: " + event.getCause());
        debug.debug("From: " + from);
        debug.debug("To: " + to);
        Optional<World> worldOptional = plugin.get(PortalConfig.class).getWorldFromEndPortal(from.getWorld());

        worldOptional.ifPresent(world -> {
            Location clone = to.clone();
            clone.setWorld(world);
        });
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getPortalType() != PortalType.ENDER) return;

        Location from = event.getFrom();
        Location to = event.getTo();
        debug.debug("Entity Portal: " + event.getPortalType());
        debug.debug("From: " + from);
        debug.debug("To: " + to);
        if (to == null) {
            return;
        }

        Optional<World> worldOptional = plugin.get(PortalConfig.class).getWorldFromEndPortal(from.getWorld());

        worldOptional.ifPresent(world -> {
            Location clone = to.clone();
            clone.setWorld(world);

            event.setCancelled(true);
            CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
            if (world.getEnvironment() == World.Environment.THE_END) {
                future = constructEndPlatform(clone);
            }
            future
                    .thenCompose(aVoid -> teleport(event.getEntity(), clone, true))
                    .thenRun(() -> debug.debug("Teleported to " + clone));
        });
    }

    @EventHandler(ignoreCancelled = true)
public void onEntityInsidePortal(final EntityInsideBlockEvent event) {
    Block block = event.getBlock();
    Material blockTypeInside = block.getType();

    // ネザーポータルの場合はイベントをキャンセルして無効化
    if (blockTypeInside == Material.NETHER_PORTAL) {
        debug.debug("Nether portal deactivation: Entity blocked from teleporting.");
        event.setCancelled(true);
    }
}
}
