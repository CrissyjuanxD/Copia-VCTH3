package Dificultades.CustomMobs;

import items.ItemsTotems;
import items.IceBow.IceBowItem;
import items.IceBow.IceBowLogic;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class Iceologer implements Listener {
    private final JavaPlugin plugin;
    private final Set<Evoker> activeIceologers = new HashSet<>();
    private final Set<Player> frozenPlayers = new HashSet<>();
    private boolean eventsRegistered = false;
    private final Random random = new Random();
    private final NamespacedKey iceologerKey;
    private final NamespacedKey iceAngelKey;
    private final NamespacedKey iceFangsKey;
    private final Set<UUID> blindnessApplied = new HashSet<>();
    private final Map<UUID, Long> playerBowCooldowns = new HashMap<>();

    // Instancias para el manejo del arco de hielo
    private final IceBowItem iceBowItem;
    private final IceBowLogic iceBowLogic;

    public Iceologer(JavaPlugin plugin) {
        this.plugin = plugin;
        this.iceologerKey = new NamespacedKey(plugin, "iceologer");
        this.iceAngelKey = new NamespacedKey(plugin, "ice_angel");
        this.iceFangsKey = new NamespacedKey(plugin, "ice_fangs");

        // Inicializar instancias del arco de hielo
        this.iceBowItem = new IceBowItem(plugin);
        this.iceBowLogic = new IceBowLogic(plugin, playerBowCooldowns);
    }

    public void apply() {
        if (!eventsRegistered) {
            Bukkit.getPluginManager().registerEvents(this, plugin);
            Bukkit.getPluginManager().registerEvents(iceBowLogic, plugin);
            eventsRegistered = true;
        }
    }

    public void revert() {
        if (eventsRegistered) {
            for (Evoker iceologer : activeIceologers) {
                if (iceologer.isValid() && !iceologer.isDead()) {
                    iceologer.remove();
                }
            }
            activeIceologers.clear();
            frozenPlayers.clear();
            blindnessApplied.clear();
            eventsRegistered = false;
        }
    }

    public Evoker spawnIceologer(Location location) {
        Evoker iceologer = (Evoker) location.getWorld().spawnEntity(location, EntityType.EVOKER);
        iceologer.setCustomName(ChatColor.AQUA + "" + ChatColor.BOLD + "Iceologer");
        iceologer.setCustomNameVisible(true);
        iceologer.getAttribute(Attribute.GENERIC_FOLLOW_RANGE).setBaseValue(32);

        // Aumentar la vida del Iceologer (el doble de vida normal)
        iceologer.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(48.0); // Normal: 24.0
        iceologer.setHealth(48.0);

        iceologer.getPersistentDataContainer().set(iceologerKey, PersistentDataType.BYTE, (byte) 1);

        // Equipar el arco de hielo usando la nueva clase
        ItemStack iceBow = iceBowItem.createIceBow();
        iceologer.getEquipment().setItemInMainHand(iceBow);
        iceologer.getEquipment().setItemInMainHandDropChance(0.0f);

        activeIceologers.add(iceologer);
        monitorIceologer(iceologer);
        return iceologer;
    }

    public void monitorIceologer(Evoker iceologer) {
        new BukkitRunnable() {
            private int bowCooldown = 0;
            private int fangsCooldown = 0;

            @Override
            public void run() {
                if (iceologer.isDead() || !iceologer.isValid()) {
                    cancel();
                    activeIceologers.remove(iceologer);
                    return;
                }

                if (iceologer.getTarget() instanceof Player player) {
                    if ((player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) &&
                            iceologer.hasLineOfSight(player)) {

                        double distance = iceologer.getLocation().distance(player.getLocation());

                        // Disparar arco cada 3-4 segundos si está a distancia media/larga
                        if (distance >= 8 && distance <= 20 && bowCooldown <= 0) {
                            iceBowLogic.shootIceBow(iceologer, player, iceologerKey);
                            bowCooldown = 60 + random.nextInt(20); // 3-4 segundos
                        }

                        if (distance < 10 && fangsCooldown <= 0) {
                            performCustomFangsAttack(iceologer, player);
                            fangsCooldown = 300 + random.nextInt(100); // 15-20 segundos
                        }

                        // Ataque especial cada 6 segundos
                        if (distance < 15 && iceologer.getTicksLived() % 120 == 0) {
                            performSpecialAttack(iceologer, player);
                        }

                        // Ataque de bloques de hielo cada 5 segundos
                        if (iceologer.getTicksLived() % 100 == 0) {
                            performIceBlockAttack(iceologer);
                        }
                    }
                }

                if (bowCooldown > 0) {
                    bowCooldown--;
                }
                if (fangsCooldown > 0) {
                    fangsCooldown--;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // MÉTODO PRINCIPAL PARA INTERCEPTAR EVOKER FANGS
    @EventHandler
    public void onEvokerFangsSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof EvokerFangs fangs)) return;

        // Buscar si hay un Iceologer cerca (30 bloques como pediste)
        for (Evoker iceologer : activeIceologers) {
            if (iceologer.getWorld().equals(fangs.getWorld()) &&
                    iceologer.getLocation().distance(fangs.getLocation()) <= 30) {

                // Cancelar el spawn del fang vanilla
                event.setCancelled(true);

                // Crear nuestro fang custom en la misma ubicación
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    createCustomIceFang(iceologer, fangs.getLocation());
                }, 1L);

                break;
            }
        }
    }

    // Método simplificado para crear fangs custom
    private void createCustomIceFang(Evoker summoner, Location location) {
        // Verificar que la ubicación sea válida
        if (location.getBlock().getType().isSolid()) {
            return;
        }

        // Crear el EvokerFang custom
        EvokerFangs fang = (EvokerFangs) summoner.getWorld().spawnEntity(location, EntityType.EVOKER_FANGS);

        // CONFIGURAR EL NOMBRE CUSTOM
        fang.setCustomName(ChatColor.AQUA + "" + ChatColor.BOLD + "Iceologer");
        fang.setCustomNameVisible(false);

        // Marcar como fang de hielo
        fang.getPersistentDataContainer().set(iceFangsKey, PersistentDataType.BYTE, (byte) 1);

        // Efectos de hielo
        fang.getWorld().spawnParticle(Particle.SNOWFLAKE,
                location.add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.1);
        fang.getWorld().playSound(location,
                Sound.BLOCK_GLASS_BREAK, 0.7f, 1.2f);

        plugin.getLogger().info("Fang custom creado con nombre: " + fang.getCustomName());
    }

    // Interceptar el ataque normal de invocación de vexes
    @EventHandler
    public void onVexSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() != EntityType.VEX) return;
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.SPELL) return;

        Vex vex = (Vex) event.getEntity();

        // Buscar Iceologers activos en un radio
        for (Evoker iceologer : activeIceologers) {
            if (iceologer.getWorld().equals(vex.getWorld()) &&
                    iceologer.getLocation().distance(vex.getLocation()) <= 12) {

                // Transformar el Vex en Ángel de Hielo
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (vex.isValid() && !vex.isDead()) {
                        transformToIceAngel(vex);
                    }
                }, 2L);
                break;
            }
        }
    }

    // Método para transformar Vex en Ángeles de Hielo
    private void transformToIceAngel(Vex vex) {
        vex.setCustomName(ChatColor.AQUA + "" + ChatColor.BOLD + "Ángel de Hielo");
        vex.setCustomNameVisible(false);
        vex.getPersistentDataContainer().set(iceAngelKey, PersistentDataType.BYTE, (byte) 1);

        // Mejorar atributos
        vex.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        vex.setHealth(20.0);

        // Efectos visuales
        vex.getWorld().spawnParticle(Particle.SNOWFLAKE,
                vex.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        vex.getWorld().playSound(vex.getLocation(),
                Sound.ENTITY_PLAYER_HURT_FREEZE, 0.8f, 1.2f);
    }

    private void performCustomFangsAttack(Evoker iceologer, LivingEntity target) {
        // Verificar cooldown manualmente para evitar spam
        Long lastAttack = iceologer.getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "last_fangs_attack"), PersistentDataType.LONG);

        if (lastAttack != null && System.currentTimeMillis() - lastAttack < 5000) {
            return;
        }

        // Registrar el último ataque
        iceologer.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "last_fangs_attack"),
                PersistentDataType.LONG,
                System.currentTimeMillis()
        );

        Location startLocation = iceologer.getLocation();
        Location targetLocation = target.getLocation();

        // Sonido de preparación
        iceologer.getWorld().playSound(startLocation, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.8f);

        // Efectos visuales en el Iceologer
        iceologer.getWorld().spawnParticle(Particle.SNOWFLAKE,
                startLocation.add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.1);

        // Calcular dirección y distancia
        Vector direction = targetLocation.toVector().subtract(startLocation.toVector()).normalize();
        double distance = startLocation.distance(targetLocation);

        // Spawnear fangs en un camino hacia el objetivo
        int fangsCount = (int) (distance * 0.8);
        fangsCount = Math.min(Math.max(fangsCount, 3), 12);

        for (int i = 0; i < fangsCount; i++) {
            double progress = (i + 1) / (double) fangsCount;
            Location fangLocation = startLocation.clone().add(direction.clone().multiply(distance * progress));

            // Ajustar altura
            fangLocation.setY(fangLocation.getWorld().getHighestBlockYAt(fangLocation) + 0.1);

            // Delay para efecto de onda
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                createCustomIceFang(iceologer, fangLocation);
            }, i * 2L);
        }
    }

    @EventHandler
    public void onEntityDamageByFangs(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof EvokerFangs fangs) {
            // Verificar si es uno de nuestros fangs custom
            if (fangs.getPersistentDataContainer().has(iceFangsKey, PersistentDataType.BYTE)) {

                // Doblar el daño
                event.setDamage(event.getDamage() * 2.0);

                // Aplicar efectos de congelación
                if (event.getEntity() instanceof LivingEntity entity) {
                    entity.setFreezeTicks(entity.getFreezeTicks() + 100);
                    entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));

                    // Efectos visuales
                    entity.getWorld().spawnParticle(Particle.SNOWFLAKE,
                            entity.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.1);
                    entity.getWorld().playSound(entity.getLocation(),
                            Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, 0.1f);
                }
            }
        }
    }

    // Hacer que los Ángeles de Hielo también apliquen efectos de congelación
    @EventHandler
    public void onIceAngelAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Vex vex &&
                vex.getPersistentDataContainer().has(iceAngelKey, PersistentDataType.BYTE)) {

            if (event.getEntity() instanceof LivingEntity entity) {
                // Aplicar congelación
                entity.setFreezeTicks(entity.getFreezeTicks() + 80); // 4 segundos
                entity.getWorld().spawnParticle(Particle.SNOWFLAKE,
                        entity.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.1);
                entity.getWorld().playSound(entity.getLocation(),
                        Sound.ENTITY_PLAYER_HURT_FREEZE, 0.5f, 1.2f);
            }
        }
    }

    private void performSpecialAttack(Evoker iceologer, Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 10f, 2f);
        Location startLocation = player.getLocation().add(0, 10, 0);
        BlockData blockData = Material.PACKED_ICE.createBlockData();

        BlockDisplay blockDisplay = (BlockDisplay) iceologer.getWorld().spawnEntity(startLocation, EntityType.BLOCK_DISPLAY);
        blockDisplay.setBlock(blockData);
        blockDisplay.setCustomName("iceSphere");
        blockDisplay.setCustomNameVisible(false);

        blockDisplay.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(0.8f, 0.8f, 0.8f),
                new Quaternionf()
        ));

        blockDisplay.setGlowing(true);
        blockDisplay.setGlowColorOverride(Color.AQUA);

        new BukkitRunnable() {
            private float rotationAngle = 0.0f;

            @Override
            public void run() {
                if (player.isDead() || !player.isOnline()) {
                    blockDisplay.remove();
                    cancel();
                    return;
                }

                boolean hit = false;
                Location currentLocation = blockDisplay.getLocation();

                if (currentLocation.distance(player.getLocation()) <= 1.0) {
                    hit = true;
                } else {
                    Vector direction = player.getLocation().toVector().subtract(currentLocation.toVector()).normalize();
                    blockDisplay.teleport(currentLocation.add(direction.multiply(0.3)));

                    rotationAngle += 10.0f;
                    Quaternionf rotation = new Quaternionf().rotateY((float) Math.toRadians(rotationAngle));
                    blockDisplay.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            rotation,
                            new Vector3f(0.8f, 0.8f, 0.8f),
                            rotation
                    ));
                }

                if (hit) {
                    if (player.isBlocking()) {
                        player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
                    } else {
                        player.damage(4);
                        applyFreezeEffect(player);
                        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.5f);
                        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 30, 0.5, 0.5, 0.5);
                    }
                    blockDisplay.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void applyFreezeEffect(Player player) {
        player.setFreezeTicks(300);
        frozenPlayers.add(player);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.1f);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // El manejo de flechas ahora está en IceBowLogic
        if (event.getDamager() instanceof Arrow arrow) {
            iceBowLogic.handleArrowDamage(event, arrow, iceologerKey);
        }
    }

    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (event.getEntity() instanceof Evoker iceologer && activeIceologers.contains(iceologer)) {
            if (event.getTarget() instanceof Player player) {
                // Aplicar ceguera a todos los jugadores en un radio de 25 bloques (solo una vez)
                if (!blindnessApplied.contains(iceologer.getUniqueId())) {
                    for (Player nearbyPlayer : iceologer.getWorld().getPlayers()) {
                        if (nearbyPlayer.getLocation().distance(iceologer.getLocation()) <= 25) {
                            nearbyPlayer.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 300, 0)); // 15 segundos
                        }
                    }
                    blindnessApplied.add(iceologer.getUniqueId());
                }
            } else {
                // Limpiar efectos de congelación si el objetivo no es un jugador
                if (event.getTarget() instanceof Player player) {
                    frozenPlayers.remove(player);
                    player.setFreezeTicks(0);
                }
            }
        }
    }

    private void performIceBlockAttack(Evoker iceologer) {
        if (random.nextInt(4) != 0) return;

        World world = iceologer.getWorld();
        List<Player> nearbyPlayers = new ArrayList<>();

        for (Entity entity : iceologer.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Player player &&
                    (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)) {
                nearbyPlayers.add(player);
            }
        }

        if (nearbyPlayers.isEmpty()) return;

        Player target = nearbyPlayers.size() > 1
                ? nearbyPlayers.get(new Random().nextInt(nearbyPlayers.size()))
                : nearbyPlayers.get(0);

        Location origin = target.getLocation().add(0, 10, 0);

        world.playSound(target.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.5f);

        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(45 * i);
            double x = Math.cos(angle) * 3;
            double z = Math.sin(angle) * 3;
            Location spawnLocation = origin.clone().add(x, 0, z);

            BlockData blockData = Material.PACKED_ICE.createBlockData();
            BlockDisplay blockDisplay = (BlockDisplay) world.spawnEntity(spawnLocation, EntityType.BLOCK_DISPLAY);
            blockDisplay.setBlock(blockData);
            blockDisplay.setGlowing(true);
            blockDisplay.setGlowColorOverride(Color.BLUE);

            animateFallingBlock(blockDisplay, target.getLocation());
        }
    }

    private void animateFallingBlock(BlockDisplay blockDisplay, Location center) {
        new BukkitRunnable() {
            private double height = 10;
            private double velocity = 0.2;

            @Override
            public void run() {
                Location currentLocation = blockDisplay.getLocation();
                height -= velocity;
                blockDisplay.teleport(currentLocation.subtract(0, velocity, 0));

                currentLocation.getWorld().spawnParticle(Particle.SNOWFLAKE, currentLocation, 5, 0.1, 0.1, 0.1, 0.1);

                if (height <= 0) {
                    applyExplosionEffect(blockDisplay.getLocation(), center);
                    blockDisplay.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void applyExplosionEffect(Location location, Location center) {
        World world = location.getWorld();

        world.spawnParticle(Particle.EXPLOSION_EMITTER, location, 1);
        world.playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2f, 2f);

        double radius = 3.0;
        for (Entity entity : world.getNearbyEntities(location, radius, radius, radius)) {
            if (entity instanceof Player player &&
                    (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE)) {

                if (player.getLocation().distance(center) <= radius) {
                    player.damage(5);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
                }
            }
        }
    }

    @EventHandler
    public void onIceologerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Evoker iceologer &&
                iceologer.getPersistentDataContainer().has(iceologerKey, PersistentDataType.BYTE)) {

            // Sonido de daño de Illusioner
            iceologer.getWorld().playSound(iceologer.getLocation(), Sound.ENTITY_ILLUSIONER_HURT, 1.0f, 1.5f);
        }
    }

    @EventHandler
    public void onIceologerDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Evoker iceologer &&
                iceologer.getPersistentDataContainer().has(iceologerKey, PersistentDataType.BYTE)) {

            // Prevenir que suelte totems
            event.getDrops().removeIf(item -> item.getType() == Material.TOTEM_OF_UNDYING);

            // 10% de probabilidad de dropear el arco
            if (random.nextDouble() <= 0.1) {
                ItemStack iceBow = iceBowItem.createIceBow();
                iceologer.getWorld().dropItemNaturally(iceologer.getLocation(), iceBow);
            }

            // Sonido de muerte de Illusioner
            iceologer.getWorld().playSound(iceologer.getLocation(), Sound.ENTITY_ILLUSIONER_DEATH, SoundCategory.HOSTILE, 1.0f, 1.5f);
            iceologer.getWorld().dropItemNaturally(iceologer.getLocation(), ItemsTotems.createIceCrystal());

            activeIceologers.remove(iceologer);
            blindnessApplied.remove(iceologer.getUniqueId());

            // Limpiar cooldowns de jugadores si es necesario
            playerBowCooldowns.entrySet().removeIf(entry ->
                    System.currentTimeMillis() - entry.getValue() > 300000); // Limpiar entradas de más de 5 minutos
        }
    }

    public NamespacedKey getIceologerKey() {
        return iceologerKey;
    }

    private boolean isIceologer(Entity entity) {
        return entity instanceof Evoker && entity.getPersistentDataContainer().has(iceologerKey, PersistentDataType.BYTE);
    }

    // Getters para acceder a las instancias desde otros lugares
    public Set<Evoker> getActiveIceologers() {
        return activeIceologers;
    }

    public IceBowItem getIceBowItem() {
        return iceBowItem;
    }

    public IceBowLogic getIceBowLogic() {
        return iceBowLogic;
    }
}