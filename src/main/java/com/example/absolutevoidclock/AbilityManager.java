package com.example.absolutevoidclock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runs the three abilities, their cooldowns and their timers. */
public final class AbilityManager {

    private static final class Frozen {
        final Location lock;
        final long endTick;
        final boolean hadGravity;
        final boolean addedSlowFall;

        Frozen(Location lock, long endTick, boolean hadGravity, boolean addedSlowFall) {
            this.lock = lock;
            this.endTick = endTick;
            this.hadGravity = hadGravity;
            this.addedSlowFall = addedSlowFall;
        }
    }

    private static final class SlowSession {
        final UUID owner;
        final long endTick;
        final double radius;
        final double slowAmount;
        final int resistanceLevel;
        final NamespacedKey slowKey;
        final NamespacedKey boostKey;
        final Map<UUID, LivingEntity> affected = new HashMap<>();

        SlowSession(UUID owner, long endTick, double radius, double slowAmount, int resistanceLevel,
                    NamespacedKey slowKey, NamespacedKey boostKey) {
            this.owner = owner;
            this.endTick = endTick;
            this.radius = radius;
            this.slowAmount = slowAmount;
            this.resistanceLevel = resistanceLevel;
            this.slowKey = slowKey;
            this.boostKey = boostKey;
        }
    }

    private final AbsoluteVoidClock plugin;
    private final Map<UUID, Map<Ability, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Frozen> frozen = new HashMap<>();
    private final Map<UUID, SlowSession> slowSessions = new HashMap<>();
    private BukkitTask task;
    private long tick;

