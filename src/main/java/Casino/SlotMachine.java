package Casino;

import items.EconomyItems;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SlotMachine implements Listener {
    private final JavaPlugin plugin;
    private final String title = ChatColor.of("#FF6B35") + "" + ChatColor.BOLD + "Máquina Tragamonedas";
    private final int[] reelSlots = {11, 13, 15};
    private final Material[] symbols = {Material.COAL, Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD};
    private final Map<UUID, Boolean> isSpinning = new ConcurrentHashMap<>();
    private final Map<Location, UUID> machineUsers = new ConcurrentHashMap<>();
    private final File configFile;
    private FileConfiguration config;

    public SlotMachine(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "SlotMachine.yml");
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            createDefaultConfig();
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void createDefaultConfig() {
        try {
            configFile.getParentFile().mkdirs();
            configFile.createNewFile();
            
            FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(configFile);
            
            // Configuración por defecto
            defaultConfig.set("SlotMachine.minerales.two_out_of_three", Arrays.asList("coal 12", "iron_ingot 10", "gold_ingot 8"));
            defaultConfig.set("SlotMachine.minerales.three_out_of_three", Arrays.asList("diamond 5", "netherite_scrap 3", "pepita_infernal 3", "corrupted_netherite_scrap 2"));
            
            defaultConfig.set("SlotMachine.itemsvarios.two_out_of_three", Arrays.asList("bread 16", "cooked_beef 8"));
            defaultConfig.set("SlotMachine.itemsvarios.three_out_of_three", Arrays.asList("totem_of_undying 1", "enchanted_golden_apple 2"));
            
            defaultConfig.set("SlotMachine.pociones.two_out_of_three", Arrays.asList("potion 3", "splash_potion 2"));
            defaultConfig.set("SlotMachine.pociones.three_out_of_three", Arrays.asList("ultra_pocion_resistencia_fuego 1"));
            
            defaultConfig.set("SlotMachine.vithiums_fichas.two_out_of_three", Arrays.asList("vithiums_fichas 5", "vithiums_fichas 8"));
            defaultConfig.set("SlotMachine.vithiums_fichas.three_out_of_three", Arrays.asList("vithiums_fichas 15", "vithiums_fichas 20"));
            
            defaultConfig.set("SlotMachine.totems.two_out_of_three", Arrays.asList("totem_of_undying 1"));
            defaultConfig.set("SlotMachine.totems.three_out_of_three", Arrays.asList("doubletotem 1", "lifetotem 1"));
            
            defaultConfig.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error creando configuración de Slot Machine: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.ORANGE_GLAZED_TERRACOTTA) return;

        Player player = event.getPlayer();
        Location blockLoc = event.getClickedBlock().getLocation();

        // Verificar si la máquina ya está siendo usada
        if (machineUsers.containsKey(blockLoc)) {
            UUID currentUser = machineUsers.get(blockLoc);
            Player currentPlayer = Bukkit.getPlayer(currentUser);
            
            if (currentPlayer != null && currentPlayer.isOnline()) {
                player.sendMessage(ChatColor.RED + "Esta máquina está siendo usada por " + currentPlayer.getName());
                return;
            } else {
                // Limpiar usuario desconectado
                machineUsers.remove(blockLoc);
            }
        }

        // Verificar que tenga fichas
        if (!hasVithiumTokens(player)) {
            player.sendMessage(ChatColor.RED + "Necesitas Vithium Fichas para usar la máquina tragamonedas.");
            return;
        }

        // Registrar usuario en la máquina
        machineUsers.put(blockLoc, player.getUniqueId());
        
        openSlotMachine(player, blockLoc);
        event.setCancelled(true);
    }

    private boolean hasVithiumTokens(Player player) {
        ItemStack tokenItem = EconomyItems.createVithiumToken();
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.isSimilar(tokenItem)) {
                return true;
            }
        }
        return false;
    }

    private void openSlotMachine(Player player, Location machineLoc) {
        Inventory inv = Bukkit.createInventory(null, 27, title);
        setupGUI(inv);
        player.openInventory(inv);
        
        // Guardar la ubicación de la máquina en metadata del jugador
        player.setMetadata("slot_machine_location", new org.bukkit.metadata.FixedMetadataValue(plugin, machineLoc));
        
        // Sonido de apertura
        machineLoc.getWorld().playSound(machineLoc, Sound.BLOCK_CHEST_OPEN, 1.0f, 1.2f);
    }

    private void setupGUI(Inventory inv) {
        // Botón de cerrar
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Cerrar");
        closeMeta.setCustomModelData(1000);
        closeButton.setItemMeta(closeMeta);
        inv.setItem(0, closeButton);

        // Slot para fichas
        ItemStack tokenSlot = new ItemStack(Material.ECHO_SHARD);
        ItemMeta tokenMeta = tokenSlot.getItemMeta();
        tokenMeta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Colocar Ficha");
        tokenMeta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Coloca una " + ChatColor.GOLD + "Vithium Ficha",
                ChatColor.GRAY + "aquí para apostar",
                ""
        ));
        tokenMeta.setCustomModelData(1000);
        tokenSlot.setItemMeta(tokenMeta);
        inv.setItem(22, tokenSlot);

        // Botón de girar
        ItemStack spinButton = new ItemStack(Material.LEVER);
        ItemMeta spinMeta = spinButton.getItemMeta();
        spinMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "¡GIRAR!");
        spinMeta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Haz clic para girar los rodillos",
                ""
        ));
        spinMeta.setCustomModelData(1000);
        spinButton.setItemMeta(spinMeta);
        inv.setItem(26, spinButton);

        // Símbolos iniciales aleatorios
        for (int slot : reelSlots) {
            setRandomSymbol(inv, slot);
        }

        // Llenar espacios vacíos con paneles
        for (int i = 0; i < inv.getSize(); i++) {
            if (i != 0 && i != 22 && i != 26 && !isReelSlot(i) && inv.getItem(i) == null) {
                ItemStack frame = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta frameMeta = frame.getItemMeta();
                frameMeta.setDisplayName(" ");
                frameMeta.setCustomModelData(1000);
                frame.setItemMeta(frameMeta);
                inv.setItem(i, frame);
            }
        }
    }

    private boolean isReelSlot(int slot) {
        for (int reelSlot : reelSlots) {
            if (reelSlot == slot) return true;
        }
        return false;
    }

    private void setRandomSymbol(Inventory inv, int slot) {
        Material symbol = symbols[new Random().nextInt(symbols.length)];
        ItemStack item = new ItemStack(symbol);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + getSymbolName(symbol));
        meta.setCustomModelData(100);
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    private String getSymbolName(Material symbol) {
        return switch (symbol) {
            case COAL -> "Carbón";
            case IRON_INGOT -> "Hierro";
            case GOLD_INGOT -> "Oro";
            case DIAMOND -> "Diamante";
            case EMERALD -> "Esmeralda";
            default -> symbol.name();
        };
    }

    private String getCategoryFromSymbol(Material symbol) {
        return switch (symbol) {
            case COAL -> "itemsvarios";
            case IRON_INGOT -> "pociones";
            case GOLD_INGOT -> "vithiums_fichas";
            case DIAMOND -> "minerales";
            case EMERALD -> "totems";
            default -> "itemsvarios";
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(title)) return;

        Player player = (Player) event.getWhoClicked();

        if (event.getClickedInventory() != null && event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        // Permitir colocar fichas en el slot 22
        if (event.getSlot() == 22) {
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            
            if (cursor != null && cursor.isSimilar(EconomyItems.createVithiumToken())) {
                event.setCancelled(false);
                return;
            }
            
            if (current != null && current.isSimilar(EconomyItems.createVithiumToken())) {
                event.setCancelled(false);
                return;
            }
        }

        if (isSpinning.getOrDefault(player.getUniqueId(), false)) {
            return;
        }

        switch (event.getSlot()) {
            case 0 -> player.closeInventory();
            case 26 -> {
                ItemStack wager = event.getInventory().getItem(22);
                if (wager != null && wager.isSimilar(EconomyItems.createVithiumToken())) {
                    startSpinAnimation(player, event.getInventory());
                } else {
                    player.sendMessage(ChatColor.RED + "¡Coloca una Vithium Ficha primero!");
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(title)) return;
        
        Player player = (Player) event.getPlayer();
        
        // Devolver fichas si las hay
        ItemStack wager = event.getInventory().getItem(22);
        if (wager != null && wager.isSimilar(EconomyItems.createVithiumToken())) {
            player.getInventory().addItem(wager);
        }
        
        // Liberar la máquina
        if (player.hasMetadata("slot_machine_location")) {
            Location machineLoc = (Location) player.getMetadata("slot_machine_location").get(0).value();
            machineUsers.remove(machineLoc);
            player.removeMetadata("slot_machine_location", plugin);
        }
    }

    private void startSpinAnimation(Player player, Inventory inv) {
        if (isSpinning.getOrDefault(player.getUniqueId(), false)) return;
        
        isSpinning.put(player.getUniqueId(), true);
        
        // Consumir ficha
        ItemStack wager = inv.getItem(22);
        if (wager != null) {
            wager.setAmount(wager.getAmount() - 1);
            if (wager.getAmount() <= 0) {
                inv.setItem(22, null);
            }
        }

        Location machineLoc = null;
        if (player.hasMetadata("slot_machine_location")) {
            machineLoc = (Location) player.getMetadata("slot_machine_location").get(0).value();
        }
        
        final Location finalMachineLoc = machineLoc;

        // Sonido de inicio
        if (finalMachineLoc != null) {
            finalMachineLoc.getWorld().playSound(finalMachineLoc, Sound.BLOCK_PISTON_EXTEND, 1.0f, 1.0f);
        }

        new BukkitRunnable() {
            final int maxTicks = 40;
            final Random random = new Random();
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    checkWin(player, inv, finalMachineLoc);
                    isSpinning.put(player.getUniqueId(), false);
                    this.cancel();
                    return;
                }

                // Sonidos durante la animación
                if (finalMachineLoc != null && ticks % 5 == 0) {
                    finalMachineLoc.getWorld().playSound(finalMachineLoc, Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.0f + (ticks * 0.02f));
                }

                // Parar rodillos gradualmente
                for (int i = 0; i < reelSlots.length; i++) {
                    int slot = reelSlots[i];
                    if (ticks < maxTicks - (i * 8)) {
                        setRandomSymbol(inv, slot);
                    }
                }

                player.updateInventory();
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void checkWin(Player player, Inventory inv, Location machineLoc) {
        Material[] results = new Material[3];
        for (int i = 0; i < reelSlots.length; i++) {
            ItemStack item = inv.getItem(reelSlots[i]);
            if (item != null) {
                results[i] = item.getType();
            }
        }

        // Contar símbolos iguales
        Map<Material, Integer> symbolCount = new HashMap<>();
        for (Material symbol : results) {
            symbolCount.put(symbol, symbolCount.getOrDefault(symbol, 0) + 1);
        }

        // Determinar tipo de victoria
        Material winningSymbol = null;
        boolean threeOfAKind = false;
        boolean twoOfAKind = false;

        for (Map.Entry<Material, Integer> entry : symbolCount.entrySet()) {
            if (entry.getValue() == 3) {
                winningSymbol = entry.getKey();
                threeOfAKind = true;
                break;
            } else if (entry.getValue() == 2) {
                winningSymbol = entry.getKey();
                twoOfAKind = true;
            }
        }

        if (threeOfAKind || twoOfAKind) {
            giveReward(player, winningSymbol, threeOfAKind, machineLoc);
        } else {
            // Sonido de pérdida
            if (machineLoc != null) {
                machineLoc.getWorld().playSound(machineLoc, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            }
            player.sendMessage(ChatColor.RED + "¡No hay suerte esta vez!");
        }
    }

    private void giveReward(Player player, Material symbol, boolean threeOfAKind, Location machineLoc) {
        String category = getCategoryFromSymbol(symbol);
        String rewardType = threeOfAKind ? "three_out_of_three" : "two_out_of_three";
        
        List<String> rewards = config.getStringList("SlotMachine." + category + "." + rewardType);
        
        if (rewards.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No hay recompensas configuradas para esta categoría.");
            return;
        }

        // Seleccionar recompensa aleatoria
        String selectedReward = rewards.get(new Random().nextInt(rewards.size()));
        String[] parts = selectedReward.split(" ");
        
        if (parts.length != 2) {
            player.sendMessage(ChatColor.RED + "Error en la configuración de recompensas.");
            return;
        }

        String itemName = parts[0];
        int amount;
        
        try {
            amount = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Error en la cantidad de la recompensa.");
            return;
        }

        // Crear item
        ItemStack rewardItem = createItemFromName(itemName, amount);
        if (rewardItem == null) {
            player.sendMessage(ChatColor.RED + "Item no reconocido: " + itemName);
            return;
        }

        // Dar recompensa
        HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(rewardItem);
        if (!remaining.isEmpty()) {
            for (ItemStack item : remaining.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }

        // Mensajes y sonidos
        String multiplier = threeOfAKind ? "x3" : "x2";
        String symbolName = getSymbolName(symbol);
        
        player.sendMessage(ChatColor.GREEN + "¡Has ganado " + multiplier + " " + 
                ChatColor.GOLD + amount + " " + rewardItem.getItemMeta().getDisplayName() + ChatColor.GREEN + "!");

        // Sonidos de victoria
        if (machineLoc != null) {
            if (threeOfAKind) {
                machineLoc.getWorld().playSound(machineLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                machineLoc.getWorld().playSound(machineLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            } else {
                machineLoc.getWorld().playSound(machineLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                machineLoc.getWorld().playSound(machineLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.2f);
            }
        }
    }

    private ItemStack createItemFromName(String itemName, int amount) {
        // Primero intentar items vanilla
        try {
            Material material = Material.valueOf(itemName.toUpperCase());
            return new ItemStack(material, amount);
        } catch (IllegalArgumentException e) {
            // No es item vanilla, intentar items custom
            return createCustomItem(itemName, amount);
        }
    }

    private ItemStack createCustomItem(String itemName, int amount) {
        ItemStack item = null;
        
        switch (itemName.toLowerCase()) {
            case "vithiums_fichas":
                item = EconomyItems.createVithiumToken();
                break;
            case "vithiums":
                item = EconomyItems.createVithiumCoin();
                break;
            case "pepita_infernal":
                item = items.EmblemItems.createPepitaInfernal();
                break;
            case "corrupted_netherite_scrap":
                item = items.CorruptedNetheriteItems.createCorruptedScrapNetherite();
                break;
            case "doubletotem":
                // Necesitarías acceso a la instancia de DoubleLifeTotem
                // Por ahora usar totem normal
                item = new ItemStack(Material.TOTEM_OF_UNDYING);
                break;
            case "lifetotem":
                // Similar al anterior
                item = new ItemStack(Material.TOTEM_OF_UNDYING);
                break;
            case "ultra_pocion_resistencia_fuego":
                item = items.BlazeItems.createPotionOfFireResistance();
                break;
            // Agregar más items custom según necesites
            default:
                return null;
        }
        
        if (item != null) {
            item.setAmount(amount);
        }
        
        return item;
    }

    // Métodos para calcular probabilidades
    private double getTwoOfThreeProbability() {
        return 0.15; // 15% de probabilidad para 2 de 3
    }

    private double getThreeOfThreeProbability() {
        return 0.05; // 5% de probabilidad para 3 de 3
    }
}