package com.draxer.dexrider.rider;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Efectos visuales y ayudas de combate compartidas por cartas, armas y finishers. */
public final class FX {
    private FX() {}

    /** Paleta de Dex. */
    public static final int MAGENTA = 0xFF2BD6;
    public static final int GOLD = 0xF2C230;
    public static final int RED = 0xD4202C;
    public static final int WHITE = 0xF4F4FA;
    public static final int VOLT = 0x7FE8FF;

    // ------------------------------------------------------------------ particulas

    public static void dust(ServerLevel level, int color, float size, Vec3 p, int count, double spread) {
        level.sendParticles(new DustParticleOptions(color, size), p.x, p.y, p.z, count, spread, spread, spread, 0);
    }

    public static void particle(ServerLevel level, ParticleOptions type, Vec3 p, int count, double spread, double speed) {
        level.sendParticles(type, p.x, p.y, p.z, count, spread, spread, spread, speed);
    }

    /** Anillo vertical de particulas alrededor del jugador (transformacion). */
    public static void ring(ServerLevel level, Entity around, int color, double radius, int layers) {
        DustParticleOptions dust = new DustParticleOptions(color, 1.4F);
        for (int layer = 0; layer < layers; layer++) {
            double y = around.getY() + layer * 0.25;
            for (int i = 0; i < 24; i++) {
                double angle = (Math.PI * 2 / 24) * i + layer * 0.3;
                level.sendParticles(dust,
                        around.getX() + Math.cos(angle) * radius, y, around.getZ() + Math.sin(angle) * radius,
                        1, 0, 0, 0, 0);
            }
        }
    }

    /** Linea de particulas entre dos puntos. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, int color, float size, double step) {
        DustParticleOptions dust = new DustParticleOptions(color, size);
        Vec3 delta = to.subtract(from);
        double len = delta.length();
        if (len < 1.0E-3) return;
        Vec3 dir = delta.scale(1.0 / len);
        for (double d = 0; d <= len; d += step) {
            Vec3 p = from.add(dir.scale(d));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** Contorno de una carta holografica (0.9 x 1.3) perpendicular a dir. */
    public static void card(ServerLevel level, Vec3 center, Vec3 dir, int edgeColor) {
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = right.cross(dir).normalize();

        DustParticleOptions edge = new DustParticleOptions(edgeColor, 0.9F);
        double w = 0.45, h = 0.65;
        int steps = 6;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = -w + 2 * w * t;
            double y = -h + 2 * h * t;
            spawn(level, edge, center.add(right.scale(x)).add(up.scale(h)));
            spawn(level, edge, center.add(right.scale(x)).add(up.scale(-h)));
            spawn(level, edge, center.add(right.scale(w)).add(up.scale(y)));
            spawn(level, edge, center.add(right.scale(-w)).add(up.scale(y)));
        }
        dust(level, GOLD, 1.2F, center, 3, 0.1);
    }

    /** Fila de cartas holograficas entre dos puntos (el "pasillo" de un Final Attack Ride). */
    public static List<Vec3> cardPath(ServerLevel level, Vec3 from, Vec3 to, int count, int color) {
        List<Vec3> cards = new ArrayList<>();
        Vec3 dir = to.subtract(from).normalize();
        for (int i = 1; i <= count; i++) {
            Vec3 p = from.lerp(to, i / (double) (count + 1));
            cards.add(p);
            card(level, p, dir, color);
        }
        return cards;
    }

    private static void spawn(ServerLevel level, DustParticleOptions dust, Vec3 p) {
        level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
    }

    // ------------------------------------------------------------------ combate

    /**
     * Dano que ignora los frames de invulnerabilidad (para golpes multiples de un finisher).
     * Pasa por LivingIncomingDamageEvent, asi que el escalado del mazo se aplica igual.
     */
    public static void hit(ServerLevel level, ServerPlayer attacker, LivingEntity target, float damage) {
        target.invulnerableTime = 0;
        target.hurtServer(level, attacker.damageSources().playerAttack(attacker), damage);
    }

    /** Empuja al objetivo alejandolo de un punto. */
    public static void push(LivingEntity target, Vec3 from, double strength, double lift) {
        Vec3 d = target.position().subtract(from);
        Vec3 h = new Vec3(d.x, 0, d.z);
        h = h.lengthSqr() < 1.0E-4 ? Vec3.ZERO : h.normalize();
        target.setDeltaMovement(target.getDeltaMovement().add(h.x * strength, lift, h.z * strength));
        target.hurtMarked = true;
    }

    /** Punto donde termina un disparo (choca con bloques). */
    public static Vec3 rayEnd(ServerLevel level, ServerPlayer player, Vec3 start, Vec3 dir, double range) {
        Vec3 end = start.add(dir.scale(range));
        HitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    /** Entidades vivas cuya caja cruza el segmento start-end (inflada en 'width'), ordenadas por distancia. */
    public static List<LivingEntity> alongLine(ServerLevel level, ServerPlayer player, Vec3 start, Vec3 end, double width) {
        AABB box = new AABB(start, end).inflate(width + 1.0);
        List<LivingEntity> hits = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (e.getBoundingBox().inflate(width).clip(start, end).isPresent()) hits.add(e);
        }
        hits.sort(Comparator.comparingDouble(e -> e.distanceToSqr(start)));
        return hits;
    }

    /**
     * Disparo instantaneo desde los ojos del jugador. Dibuja el rayo y devuelve a quien golpea.
     * pierce = atraviesa a todos; si no, solo el primero.
     */
    public static List<LivingEntity> beam(ServerLevel level, ServerPlayer player, Vec3 dir, double range,
                                          float damage, int color, float size, boolean pierce) {
        Vec3 start = player.getEyePosition().add(0, -0.15, 0);
        Vec3 end = rayEnd(level, player, start, dir, range);
        List<LivingEntity> hits = alongLine(level, player, start, end, 0.3);
        if (!pierce && !hits.isEmpty()) {
            LivingEntity first = hits.get(0);
            hits = List.of(first);
            end = first.position().add(0, first.getBbHeight() * 0.5, 0);
        }
        line(level, start.add(dir.scale(0.8)), end, color, size, 0.35);
        for (LivingEntity e : hits) hit(level, player, e, damage);
        return hits;
    }

    /** Gira un vector horizontalmente 'degrees' grados (para disparos en abanico). */
    public static Vec3 yaw(Vec3 v, double degrees) {
        double r = Math.toRadians(degrees);
        double cos = Math.cos(r), sin = Math.sin(r);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos).normalize();
    }

    /** Objetivo mas centrado y cercano delante del jugador (o null). */
    public static LivingEntity findTarget(ServerLevel level, ServerPlayer player, double range, double minDot) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(range),
                e -> e != player && e.isAlive())) {
            Vec3 to = center(e).subtract(eye);
            double dist = to.length();
            if (dist > range) continue;
            double dot = to.normalize().dot(look);
            if (dot < minDot || !player.hasLineOfSight(e)) continue;
            double score = dist * (2.0 - dot);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        return best;
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }
}
