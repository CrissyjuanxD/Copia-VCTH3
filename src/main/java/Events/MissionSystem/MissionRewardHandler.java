package Events.MissionSystem;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.ChatColor;

import java.io.IOException;
import java.util.List;

public class MissionRewardHandler implements Listener {
    private final JavaPlugin plugin;
    private final MissionHandler missionHandler;

    public MissionRewardHandler(JavaPlugin plugin, MissionHandler missionHandler) {
        this.plugin = plugin;
        this.missionHandler = missionHandler;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.PINK_GLAZED_TERRACOTTA) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (!isMissionToken(item)) return;

        int missionNumber = getMissionNumberFromToken(item);
        if (missionNumber == -1) return;

        // Verificar que la misión esté completada y no haya reclamado la recompensa
        FileConfiguration data = YamlConfiguration.loadConfiguration(missionHandler.getMissionFile());
        String playerName = player.getName();
        
        if (!data.getBoolean("players." + playerName + ".missions." + missionNumber + ".completed", false)) {
            player.sendMessage(ChatColor.RED + "No has completado esta misión.");
            return;
        }

        if (data.getBoolean("players." + playerName + ".missions." + missionNumber + ".reward_claimed", false)) {
            player.sendMessage(ChatColor.RED + "Ya has reclamado la recompensa de esta misión.");
            return;
        }

        event.setCancelled(true);
        
        // Consumir la ficha
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // Iniciar animación de recompensa
        startRewardAnimation(player, block.getLocation(), missionNumber);
        
        // Marcar recompensa como reclamada
        data.set("players." + playerName + ".missions." + missionNumber + ".reward_claimed", true);
        try {
            data.save(missionHandler.getMissionFile());
        } catch (IOException e) {
            plugin.getLogger().severe("Error al guardar reclamación de recompensa: " + e.getMessage());
        }
    }

    private boolean isMissionToken(ItemStack item) {
        if (item == null || item.getType() != Material.ECHO_SHARD) return false;
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return false;
        
        int cmd = meta.getCustomModelData();
        return cmd >= 3001 && cmd <= 3027; // Fichas de misión 1-27
    }

    private int getMissionNumberFromToken(ItemStack item) {
        if (!isMissionToken(item)) return -1;
        
        ItemMeta meta = item.getItemMeta();
        return meta.getCustomModelData() - 3000; // 3001 -> 1, 3002 -> 2, etc.
    }

    private void startRewardAnimation(Player player, Location blockLocation, int missionNumber) {
        Location center = blockLocation.clone().add(0.5, 0.5, 0.5);
        World world = center.getWorld();

        // Fase 1: Círculo de Sonic Boom (radio 5)
        new BukkitRunnable() {
            @Override
            public void run() {
                for (int i = 0; i < 360; i += 10) {
                    double angle = Math.toRadians(i);
                    double x = 5 * Math.cos(angle);
                    double z = 5 * Math.sin(angle);
                    Location particleLoc = center.clone().add(x, 0, z);
                    world.spawnParticle(Particle.SONIC_BOOM, particleLoc, 1);
                }
                world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
            }
        }.runTaskLater(plugin, 10L);

        // Fase 2: Círculo de Poof giratorio con cambios de color
        startRotatingPoofAnimation(center, missionNumber);
    }

    private void startRotatingPoofAnimation(Location center, int missionNumber) {
        World world = center.getWorld();
        
        // Colores que cambian cada 2 segundos
        Particle.DustOptions[] colors = {
                new Particle.DustOptions(Color.ORANGE, 1.0f),
                new Particle.DustOptions(Color.GREEN, 1.0f),
                new Particle.DustOptions(Color.PURPLE, 1.0f),
                new Particle.DustOptions(Color.YELLOW, 1.0f),
                new Particle.DustOptions(Color.RED, 1.0f)
        };

        new BukkitRunnable() {
            int ticks = 0;
            double rotation = 0;

            @Override
            public void run() {
                if (ticks >= 200) { // 10 segundos
                    // Fase 3: Transformar a explosión y crear rayo
                    createFinalExplosionAndBeam(center, missionNumber);
                    this.cancel();
                    return;
                }

                // Cambiar color cada 40 ticks (2 segundos)
                int colorIndex = (ticks / 40) % colors.length;
                Particle.DustOptions currentColor = colors[colorIndex];

                // Crear círculo giratorio
                for (int i = 0; i < 360; i += 15) {
                    double angle = Math.toRadians(i + rotation);
                    double x = 5 * Math.cos(angle);
                    double z = 5 * Math.sin(angle);
                    Location particleLoc = center.clone().add(x, 0, z);
                    world.spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, 0, currentColor);
                }

                rotation += 5; // Velocidad de rotación
                ticks++;
            }
        }.runTaskTimer(plugin, 40L, 1L); // Empezar después de sonic boom
    }

    private void createFinalExplosionAndBeam(Location center, int missionNumber) {
        World world = center.getWorld();

        // Explosión en círculo
        for (int i = 0; i < 360; i += 10) {
            double angle = Math.toRadians(i);
            double x = 5 * Math.cos(angle);
            double z = 5 * Math.sin(angle);
            Location particleLoc = center.clone().add(x, 0, z);
            world.spawnParticle(Particle.EXPLOSION, particleLoc, 1);
        }

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);

        // Rayo de partículas negras hacia arriba
        new BukkitRunnable() {
            int height = 0;

            @Override
            public void run() {
                if (height >= 7) {
                    // Dar recompensa
                    giveRewardChest(center, missionNumber);
                    this.cancel();
                    return;
                }

                Location beamLoc = center.clone().add(0, height, 0);
                Particle.DustOptions blackDust = new Particle.DustOptions(Color.BLACK, 2.0f);
                world.spawnParticle(Particle.DUST, beamLoc, 10, 0.2, 0.2, 0.2, 0, blackDust);
                
                height++;
            }
        }.runTaskTimer(plugin, 20L, 5L); // Cada 5 ticks
    }

    private void giveRewardChest(Location location, int missionNumber) {
        World world = location.getWorld();
        
        // Crear cofre con recompensas
        Inventory rewardChest = Bukkit.createInventory(null, 27, 
                ChatColor.GOLD + "Recompensa de Misión #" + missionNumber);

        Mission mission = missionHandler.getMissions().get(missionNumber);
        if (mission != null) {
            List<ItemStack> rewards = mission.getRewards();
            for (int i = 0; i < rewards.size() && i < 27; i++) {
                rewardChest.setItem(i, rewards.get(i));
            }
        }

        // Encontrar jugador más cercano
        Player nearestPlayer = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (Player player : world.getPlayers()) {
            double distance = player.getLocation().distance(location);
            if (distance < nearestDistance && distance <= 10) {
                nearestDistance = distance;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null) {
            nearestPlayer.openInventory(rewardChest);
            nearestPlayer.playSound(location, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.0f);
            nearestPlayer.sendMessage(ChatColor.GREEN + "¡Has reclamado la recompensa de la Misión #" + missionNumber + "!");
        }

        // Efectos finales
        world.spawnParticle(Particle.FIREWORK, location, 50, 1, 1, 1, 0.1);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 2.0f, 1.0f);
    }
}