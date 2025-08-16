package Casino;

import items.EconomyItems;
import org.bukkit.*;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlackJack implements Listener {
    private final JavaPlugin plugin;
    private final String title = ChatColor.of("#FFD700") + "" + ChatColor.BOLD + "BlackJack";
    private final List<Integer> playerCardSlots = Arrays.asList(19, 20, 21, 22, 23);
    private final List<Integer> dealerCardSlots = Arrays.asList(10, 11, 12, 13, 14);
    private final Map<UUID, Boolean> isPlaying = new ConcurrentHashMap<>();
    private final Map<UUID, List<Card>> playerHands = new ConcurrentHashMap<>();
    private final Map<UUID, List<Card>> dealerHands = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> playerStand = new ConcurrentHashMap<>();
    private final Map<Location, UUID> tableUsers = new ConcurrentHashMap<>();

    // Cartas y sus CustomModelData
    private final Map<String, Integer> cardModelData = new HashMap<>();

    public BlackJack(JavaPlugin plugin) {
        this.plugin = plugin;
        initializeCardModelData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void initializeCardModelData() {
        String[] suits = {"♠", "♥", "♦", "♣"};
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        
        int modelData = 5000;
        for (String suit : suits) {
            for (String rank : ranks) {
                cardModelData.put(rank + suit, modelData++);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.YELLOW_GLAZED_TERRACOTTA) return;

        Player player = event.getPlayer();
        Location blockLoc = event.getClickedBlock().getLocation();

        // Verificar si la mesa ya está siendo usada
        if (tableUsers.containsKey(blockLoc)) {
            UUID currentUser = tableUsers.get(blockLoc);
            Player currentPlayer = Bukkit.getPlayer(currentUser);
            
            if (currentPlayer != null && currentPlayer.isOnline()) {
                player.sendMessage(ChatColor.RED + "Esta mesa está siendo usada por " + currentPlayer.getName());
                return;
            } else {
                // Limpiar usuario desconectado
                tableUsers.remove(blockLoc);
            }
        }

        // Verificar que tenga fichas
        if (!hasVithiumTokens(player)) {
            player.sendMessage(ChatColor.RED + "Necesitas Vithium Fichas para jugar BlackJack.");
            return;
        }

        // Registrar usuario en la mesa
        tableUsers.put(blockLoc, player.getUniqueId());
        
        openBlackJack(player, blockLoc);
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

    private void openBlackJack(Player player, Location tableLoc) {
        Inventory inv = Bukkit.createInventory(null, 36, title);
        setupGUI(inv);
        player.openInventory(inv);
        
        // Guardar la ubicación de la mesa
        player.setMetadata("blackjack_table_location", new org.bukkit.metadata.FixedMetadataValue(plugin, tableLoc));
        
        // Sonido de apertura
        tableLoc.getWorld().playSound(tableLoc, Sound.BLOCK_CHEST_OPEN, 1.0f, 0.8f);
    }

    private void setupGUI(Inventory inv) {
        // Botón de cerrar
        ItemStack closeButton = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeButton.getItemMeta();
        closeMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Cerrar");
        closeMeta.setCustomModelData(6000);
        closeButton.setItemMeta(closeMeta);
        inv.setItem(0, closeButton);

        // Slot para apuesta
        ItemStack wagerSlot = new ItemStack(Material.ECHO_SHARD);
        ItemMeta wagerMeta = wagerSlot.getItemMeta();
        wagerMeta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "Apuesta");
        wagerMeta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Coloca " + ChatColor.GOLD + "Vithium Fichas",
                ChatColor.GRAY + "aquí para apostar",
                ""
        ));
        wagerMeta.setCustomModelData(6000);
        wagerSlot.setItemMeta(wagerMeta);
        inv.setItem(31, wagerSlot);

        // Botón de repartir
        ItemStack dealButton = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta dealMeta = dealButton.getItemMeta();
        dealMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "REPARTIR");
        dealMeta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Coloca tu apuesta y haz clic para empezar",
                ""
        ));
        dealMeta.setCustomModelData(6000);
        dealButton.setItemMeta(dealMeta);
        inv.setItem(25, dealButton);

        // Botón de pedir carta
        ItemStack hitButton = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta hitMeta = hitButton.getItemMeta();
        hitMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "PEDIR");
        hitMeta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Pedir otra carta",
                ""
        ));
        hitMeta.setCustomModelData(6000);
        hitButton.setItemMeta(hitMeta);
        inv.setItem(33, hitButton);

        // Botón de plantarse
        ItemStack standButton = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta standMeta = standButton.getItemMeta();
        standMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "PLANTARSE");
        standMeta.setLore(Arrays.asList(
                "",
                ChatColor.GRAY + "Mantener tu mano actual",
                ""
        ));
        standMeta.setCustomModelData(6000);
        standButton.setItemMeta(standMeta);
        inv.setItem(34, standButton);

        // Llenar espacios vacíos
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null && !playerCardSlots.contains(i) && !dealerCardSlots.contains(i)) {
                ItemStack frame = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta frameMeta = frame.getItemMeta();
                frameMeta.setDisplayName(" ");
                frameMeta.setCustomModelData(6000);
                frame.setItemMeta(frameMeta);
                inv.setItem(i, frame);
            }
        }
    }

    private void initializeDeck() {
        // El mazo se inicializa cuando se necesita
    }

    private Card drawCard() {
        String[] suits = {"♠", "♥", "♦", "♣"};
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        
        Random random = new Random();
        String suit = suits[random.nextInt(suits.length)];
        String rank = ranks[random.nextInt(ranks.length)];
        
        return new Card(rank, suit);
    }

    private void startGame(Player player, Inventory inv) {
        UUID playerId = player.getUniqueId();
        
        if (isPlaying.getOrDefault(playerId, false)) return;

        ItemStack wager = inv.getItem(31);
        if (wager == null || !wager.isSimilar(EconomyItems.createVithiumToken())) {
            player.sendMessage(ChatColor.RED + "¡Coloca Vithium Fichas primero!");
            return;
        }

        List<Card> playerHand = new ArrayList<>();
        List<Card> dealerHand = new ArrayList<>();
        
        playerHand.add(drawCard());
        dealerHand.add(drawCard());
        playerHand.add(drawCard());
        dealerHand.add(drawCard());

        playerHands.put(playerId, playerHand);
        dealerHands.put(playerId, dealerHand);
        playerStand.put(playerId, false);
        isPlaying.put(playerId, true);

        updateCards(inv, playerId);

        if (calculateHandValue(playerHand) == 21) {
            blackjack(player, inv);
        }

        // Sonido de repartir cartas
        if (player.hasMetadata("blackjack_table_location")) {
            Location tableLoc = (Location) player.getMetadata("blackjack_table_location").get(0).value();
            tableLoc.getWorld().playSound(tableLoc, Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
        }
    }

    private void updateCards(Inventory inv, UUID playerId) {
        List<Card> playerHand = playerHands.get(playerId);
        List<Card> dealerHand = dealerHands.get(playerId);
        boolean playerStanding = playerStand.getOrDefault(playerId, false);

        // Actualizar cartas del jugador
        for (int i = 0; i < playerCardSlots.size(); i++) {
            if (i < playerHand.size()) {
                Card card = playerHand.get(i);
                ItemStack cardItem = createCardItem(card);
                inv.setItem(playerCardSlots.get(i), cardItem);
            } else {
                inv.setItem(playerCardSlots.get(i), null);
            }
        }

        // Actualizar cartas del dealer
        for (int i = 0; i < dealerCardSlots.size(); i++) {
            if (i < dealerHand.size()) {
                Card card = dealerHand.get(i);
                ItemStack cardItem;
                
                if (i == 1 && !playerStanding) {
                    // Carta oculta
                    cardItem = new ItemStack(Material.PAPER);
                    ItemMeta meta = cardItem.getItemMeta();
                    meta.setDisplayName(ChatColor.DARK_GRAY + "Carta Oculta");
                    meta.setCustomModelData(6000);
                    cardItem.setItemMeta(meta);
                } else {
                    cardItem = createCardItem(card);
                }
                inv.setItem(dealerCardSlots.get(i), cardItem);
            } else {
                inv.setItem(dealerCardSlots.get(i), null);
            }
        }
    }

    private ItemStack createCardItem(Card card) {
        ItemStack cardItem = new ItemStack(Material.PAPER);
        ItemMeta meta = cardItem.getItemMeta();
        
        String cardString = card.toString();
        meta.setDisplayName(ChatColor.WHITE + cardString);
        
        // Asignar CustomModelData basado en la carta
        Integer modelData = cardModelData.get(cardString);
        if (modelData != null) {
            meta.setCustomModelData(modelData);
        } else {
            meta.setCustomModelData(6000); // Fallback
        }
        
        cardItem.setItemMeta(meta);
        return cardItem;
    }

    private int calculateHandValue(List<Card> hand) {
        int value = 0;
        int aces = 0;

        for (Card card : hand) {
            if (card.rank.equals("A")) {
                aces++;
            } else if (card.rank.equals("K") || card.rank.equals("Q") || card.rank.equals("J")) {
                value += 10;
            } else {
                value += Integer.parseInt(card.rank);
            }
        }

        for (int i = 0; i < aces; i++) {
            if (value + 11 <= 21) {
                value += 11;
            } else {
                value += 1;
            }
        }

        return value;
    }

    private void hit(Player player, Inventory inv) {
        UUID playerId = player.getUniqueId();
        
        if (!isPlaying.getOrDefault(playerId, false) || playerStand.getOrDefault(playerId, false)) return;

        List<Card> playerHand = playerHands.get(playerId);
        if (playerHand == null) return;

        playerHand.add(drawCard());
        updateCards(inv, playerId);

        int value = calculateHandValue(playerHand);
        if (value > 21) {
            bust(player, inv);
        } else if (value == 21) {
            stand(player, inv);
        }

        // Sonido de carta
        if (player.hasMetadata("blackjack_table_location")) {
            Location tableLoc = (Location) player.getMetadata("blackjack_table_location").get(0).value();
            tableLoc.getWorld().playSound(tableLoc, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.0f);
        }
    }

    private void stand(Player player, Inventory inv) {
        UUID playerId = player.getUniqueId();
        
        if (!isPlaying.getOrDefault(playerId, false) || playerStand.getOrDefault(playerId, false)) return;

        playerStand.put(playerId, true);
        updateCards(inv, playerId);

        List<Card> dealerHand = dealerHands.get(playerId);
        if (dealerHand == null) return;

        // Dealer debe pedir cartas hasta 17
        new BukkitRunnable() {
            @Override
            public void run() {
                if (calculateHandValue(dealerHand) < 17) {
                    dealerHand.add(drawCard());
                    updateCards(inv, playerId);
                    
                    // Sonido de carta del dealer
                    if (player.hasMetadata("blackjack_table_location")) {
                        Location tableLoc = (Location) player.getMetadata("blackjack_table_location").get(0).value();
                        tableLoc.getWorld().playSound(tableLoc, Sound.ENTITY_ITEM_PICKUP, 0.8f, 0.8f);
                    }
                } else {
                    checkWinner(player, inv);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void checkWinner(Player player, Inventory inv) {
        UUID playerId = player.getUniqueId();
        
        List<Card> playerHand = playerHands.get(playerId);
        List<Card> dealerHand = dealerHands.get(playerId);
        
        if (playerHand == null || dealerHand == null) return;

        int playerValue = calculateHandValue(playerHand);
        int dealerValue = calculateHandValue(dealerHand);

        if (dealerValue > 21 || playerValue > dealerValue) {
            win(player, inv);
        } else if (dealerValue > playerValue) {
            lose(player, inv, "¡El dealer gana con " + dealerValue + "!");
        } else {
            push(player, inv);
        }
    }

    private void blackjack(Player player, Inventory inv) {
        ItemStack wager = inv.getItem(31);
        if (wager != null && wager.isSimilar(EconomyItems.createVithiumToken())) {
            int winAmount = wager.getAmount() * 3;
            ItemStack reward = EconomyItems.createVithiumToken();
            reward.setAmount(winAmount);
            
            HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(reward);
            if (!remaining.isEmpty()) {
                for (ItemStack item : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            
            // Sonidos de victoria
            if (player.hasMetadata("blackjack_table_location")) {
                Location tableLoc = (Location) player.getMetadata("blackjack_table_location").get(0).value();
                tableLoc.getWorld().playSound(tableLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                tableLoc.getWorld().playSound(tableLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            }
            
            player.sendMessage(ChatColor.GREEN + "¡BLACKJACK! ¡Ganaste " + winAmount + " Vithium Fichas!");
            inv.setItem(31, null);
        }
        endGame(player.getUniqueId());
    }

    private void win(Player player, Inventory inv) {
        ItemStack wager = inv.getItem(31);
        if (wager != null && wager.isSimilar(EconomyItems.createVithiumToken())) {
            int winAmount = wager.getAmount() * 2;
            ItemStack reward = EconomyItems.createVithiumToken();
            reward.setAmount(winAmount);
            
            HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(reward);
            if (!remaining.isEmpty()) {
                for (ItemStack item : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            
            // Sonido de victoria
            if (player.hasMetadata("blackjack_table_location")) {
                Location tableLoc = (Location) player.getMetadata("blackjack_table_location").get(0).value();
                tableLoc.getWorld().playSound(tableLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
                tableLoc.getWorld().playSound(tableLoc, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f);
            }
            
            player.sendMessage(ChatColor.GREEN + "¡Ganaste " + winAmount + " Vithium Fichas!");
            inv.setItem(31, null);
        }
        endGame(player.getUniqueId());
    }

    private void push(Player player, Inventory inv) {
        ItemStack wager = inv.getItem(31);
        if (wager != null && wager.isSimilar(EconomyItems.createVithiumToken())) {
            HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(wager);
            if (!remaining.isEmpty()) {
                for (ItemStack item : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            
            // Sonido de empate
            if (player.hasMetadata("blackjack_table_location")) {
                Location tableLoc = (Location) player.getMetadata("blackjack_table_location").get(0).value();
                tableLoc.getWorld().playSound(tableLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
            }
            
            player.sendMessage(ChatColor.YELLOW + "¡Empate! Tu apuesta ha sido devuelta.");
            inv.setItem(31, null);
        }
        endGame(player.getUniqueId());
    }

    private void bust(Player player, Inventory inv) {
        lose(player, inv, "¡Te pasaste! Tu mano supera 21!");
    }

    private void lose(Player player, Inventory inv, String message) {
        // Sonido de pérdida
        if (player.hasMetadata("blackjack_table_location")) {
            Location tableLoc = (Location) player.getMetadata("blackjack_table_location").get(0).value();
            tableLoc.getWorld().playSound(tableLoc, Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
        }
        
        player.sendMessage(ChatColor.RED + message);
        inv.setItem(31, null);
        endGame(player.getUniqueId());
    }

    private void endGame(UUID playerId) {
        isPlaying.remove(playerId);
        playerHands.remove(playerId);
        dealerHands.remove(playerId);
        playerStand.remove(playerId);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(title)) return;

        Player player = (Player) event.getWhoClicked();
        UUID playerId = player.getUniqueId();

        if (event.getClickedInventory() != null && event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;

        // Permitir colocar fichas en el slot 31
        if (event.getSlot() == 31) {
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

        switch (event.getSlot()) {
            case 0 -> player.closeInventory();
            case 25 -> {
                if (!isPlaying.getOrDefault(playerId, false)) {
                    startGame(player, event.getInventory());
                }
            }
            case 33 -> {
                if (isPlaying.getOrDefault(playerId, false) && !playerStand.getOrDefault(playerId, false)) {
                    hit(player, event.getInventory());
                }
            }
            case 34 -> {
                if (isPlaying.getOrDefault(playerId, false) && !playerStand.getOrDefault(playerId, false)) {
                    stand(player, event.getInventory());
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(title)) return;
        
        Player player = (Player) event.getPlayer();
        UUID playerId = player.getUniqueId();
        
        // Devolver fichas si las hay
        ItemStack wager = event.getInventory().getItem(31);
        if (wager != null && wager.isSimilar(EconomyItems.createVithiumToken())) {
            HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(wager);
            if (!remaining.isEmpty()) {
                for (ItemStack item : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
        }
        
        // Liberar la mesa
        if (player.hasMetadata("blackjack_table_location")) {
            Location tableLoc = (Location) player.getMetadata("blackjack_table_location").get(0).value();
            tableUsers.remove(tableLoc);
            player.removeMetadata("blackjack_table_location", plugin);
        }
        
        // Limpiar datos del juego
        endGame(playerId);
    }

    private static class Card {
        private final String rank;
        private final String suit;

        public Card(String rank, String suit) {
            this.rank = rank;
            this.suit = suit;
        }

        @Override
        public String toString() {
            return rank + suit;
        }
    }
}