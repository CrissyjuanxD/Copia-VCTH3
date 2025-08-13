package Events.MissionSystem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import net.md_5.bungee.api.ChatColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MissionGUI implements Listener {
    private final JavaPlugin plugin;
    private final MissionHandler missionHandler;
    private final ItemStack grayPane = createGrayPane();

    public MissionGUI(JavaPlugin plugin, MissionHandler missionHandler) {
        this.plugin = plugin;
        this.missionHandler = missionHandler;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private ItemStack createGrayPane() {
        ItemStack pane = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.setCustomModelData(2);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    public void openMissionGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.GOLD + "" + ChatColor.BOLD + "Sistema de Misiones");

        // Llenar slots 0-26 con paneles grises
        for (int i = 0; i <= 26; i++) {
            gui.setItem(i, grayPane);
        }

        // Obtener datos del jugador
        FileConfiguration data = YamlConfiguration.loadConfiguration(missionHandler.getMissionFile());
        String playerName = player.getName();

        // Colocar misiones en slots 27-53
        Map<Integer, Mission> allMissions = missionHandler.getMissions();
        for (int missionNum = 1; missionNum <= 27; missionNum++) {
            int slot = 26 + missionNum; // Slot 27 para misión 1, etc.
            
            if (slot > 53) break; // No exceder el tamaño del inventario
            
            if (allMissions.containsKey(missionNum)) {
                Mission mission = allMissions.get(missionNum);
                boolean isActive = missionHandler.isMissionActive(missionNum);
                boolean isCompleted = data.getBoolean("players." + playerName + ".missions." + missionNum + ".completed", false);
                
                gui.setItem(slot, createMissionItem(mission, isActive, isCompleted, playerName));
            } else {
                // Misión no implementada aún
                gui.setItem(slot, createUnknownMissionItem(missionNum));
            }
        }

        player.openInventory(gui);
    }

    private ItemStack createMissionItem(Mission mission, boolean isActive, boolean isCompleted, String playerName) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        String displayName;
        ChatColor nameColor;

        if (!isActive) {
            displayName = ChatColor.GRAY + "???";
            nameColor = ChatColor.GRAY;
        } else if (isCompleted) {
            displayName = ChatColor.GREEN + mission.getName();
            nameColor = ChatColor.GREEN;
        } else {
            displayName = ChatColor.RED + mission.getName();
            nameColor = ChatColor.RED;
        }

        meta.setDisplayName(displayName);
        meta.setCustomModelData(2000 + mission.getMissionNumber() - 1);

        List<String> lore = new ArrayList<>();
        
        if (isActive) {
            lore.add(ChatColor.GRAY + mission.getDescription());
            lore.add("");
            lore.add(isCompleted ? ChatColor.GREEN + "✔ Completada" : ChatColor.RED + "✖ Pendiente");

            // Agregar progreso específico para misiones con listas
            addMissionSpecificProgress(mission, playerName, lore);
        } else {
            lore.add(ChatColor.GRAY + "Misión no descubierta");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUnknownMissionItem(int missionNumber) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.GRAY + "???");
        meta.setCustomModelData(2000 + missionNumber - 1);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Misión no implementada");
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private void addMissionSpecificProgress(Mission mission, String playerName, List<String> lore) {
        FileConfiguration data = YamlConfiguration.loadConfiguration(missionHandler.getMissionFile());

        if (mission instanceof Mission1) {
            lore.add("");
            lore.add(ChatColor.YELLOW + "Progreso de armadura:");
            
            String[] armorPieces = {"helmet", "chestplate", "leggings", "boots"};
            String[] armorNames = {"Casco", "Peto", "Pantalones", "Botas"};
            
            for (int i = 0; i < armorPieces.length; i++) {
                boolean hasArmor = data.getBoolean(
                        "players." + playerName + ".missions.1.armor." + armorPieces[i], false);
                lore.add((hasArmor ? ChatColor.GREEN : ChatColor.GRAY) + "- " + armorNames[i] + " de Diamante");
            }
        } else if (mission instanceof Mission2) {
            lore.add("");
            lore.add(ChatColor.YELLOW + "Progreso de encantamientos:");
            
            String[] armorPieces = {"helmet", "chestplate", "leggings", "boots"};
            String[] armorNames = {"Casco", "Peto", "Pantalones", "Botas"};
            
            for (int i = 0; i < armorPieces.length; i++) {
                boolean hasEnchant = data.getBoolean(
                        "players." + playerName + ".missions.2.protection." + armorPieces[i], false);
                lore.add((hasEnchant ? ChatColor.GREEN : ChatColor.GRAY) + "- " + armorNames[i] + " con Protección IV");
            }
        } else if (mission instanceof Mission4) {
            lore.add("");
            lore.add(ChatColor.YELLOW + "Progreso de armadura:");
            
            String[] armorPieces = {"helmet", "chestplate", "leggings", "boots"};
            String[] armorNames = {"Casco", "Peto", "Pantalones", "Botas"};
            
            for (int i = 0; i < armorPieces.length; i++) {
                boolean hasArmor = data.getBoolean(
                        "players." + playerName + ".missions.4.netherite_armor." + armorPieces[i], false);
                lore.add((hasArmor ? ChatColor.GREEN : ChatColor.GRAY) + "- " + armorNames[i] + " de Netherite");
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.GOLD + "" + ChatColor.BOLD + "Sistema de Misiones")) {
            event.setCancelled(true); // Cancelar cualquier interacción
        }
    }
}