    public AbilityManager(AbsoluteVoidClock plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Map.Entry<UUID, Frozen> entry : new ArrayList<>(frozen.entrySet())) {
            Player victim = Bukkit.getPlayer(entry.getKey());
            if (victim != null) {
                releaseFrozen(victim, entry.getValue());
            }
        }
        frozen.clear();
        for (SlowSession session : new ArrayList<>(slowSessions.values())) {
            endSlow(session, Bukkit.getPlayer(session.owner));
        }
        slowSessions.clear();
    }

    // ------------------------------------------------------------------ entry points

    /** Right-click with the clock. */
    public void use(Player player, Ability ability) {
        switch (ability) {
            case FREEZE_STRIKE -> player.sendActionBar(
                    msg("Freeze Strike: hit a player with the clock to freeze them.", NamedTextColor.AQUA));
            case TIME_SLOW -> startSlow(player);
            case VOID_BEAM -> fireBeam(player);
        }
    }

    /** A player holding the clock (Freeze Strike selected) hit another player. */
    public void onFreezeHit(Player attacker, Player victim) {
        if (frozen.containsKey(victim.getUniqueId())) {
            return;
        }
        if (!ready(attacker, Ability.FREEZE_STRIKE)) {
            return;
        }
        double seconds = clamp(num(Ability.FREEZE_STRIKE, "freeze_seconds"), 0.5, 10);
        startCooldown(attacker, Ability.FREEZE_STRIKE, num(Ability.FREEZE_STRIKE, "cooldown_seconds"));

        int ticks = (int) Math.round(seconds * 20);
        boolean addedSlowFall = false;
        if (!victim.hasPotionEffect(PotionEffectType.SLOW_FALLING)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ticks + 10, 0, false, false, false));
            addedSlowFall = true;
        }
        Frozen state = new Frozen(victim.getLocation().clone(), tick + ticks, victim.hasGravity(), addedSlowFall);
        frozen.put(victim.getUniqueId(), state);
        victim.setGravity(false);
        victim.setVelocity(new Vector(0, 0, 0));

        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.5f);
        victim.sendActionBar(msg("Frozen by the void!", NamedTextColor.AQUA));
        attacker.sendActionBar(msg("Target frozen for " + fmt(seconds) + "s - hit them!", NamedTextColor.AQUA));
    }

    public boolean isFrozen(UUID id) {
        return frozen.containsKey(id);
    }

    /** The spot a frozen player is pinned to, or null. */
    public Location frozenLock(UUID id) {
        Frozen state = frozen.get(id);
        return state == null ? null : state.lock;
    }

    /** Ends everything tied to this player (death, quit). */
    public void clearPlayer(Player player) {
        Frozen state = frozen.remove(player.getUniqueId());
        if (state != null) {
            releaseFrozen(player, state);
        }
        SlowSession session = slowSessions.remove(player.getUniqueId());
        if (session != null) {
            endSlow(session, player);
        }
    }

    public void resetCooldowns(UUID id) {
        cooldowns.remove(id);
    }

    // ------------------------------------------------------------------ main loop

    private void tick() {
        tick++;

        Iterator<Map.Entry<UUID, Frozen>> frozenIt = frozen.entrySet().iterator();
        while (frozenIt.hasNext()) {
            Map.Entry<UUID, Frozen> entry = frozenIt.next();
            Frozen state = entry.getValue();
            Player victim = Bukkit.getPlayer(entry.getKey());
            if (victim == null || !victim.isOnline() || victim.isDead() || tick >= state.endTick) {
                frozenIt.remove();
                if (victim != null) {
                    releaseFrozen(victim, state);
                }
                continue;
            }
            victim.setVelocity(new Vector(0, 0, 0));
            victim.setFallDistance(0f);
            if (tick % 5 == 0) {
                victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0),
                        8, 0.4, 0.8, 0.4, 0.01);
            }
        }

        if (tick % 5 == 0) {
            Iterator<SlowSession> slowIt = slowSessions.values().iterator();
            while (slowIt.hasNext()) {
                SlowSession session = slowIt.next();
                Player owner = Bukkit.getPlayer(session.owner);
                if (owner == null || !owner.isOnline() || owner.isDead() || tick >= session.endTick) {
                    slowIt.remove();
                    endSlow(session, owner);
                    continue;
                }
                updateSlow(session, owner);
            }
        }
    }

    // ------------------------------------------------------------------ Freeze Strike

    private void releaseFrozen(Player victim, Frozen state) {
        victim.setGravity(state.hadGravity);
        if (state.addedSlowFall) {
            victim.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
        victim.playSound(victim.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.5f);
        victim.sendActionBar(msg("You can move again.", NamedTextColor.GRAY));
    }

    // ------------------------------------------------------------------ Time Slow

    private void startSlow(Player player) {
        if (slowSessions.containsKey(player.getUniqueId())) {
            player.sendActionBar(msg("Time Slow is already active.", NamedTextColor.RED));
            return;
        }
        if (!ready(player, Ability.TIME_SLOW)) {
            return;
        }
        double durationSeconds = clamp(num(Ability.TIME_SLOW, "duration_seconds"), 1, 120);
        double radius = clamp(num(Ability.TIME_SLOW, "radius"), 1, 32);
        double tickRate = clamp(num(Ability.TIME_SLOW, "tick_rate"), 1, 20);
        double boostPercent = clamp(num(Ability.TIME_SLOW, "attack_speed_percent"), 0, 300);
        int resistance = (int) Math.round(clamp(num(Ability.TIME_SLOW, "resistance_level"), 0, 5));
        int durationTicks = (int) Math.round(durationSeconds * 20);

        startCooldown(player, Ability.TIME_SLOW, num(Ability.TIME_SLOW, "cooldown_seconds"));

        UUID id = player.getUniqueId();
        SlowSession session = new SlowSession(
                id,
                tick + durationTicks,
                radius,
                (tickRate / 20.0) - 1.0,
                resistance,
                new NamespacedKey(plugin, "slow_" + id),
                new NamespacedKey(plugin, "boost_" + id));
        slowSessions.put(id, session);

        if (boostPercent > 0) {
            addModifier(player, Attribute.ATTACK_SPEED, session.boostKey, boostPercent / 100.0);
        }
        if (resistance > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, resistance - 1,
                    false, true, true));
        }
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 0.6f);
        player.sendActionBar(msg("Time Slow!", NamedTextColor.LIGHT_PURPLE));
        updateSlow(session, player);
    }

    private void updateSlow(SlowSession session, Player owner) {
        Location center = owner.getLocation();
        double radiusSq = session.radius * session.radius;
        Set<UUID> present = new HashSet<>();

        for (Entity entity : owner.getNearbyEntities(session.radius, session.radius, session.radius)) {
            if (!(entity instanceof LivingEntity)) {
                continue;
            }
            if (entity instanceof ArmorStand) {
                continue;
            }
            if (entity instanceof Player other && other.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            if (entity.getLocation().distanceSquared(center) > radiusSq) {
                continue;
            }
            LivingEntity living = (LivingEntity) entity;
            present.add(living.getUniqueId());
            if (!session.affected.containsKey(living.getUniqueId())) {
                addModifier(living, Attribute.MOVEMENT_SPEED, session.slowKey, session.slowAmount);
                addModifier(living, Attribute.ATTACK_SPEED, session.slowKey, session.slowAmount);
                session.affected.put(living.getUniqueId(), living);
            }
        }

        Iterator<Map.Entry<UUID, LivingEntity>> it = session.affected.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LivingEntity> entry = it.next();
            if (!present.contains(entry.getKey())) {
                releaseSlow(entry.getValue(), session);
                it.remove();
            }
        }

        drawRing(owner, session.radius);
        long secondsLeft = (session.endTick - tick + 19) / 20;
        owner.sendActionBar(msg("Time Slow: " + secondsLeft + "s", NamedTextColor.LIGHT_PURPLE));
    }

    private void endSlow(SlowSession session, Player owner) {
        for (LivingEntity living : session.affected.values()) {
            releaseSlow(living, session);
        }
        session.affected.clear();
        if (owner == null) {
            return;
        }
        AttributeInstance attack = owner.getAttribute(Attribute.ATTACK_SPEED);
        if (attack != null) {
            removeModifier(attack, session.boostKey);
        }
        if (session.resistanceLevel > 0) {
            PotionEffect current = owner.getPotionEffect(PotionEffectType.RESISTANCE);
            if (current != null && current.getAmplifier() == session.resistanceLevel - 1) {
                owner.removePotionEffect(PotionEffectType.RESISTANCE);
            }
        }
        owner.sendActionBar(msg("Time Slow ended.", NamedTextColor.GRAY));
        owner.playSound(owner.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
    }

    private void releaseSlow(LivingEntity living, SlowSession session) {
        if (!living.isValid()) {
            return;
        }
        AttributeInstance speed = living.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            removeModifier(speed, session.slowKey);
        }
        AttributeInstance attack = living.getAttribute(Attribute.ATTACK_SPEED);
        if (attack != null) {
            removeModifier(attack, session.slowKey);
        }
    }

    private void drawRing(Player owner, double radius) {
        World world = owner.getWorld();
        Location center = owner.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(120, 40, 200), 1.2f);
        int points = (int) Math.min(96, Math.max(16, radius * 6));
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.15, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
        }
    }

    // ------------------------------------------------------------------ Void Beam

    private void fireBeam(Player player) {
        if (!ready(player, Ability.VOID_BEAM)) {
            return;
        }
        double damage = clamp(num(Ability.VOID_BEAM, "damage_hearts"), 0.5, 20) * 2.0;
        double range = clamp(num(Ability.VOID_BEAM, "range"), 5, 100);
        boolean ignoreArmor = plugin.getConfig().getBoolean("void_beam.ignore_armor");
        startCooldown(player, Ability.VOID_BEAM, num(Ability.VOID_BEAM, "cooldown_seconds"));

        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();

        RayTraceResult result = world.rayTrace(eye, direction, range, FluidCollisionMode.NEVER, true, 0.4,
                entity -> canTarget(player, entity));

        Vector endPoint;
        LivingEntity hitEntity = null;
        if (result != null) {
            endPoint = result.getHitPosition();
            if (result.getHitEntity() instanceof LivingEntity living) {
                hitEntity = living;
            }
        } else {
            endPoint = eye.toVector().add(direction.clone().multiply(range));
        }
        double length = endPoint.distance(eye.toVector());

        Location start = eye.clone().add(0, -0.2, 0).add(direction.clone().multiply(0.6));
        drawBeam(world, start, direction, Math.max(0, length - 0.6));
        world.playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.5f);

        if (hitEntity != null) {
            damageTarget(hitEntity, player, damage, ignoreArmor);
            world.spawnParticle(Particle.END_ROD, hitEntity.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
            world.playSound(hitEntity.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
        }
    }

    private boolean canTarget(Player shooter, Entity entity) {
        if (entity.equals(shooter) || !(entity instanceof LivingEntity) || entity instanceof ArmorStand) {
            return false;
        }
        if (entity instanceof Player other && other.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return entity.isValid();
    }

    private void damageTarget(LivingEntity target, Player source, double amount, boolean ignoreArmor) {
        target.setNoDamageTicks(0);
        if (ignoreArmor) {
            DamageSource damageSource = DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(source)
                    .withDirectEntity(source)
                    .build();
            target.damage(amount, damageSource);
        } else {
            target.damage(amount, source);
        }
    }

    private void drawBeam(World world, Location from, Vector direction, double length) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(150, 60, 255), 1.4f);
        int index = 0;
        for (double d = 0; d <= length; d += 0.4) {
            Location point = from.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, dust);
            if (index % 3 == 0) {
                world.spawnParticle(Particle.END_ROD, point, 1, 0.02, 0.02, 0.02, 0);
            }
            index++;
        }
    }

    // ------------------------------------------------------------------ cooldowns and helpers

    private boolean ready(Player player, Ability ability) {
        long remaining = remainingMs(player.getUniqueId(), ability);
        if (remaining <= 0) {
            return true;
        }
        player.sendActionBar(msg(ability.displayName() + " on cooldown: "
                + String.format(Locale.ROOT, "%.1f", remaining / 1000.0) + "s", NamedTextColor.RED));
        return false;
    }

    private long remainingMs(UUID id, Ability ability) {
        Map<Ability, Long> map = cooldowns.get(id);
        if (map == null) {
            return 0;
        }
        Long end = map.get(ability);
        if (end == null) {
            return 0;
        }
        return Math.max(0, end - System.currentTimeMillis());
    }

    private void startCooldown(Player player, Ability ability, double seconds) {
        if (seconds <= 0) {
            return;
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), key -> new EnumMap<>(Ability.class))
                .put(ability, System.currentTimeMillis() + (long) (seconds * 1000));
    }

    private double num(Ability ability, String key) {
        return plugin.getConfig().getDouble(ability.id() + "." + key);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String fmt(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static Component msg(String text, NamedTextColor color) {
        return Component.text(text, color);
    }

    private static void addModifier(LivingEntity entity, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        removeModifier(instance, key);
        instance.addTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private static void removeModifier(AttributeInstance instance, NamespacedKey key) {
        for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) {
            if (modifier.getKey().equals(key)) {
                instance.removeModifier(modifier);
            }
        }
    }
}
