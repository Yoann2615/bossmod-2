package dev.bossmod;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Die vier zuteilbaren Bosse mit passiven Boni und je einer Spezialfaehigkeit. */
public enum Boss {

    WARDEN("warden", "Warden", ChatFormatting.DARK_AQUA,
            "Nachtsicht, Resistenz I",
            "Schallwelle: 10 Schaden und Dunkelheit im Umkreis von 8 Bloecken",
            List.of(new Passive(MobEffects.NIGHT_VISION, 0), new Passive(MobEffects.RESISTANCE, 0))) {
        @Override
        boolean useAbility(ServerPlayer player, ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM,
                    SoundSource.PLAYERS, 3.0F, 1.0F);
            level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getEyeY(), player.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
            for (LivingEntity target : nearbyTargets(player, level, 8.0)) {
                target.hurtServer(level, level.damageSources().sonicBoom(player), 10.0F);
                target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0));
            }
            return true;
        }
    },

    WITHER("wither", "Wither", ChatFormatting.DARK_GRAY,
            "Staerke I, immun gegen den Wither-Effekt",
            "Verfall: Wither II fuer 8 Sekunden im Umkreis von 6 Bloecken, du bekommst Absorption",
            List.of(new Passive(MobEffects.STRENGTH, 0))) {
        @Override
        boolean useAbility(ServerPlayer player, ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SHOOT,
                    SoundSource.PLAYERS, 2.0F, 0.8F);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(),
                    40, 2.0, 1.0, 2.0, 0.02);
            for (LivingEntity target : nearbyTargets(player, level, 6.0)) {
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 160, 1));
            }
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
            return true;
        }
    },

    ENDER_DRAGON("ender_dragon", "Enderdrache", ChatFormatting.LIGHT_PURPLE,
            "Langsames Fallen, Geschwindigkeit I",
            "Drachensprung: bis zu 10 Bloecke in Blickrichtung teleportieren",
            List.of(new Passive(MobEffects.SLOW_FALLING, 0), new Passive(MobEffects.SPEED, 0))) {
        @Override
        boolean useAbility(ServerPlayer player, ServerLevel level) {
            Vec3 look = player.getLookAngle();
            for (int distance = 10; distance >= 3; distance--) {
                boolean teleported = player.randomTeleport(
                        player.getX() + look.x * distance,
                        player.getY() + look.y * distance,
                        player.getZ() + look.z * distance,
                        true);
                if (teleported) {
                    level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP,
                            SoundSource.PLAYERS, 2.0F, 1.0F);
                    level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                            40, 0.5, 1.0, 0.5, 0.1);
                    return true;
                }
            }
            return false;
        }
    },

    ELDER_GUARDIAN("elder_guardian", "Elder Guardian", ChatFormatting.GOLD,
            "Wasseratmung, Delfingunst",
            "Fluch: Abbaumuedigkeit III und Langsamkeit II im Umkreis von 10 Bloecken",
            List.of(new Passive(MobEffects.WATER_BREATHING, 0), new Passive(MobEffects.DOLPHINS_GRACE, 0))) {
        @Override
        boolean useAbility(ServerPlayer player, ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE,
                    SoundSource.PLAYERS, 2.0F, 1.0F);
            for (LivingEntity target : nearbyTargets(player, level, 10.0)) {
                target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 400, 2));
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 1));
            }
            return true;
        }
    };

    /** Ein dauerhafter Effekt, der dem Boss-Spieler immer wieder erneuert wird. */
    public record Passive(Holder<MobEffect> effect, int amplifier) {
    }

    private final String id;
    private final String displayName;
    private final ChatFormatting color;
    private final String passiveText;
    private final String abilityText;
    private final List<Passive> passives;

    Boss(String id, String displayName, ChatFormatting color, String passiveText, String abilityText,
            List<Passive> passives) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.passiveText = passiveText;
        this.abilityText = abilityText;
        this.passives = passives;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public ChatFormatting color() {
        return color;
    }

    public String passiveText() {
        return passiveText;
    }

    public String abilityText() {
        return abilityText;
    }

    public List<Passive> passives() {
        return passives;
    }

    /**
     * Fuehrt die Spezialfaehigkeit aus.
     *
     * @return {@code true}, wenn sie ausgeloest wurde (dann startet die Abklingzeit)
     */
    abstract boolean useAbility(ServerPlayer player, ServerLevel level);

    private static List<LivingEntity> nearbyTargets(ServerPlayer player, ServerLevel level, double radius) {
        return level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                entity -> entity != player && entity.isAlive());
    }
}
