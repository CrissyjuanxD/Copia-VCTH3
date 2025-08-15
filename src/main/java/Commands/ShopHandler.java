package Commands;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ShopHandler implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey shopKey;
    private final NamespacedKey shopIdKey;
    private final Map<UUID, String> editingShops = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> editingSlots = new ConcurrentHashMap<>();
    private final Map<UUID, String> playersWaitingForInput = new ConcurrentHashMap<>();
    private final Map<UUID, String> editingType = new ConcurrentHashMap<>();
    private final File tradesFile;

    public ShopHandler(JavaPlugin plugin) {
        this.plugin = plugin;
        this.shopKey = new NamespacedKey(plugin, "shop_type");
        this.shopIdKey = new NamespacedKey(plugin, "shop_id");
        this.tradesFile = new File(plugin.getDataFolder(), "tradeos.yml");
        
        if (!tradesFile.exists()) {
            try {
                tradesFile.getParentFile().mkdirs();
                tradesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Error creando archivo tradeos.yml: " + e.getMessage());
            }
        }
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void spawnShop(String shopType, Location location) {
        Villager villager = (Villager) location.getWorld().spawnEntity(location, EntityType.VILLAGER);
        
        String shopId = UUID.randomUUID().toString();
        
        villager.setCustomName(ChatColor.GOLD + "" + ChatColor.BOLD + shopType);
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setSilent(true);
        villager.setInvulnerable(true);
        villager.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.0);
        
        villager.getPersistentDataContainer().set(shopKey, PersistentDataType.STRING, shopType);
        villager.getPersistentDataContainer().set(shopIdKey, PersistentDataType.STRING, shopId);
        
        // Inicializar con 12 tradeos vacíos
        initializeEmptyTrades(villager);
        saveShopTrades(shopId, villager.getRecipes());
    }

    private void initializeEmptyTrades(Villager villager) {
        List<MerchantRecipe> recipes = new ArrayList<>();
        
        for (int i = 0; i < 12; i++) {
            ItemStack emptyPaper = createEmptyTradeItem();
            MerchantRecipe recipe = new MerchantRecipe(emptyPaper, 999);
            recipe.addIngredient(emptyPaper);
            recipes.add(recipe);
        }
        
        villager.setRecipes(recipes);
    }

    private ItemStack createEmptyTradeItem() {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.setDisplayName(ChatColor.GRAY + "Vacío");
        meta.setCustomModelData(100);
        paper.setItemMeta(meta);
        return paper;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        
        Villager villager = (Villager) event.getRightClicked();
        Player player = event.getPlayer();
        
        if (!villager.getPersistentDataContainer().has(shopKey, PersistentDataType.STRING)) return;
        
        if (player.isSneaking() && player.isOp()) {
            event.setCancelled(true);
            openShopConfigGUI(player, villager);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;
        
        Villager villager = (Villager) event.getEntity();
        if (villager.getPersistentDataContainer().has(shopKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    private void openShopConfigGUI(Player player, Villager villager) {
        String shopType = villager.getPersistentDataContainer().get(shopKey, PersistentDataType.STRING);
        String shopId = villager.getPersistentDataContainer().get(shopIdKey, PersistentDataType.STRING);
        
        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.GOLD + "Configurar Tienda: " + shopType);
        
        editingShops.put(player.getUniqueId(), shopId);
        
        // Cargar tradeos guardados
        loadShopTrades(shopId, villager);
        
        // Configurar GUI con slots para 12 tradeos con separaciones
        for (int trade = 0; trade < 12; trade++) {
            int row = trade / 4; // 4 tradeos por fila
            int col = trade % 4; // Posición en la fila
            
            int baseSlot = row * 9 + col * 2; // Separación de 2 slots entre tradeos
            
            int ingredient1Slot = baseSlot;
            int ingredient2Slot = baseSlot + 9; // Una fila abajo
            int resultSlot = baseSlot + 18; // Dos filas abajo
            
            // Colocar items de configuración
            gui.setItem(ingredient1Slot, createConfigSlotItem("Ingrediente 1", trade));
            gui.setItem(ingredient2Slot, createConfigSlotItem("Ingrediente 2", trade));
            gui.setItem(resultSlot, createConfigSlotItem("Resultado", trade));
            
            // Añadir separadores visuales
            if (col < 3) { // No poner separador después del último tradeo de la fila
                ItemStack separator = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
                ItemMeta sepMeta = separator.getItemMeta();
                sepMeta.setDisplayName(" ");
                separator.setItemMeta(sepMeta);
                
                gui.setItem(baseSlot + 1, separator);
                gui.setItem(baseSlot + 10, separator);
                gui.setItem(baseSlot + 19, separator);
            }
        }
        
        player.openInventory(gui);
    }

    private ItemStack createConfigSlotItem(String type, int tradeNumber) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName(ChatColor.YELLOW + type + " - Tradeo " + (tradeNumber + 1));
        meta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Click para configurar",
                ChatColor.GRAY + "Escribe en chat: <item> <cantidad>",
                ChatColor.GRAY + "Ejemplo: diamond 5",
                ChatColor.RED + "Escribe 'eliminar' para borrar el tradeo",
                ""
        ));
        meta.setCustomModelData(101 + tradeNumber);
        
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();
        
        if (title.startsWith(ChatColor.GOLD + "Configurar Tienda:")) {
            event.setCancelled(true);
            
            if (event.getCurrentItem() == null) return;
            
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta == null || !meta.hasCustomModelData()) return;
            
            int customModelData = meta.getCustomModelData();
            if (customModelData < 101 || customModelData > 112) return;
            
            int tradeNumber = customModelData - 101;
            int slot = event.getSlot();
            
            // Determinar qué tipo de slot es
            String slotType = determineSlotType(slot, tradeNumber);
            if (slotType == null) return;
            
            editingSlots.put(player.getUniqueId(), tradeNumber);
            editingType.put(player.getUniqueId(), slotType);
            playersWaitingForInput.put(player.getUniqueId(), "trade_config");
            
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "Configura " + slotType + " para el Tradeo " + (tradeNumber + 1));
            player.sendMessage(ChatColor.GRAY + "Formato: <item> <cantidad>");
            player.sendMessage(ChatColor.GRAY + "Ejemplo: diamond 5 o nether_emblem 1");
            player.sendMessage(ChatColor.RED + "Escribe 'eliminar' para borrar todo el tradeo");
            player.sendMessage(ChatColor.GRAY + "Escribe 'cancelar' para abortar");
        }
    }

    private String determineSlotType(int slot, int tradeNumber) {
        int row = tradeNumber / 4;
        int col = tradeNumber % 4;
        int baseSlot = row * 9 + col * 2;
        
        if (slot == baseSlot) return "Ingrediente 1";
        if (slot == baseSlot + 9) return "Ingrediente 2";
        if (slot == baseSlot + 18) return "Resultado";
        
        return null;
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!playersWaitingForInput.containsKey(uuid)) return;

        event.setCancelled(true);
        String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("cancelar")) {
            player.sendMessage(ChatColor.RED + "Configuración cancelada");
            cleanupPlayerData(uuid);
            
            // Reabrir GUI en el hilo principal
            new BukkitRunnable() {
                @Override
                public void run() {
                    String shopId = editingShops.get(uuid);
                    if (shopId != null) {
                        Villager villager = findVillagerByShopId(shopId);
                        if (villager != null) {
                            openShopConfigGUI(player, villager);
                        }
                    }
                }
            }.runTask(plugin);
            return;
        }

        // Manejar eliminación de tradeo completo
        if (input.equalsIgnoreCase("eliminar")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    handleTradeDelete(player, uuid);
                }
            }.runTask(plugin);
            return;
        }

        // Parsear input normal
        String[] parts = input.split(" ");
        if (parts.length != 2) {
            player.sendMessage(ChatColor.RED + "Formato inválido. Usa: <item> <cantidad>");
            return;
        }

        String itemName = parts[0];
        int amount;
        
        try {
            amount = Integer.parseInt(parts[1]);
            if (amount <= 0 || amount > 64) {
                player.sendMessage(ChatColor.RED + "La cantidad debe estar entre 1 y 64");
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "La cantidad debe ser un número válido");
            return;
        }

        // Crear el item en el hilo principal
        new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack item = createItemFromName(itemName, amount);
                if (item == null) {
                    player.sendMessage(ChatColor.RED + "Item no reconocido: " + itemName);
                    return;
                }

                // Aplicar configuración
                int tradeNumber = editingSlots.get(uuid);
                String slotType = editingType.get(uuid);
                String shopId = editingShops.get(uuid);
                
                if (shopId != null) {
                    Villager villager = findVillagerByShopId(shopId);
                    if (villager != null) {
                        updateVillagerTrade(villager, tradeNumber, slotType, item);
                        saveShopTrades(shopId, villager.getRecipes());
                        player.sendMessage(ChatColor.GREEN + slotType + " configurado para Tradeo " + (tradeNumber + 1));
                        
                        // Actualizar GUI
                        updateGUISlot(player, tradeNumber, slotType, item);
                    }
                }

                cleanupPlayerData(uuid);
                
                // Reabrir GUI
                String shopId = editingShops.get(uuid);
                if (shopId != null) {
                    Villager villager = findVillagerByShopId(shopId);
                    if (villager != null) {
                        openShopConfigGUI(player, villager);
                    }
                }
            }
        }.runTask(plugin);
    }

    private void handleTradeDelete(Player player, UUID uuid) {
        int tradeNumber = editingSlots.get(uuid);
        String shopId = editingShops.get(uuid);
        
        if (shopId != null) {
            Villager villager = findVillagerByShopId(shopId);
            if (villager != null) {
                // Resetear el tradeo completo
                resetTrade(villager, tradeNumber);
                saveShopTrades(shopId, villager.getRecipes());
                player.sendMessage(ChatColor.GREEN + "Tradeo " + (tradeNumber + 1) + " eliminado completamente");
            }
        }

        cleanupPlayerData(uuid);
        
        // Reabrir GUI
        String shopId = editingShops.get(uuid);
        if (shopId != null) {
            Villager villager = findVillagerByShopId(shopId);
            if (villager != null) {
                openShopConfigGUI(player, villager);
            }
        }
    }

    private void resetTrade(Villager villager, int tradeNumber) {
        List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
        
        // Asegurar que tenemos suficientes recetas
        while (recipes.size() <= tradeNumber) {
            ItemStack emptyPaper = createEmptyTradeItem();
            MerchantRecipe recipe = new MerchantRecipe(emptyPaper, 999);
            recipe.addIngredient(emptyPaper);
            recipes.add(recipe);
        }
        
        // Resetear el tradeo específico
        ItemStack emptyPaper = createEmptyTradeItem();
        MerchantRecipe emptyRecipe = new MerchantRecipe(emptyPaper, 999);
        emptyRecipe.addIngredient(emptyPaper);
        
        recipes.set(tradeNumber, emptyRecipe);
        villager.setRecipes(recipes);
    }

    private void updateGUISlot(Player player, int tradeNumber, String slotType, ItemStack item) {
        Inventory gui = player.getOpenInventory().getTopInventory();
        if (gui == null) return;
        
        int row = tradeNumber / 4;
        int col = tradeNumber % 4;
        int baseSlot = row * 9 + col * 2;
        
        int targetSlot = -1;
        switch (slotType) {
            case "Ingrediente 1":
                targetSlot = baseSlot;
                break;
            case "Ingrediente 2":
                targetSlot = baseSlot + 9;
                break;
            case "Resultado":
                targetSlot = baseSlot + 18;
                break;
        }
        
        if (targetSlot != -1) {
            ItemStack displayItem = item.clone();
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(ChatColor.GREEN + "Configurado: " + item.getType().name());
                lore.add(ChatColor.GREEN + "Cantidad: " + item.getAmount());
                lore.add("");
                meta.setLore(lore);
                displayItem.setItemMeta(meta);
            }
            gui.setItem(targetSlot, displayItem);
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
        // Usar la misma lógica que ItemsCommands para items custom
        ItemStack item = null;
        
        switch (itemName.toLowerCase()) {
            case "nether_emblem":
                item = items.EmblemItems.createNetherEmblem();
                break;
            case "overworld_emblem":
                item = items.EmblemItems.createOverworldEmblem();
                break;
            case "end_relic":
                item = items.EmblemItems.createEndEmblem();
                break;
            case "vithiums":
                item = items.EconomyItems.createVithiumCoin();
                break;
            case "vithiums_fichas":
                item = items.EconomyItems.createVithiumToken();
                break;
            case "mochila":
                item = items.EconomyItems.createNormalMochila();
                break;
            case "mochila_verde":
                item = items.EconomyItems.createGreenMochila();
                break;
            case "mochila_roja":
                item = items.EconomyItems.createRedMochila();
                break;
            case "mochila_azul":
                item = items.EconomyItems.createBlueMochila();
                break;
            case "mochila_morada":
                item = items.EconomyItems.createPurpleMochila();
                break;
            case "mochila_negra":
                item = items.EconomyItems.createBlackMochila();
                break;
            case "mochila_blanca":
                item = items.EconomyItems.createWhiteMochila();
                break;
            case "mochila_amarilla":
                item = items.EconomyItems.createYellowMochila();
                break;
            case "enderbag":
                item = items.EconomyItems.createEnderBag();
                break;
            case "gancho":
                item = items.EconomyItems.createGancho();
                break;
            case "panic_apple":
                item = items.EconomyItems.createManzanaPanico();
                break;
            case "yunque_nivel_1":
                item = items.EconomyItems.createYunqueReparadorNivel1();
                break;
            case "yunque_nivel_2":
                item = items.EconomyItems.createYunqueReparadorNivel2();
                break;
            case "corrupted_golden_apple":
                item = items.CorruptedGoldenApple.createCorruptedGoldenApple();
                break;
            case "apilate_gold_block":
                item = items.CorruptedGoldenApple.createApilateGoldBlock();
                break;
            case "orbe_de_vida":
                item = items.ReviveItems.createResurrectOrb();
                break;
            case "fragmento_infernal":
                item = items.EmblemItems.createFragmentoInfernal();
                break;
            case "pepita_infernal":
                item = items.EmblemItems.createPepitaInfernal();
                break;
            case "corrupted_nether_star":
                item = items.EmblemItems.createcorruptedNetherStar();
                break;
            case "corrupted_steak":
                item = Dificultades.DayOneChanges.corruptedSteak();
                break;
            case "placa_diamante":
                item = Enchants.EnhancedEnchantmentTable.createDiamondPlate();
                break;
            case "mesa_encantamientos_mejorada":
                item = Enchants.EnhancedEnchantmentTable.createEnhancedEnchantmentTable();
                break;
            case "enderite_sword":
                item = items.EnderiteTools.createEnderiteSword();
                break;
            case "enderite_axe":
                item = items.EnderiteTools.createEnderiteAxe();
                break;
            case "enderite_pickaxe":
                item = items.EnderiteTools.createEnderitePickaxe();
                break;
            case "enderite_shovel":
                item = items.EnderiteTools.createEnderiteShovel();
                break;
            case "enderite_hoe":
                item = items.EnderiteTools.createEnderiteHoe();
                break;
            case "corrupted_netherite_scrap":
                item = items.CorruptedNetheriteItems.createCorruptedScrapNetherite();
                break;
            case "corrupted_netherite_ingot":
                item = items.CorruptedNetheriteItems.createCorruptedNetheriteIngot();
                break;
            case "corrupted_powder":
                item = items.CorruptedMobItems.createCorruptedPowder();
                break;
            case "corrupted_rotten":
                item = items.CorruptedMobItems.createCorruptedMeet();
                break;
            case "corrupted_spidereyes":
                item = items.CorruptedMobItems.createCorruptedSpiderEye();
                break;
            case "corrupted_ancient_debris":
                item = new Blocks.CorruptedAncientDebris(plugin).createcorruptedancientdebris();
                break;
            case "guardian_shulker_heart":
                item = new Blocks.GuardianShulkerHeart(plugin).createGuardianShulkerHeart();
                break;
            case "endstalactitas":
                item = Blocks.Endstalactitas.createEndstalactita();
                break;
            case "ultracorruptedspidereye":
                item = items.ItemsTotems.createUltraCorruptedSpiderEye();
                break;
            case "infernalcreeperpowder":
                item = items.ItemsTotems.createInfernalCreeperPowder();
                break;
            case "whiteenderpearl":
                item = items.ItemsTotems.createWhiteEnderPearl();
                break;
            case "specialtotem":
                item = items.ItemsTotems.createSpecialTotem();
                break;
            case "varita_guardian_blaze":
                item = items.BlazeItems.createBlazeRod();
                break;
            case "polvo_guardian_blaze":
                item = items.BlazeItems.createGuardianBlazePowder();
                break;
            case "ultra_pocion_resistencia_fuego":
                item = items.BlazeItems.createPotionOfFireResistance();
                break;
            case "guardian_shulker_shell":
                item = items.EndItems.createGuardianShulkerShell();
                break;
            case "enderite_nugget":
                item = items.EndItems.createEnderiteNugget(1);
                break;
            case "enderite_fragment":
                item = items.EndItems.createFragmentoEnderite();
                break;
            case "end_amatist":
                item = items.EndItems.createEndAmatist(1);
                break;
            case "enderite_ingot":
                item = items.EndItems.createIngotEnderite();
                break;
            case "enderite_upgrades":
                item = items.EndItems.createEnderiteUpgrades();
                break;
            case "upgrade_vacio":
                item = items.UpgradeNTItems.createUpgradeVacio();
                break;
            case "fragmento_upgrade":
                item = items.UpgradeNTItems.createFragmentoUpgrade();
                break;
            case "duplicador":
                item = items.UpgradeNTItems.createDuplicador();
                break;
        }
        
        if (item != null) {
            item.setAmount(amount);
        }
        
        // Ejecutar en el hilo principal
        new BukkitRunnable() {
            @Override
            public void run() {
                if (item == null) {
                    player.sendMessage(ChatColor.RED + "Item no reconocido: " + itemName);
                    return;
                }

                // Aplicar configuración
                int tradeNumber = editingSlots.get(uuid);
                String slotType = editingType.get(uuid);
                String shopId = editingShops.get(uuid);
                
                if (shopId != null) {
                    Villager villager = findVillagerByShopId(shopId);
                    if (villager != null) {
                        updateVillagerTrade(villager, tradeNumber, slotType, item);
                        saveShopTrades(shopId, villager.getRecipes());
                        player.sendMessage(ChatColor.GREEN + slotType + " configurado para Tradeo " + (tradeNumber + 1));
                        
                        // Actualizar GUI
                        updateGUISlot(player, tradeNumber, slotType, item);
                    }
                }

                cleanupPlayerData(uuid);
                
                // Reabrir GUI
                String shopId = editingShops.get(uuid);
                if (shopId != null) {
                    Villager villager = findVillagerByShopId(shopId);
                    if (villager != null) {
                        openShopConfigGUI(player, villager);
                    }
                }
            }
        }.runTask(plugin);
    }

    private void updateVillagerTrade(Villager villager, int tradeNumber, String slotType, ItemStack item) {
        List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
        
        // Asegurar que tenemos suficientes recetas
        while (recipes.size() <= tradeNumber) {
            ItemStack emptyPaper = createEmptyTradeItem();
            MerchantRecipe recipe = new MerchantRecipe(emptyPaper, 999);
            recipe.addIngredient(emptyPaper);
            recipes.add(recipe);
        }
        
        MerchantRecipe currentRecipe = recipes.get(tradeNumber);
        List<ItemStack> ingredients = new ArrayList<>(currentRecipe.getIngredients());
        ItemStack result = currentRecipe.getResult();
        
        // Asegurar que tenemos al menos 2 ingredientes
        while (ingredients.size() < 2) {
            ingredients.add(new ItemStack(Material.AIR));
        }
        
        switch (slotType) {
            case "Ingrediente 1":
                ingredients.set(0, item);
                break;
            case "Ingrediente 2":
                ingredients.set(1, item);
                break;
            case "Resultado":
                result = item;
                break;
        }
        
        // Crear nueva receta
        MerchantRecipe newRecipe = new MerchantRecipe(result, 999);
        if (!ingredients.get(0).getType().isAir()) {
            newRecipe.addIngredient(ingredients.get(0));
        }
        if (!ingredients.get(1).getType().isAir()) {
            newRecipe.addIngredient(ingredients.get(1));
        }
        
        recipes.set(tradeNumber, newRecipe);
        villager.setRecipes(recipes);
    }

    private void saveShopTrades(String shopId, List<MerchantRecipe> recipes) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(tradesFile);
        
        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe recipe = recipes.get(i);
            String basePath = "shops." + shopId + ".trades." + i;
            
            // Guardar ingredientes
            List<ItemStack> ingredients = recipe.getIngredients();
            for (int j = 0; j < ingredients.size(); j++) {
                config.set(basePath + ".ingredients." + j, ingredients.get(j));
            }
            
            // Guardar resultado
            config.set(basePath + ".result", recipe.getResult());
            config.set(basePath + ".maxUses", recipe.getMaxUses());
        }
        
        try {
            config.save(tradesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando tradeos: " + e.getMessage());
        }
    }

    private void loadShopTrades(String shopId, Villager villager) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(tradesFile);
        
        if (!config.contains("shops." + shopId)) return;
        
        List<MerchantRecipe> recipes = new ArrayList<>();
        
        for (int i = 0; i < 12; i++) {
            String basePath = "shops." + shopId + ".trades." + i;
            
            if (config.contains(basePath)) {
                ItemStack result = config.getItemStack(basePath + ".result");
                int maxUses = config.getInt(basePath + ".maxUses", 999);
                
                if (result != null) {
                    MerchantRecipe recipe = new MerchantRecipe(result, maxUses);
                    
                    // Cargar ingredientes
                    for (int j = 0; j < 2; j++) {
                        ItemStack ingredient = config.getItemStack(basePath + ".ingredients." + j);
                        if (ingredient != null && !ingredient.getType().isAir()) {
                            recipe.addIngredient(ingredient);
                        }
                    }
                    
                    recipes.add(recipe);
                } else {
                    // Tradeo vacío
                    ItemStack emptyPaper = createEmptyTradeItem();
                    MerchantRecipe recipe = new MerchantRecipe(emptyPaper, 999);
                    recipe.addIngredient(emptyPaper);
                    recipes.add(recipe);
                }
            } else {
                // Tradeo vacío
                ItemStack emptyPaper = createEmptyTradeItem();
                MerchantRecipe recipe = new MerchantRecipe(emptyPaper, 999);
                recipe.addIngredient(emptyPaper);
                recipes.add(recipe);
            }
        }
        
        villager.setRecipes(recipes);
    }

    private Villager findVillagerByShopId(String shopId) {
        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (villager.getPersistentDataContainer().has(shopIdKey, PersistentDataType.STRING)) {
                    String villagerShopId = villager.getPersistentDataContainer().get(shopIdKey, PersistentDataType.STRING);
                    if (shopId.equals(villagerShopId)) {
                        return villager;
                    }
                }
            }
        }
        return null;
    }

    private void cleanupPlayerData(UUID uuid) {
        editingShops.remove(uuid);
        editingSlots.remove(uuid);
        editingType.remove(uuid);
        playersWaitingForInput.remove(uuid);
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        
        Player player = (Player) event.getPlayer();
        String title = event.getView().getTitle();
        
        if (title.startsWith(ChatColor.GOLD + "Configurar Tienda:")) {
            // Solo limpiar si no estamos esperando input por chat
            if (!playersWaitingForInput.containsKey(player.getUniqueId())) {
                cleanupPlayerData(player.getUniqueId());
            }
        }
    }
}