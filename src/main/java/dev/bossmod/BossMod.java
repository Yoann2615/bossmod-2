package dev.bossmod;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class BossMod implements ModInitializer {

    private static final String TAG_PREFIX = "bossmod_";
    private static final int COOLDOWN_TICKS = 20 * 30;
    private static final int PASSIVE_INTERVAL_TICKS = 20;
    private static final int PASSIVE_DURATION_TICKS = 440;

    /** Letzter Faehigkeits-Einsatz pro Spieler (Server-Tick). */
    private final Map<UUID, Integer> lastAbilityUse = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                assignBossIfMissing(handler.getPlayer(), server));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                lastAbilityUse.remove(handler.getPlayer().getUUID()));

        // Nach dem Tod wird ein neues Spieler-Objekt erzeugt, der Boss muss mitwandern.
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                bossOf(oldPlayer).ifPresent(boss -> newPlayer.addTag(TAG_PREFIX + boss.id())));

        ServerTickEvents.END_SERVER_TICK.register(this::tick);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("boss").executes(context -> showBoss(context.getSource())));
            dispatcher.register(Commands.literal("ability").executes(context -> useAbility(context.getSource())));
        });
    }

    private static Optional<Boss> bossOf(ServerPlayer player) {
        for (Boss boss : Boss.values()) {
            if (player.getTags().contains(TAG_PREFIX + boss.id())) {
                return Optional.of(boss);
            }
        }
        return Optional.empty();
    }

    private static void assignBossIfMissing(ServerPlayer player, MinecraftServer server) {
        if (bossOf(player).isPresent()) {
            return;
        }
        Boss[] bosses = Boss.values();
        Boss boss = bosses[ThreadLocalRandom.current().nextInt(bosses.length)];
        player.addTag(TAG_PREFIX + boss.id());

        server.getPlayerList().broadcastSystemMessage(
                Component.literal(player.getName().getString() + " ist jetzt ")
                        .append(Component.literal(boss.displayName()).withStyle(boss.color(), ChatFormatting.BOLD))
                        .append(Component.literal("!")),
                false);
        describe(player, boss);
    }

    private static void describe(ServerPlayer player, Boss boss) {
        player.sendSystemMessage(Component.literal("Dein Boss: ")
                .append(Component.literal(boss.displayName()).withStyle(boss.color(), ChatFormatting.BOLD)));
        player.sendSystemMessage(Component.literal("Passiv: " + boss.passiveText()).withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal("Faehigkeit (/ability, 30 s Abklingzeit): " + boss.abilityText())
                .withStyle(ChatFormatting.GRAY));
    }

    private void tick(MinecraftServer server) {
        int tickCount = server.getTickCount();
        boolean refreshPassives = tickCount % PASSIVE_INTERVAL_TICKS == 0;
        boolean cleanseWither = tickCount % 5 == 0;
        if (!refreshPassives && !cleanseWither) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<Boss> boss = bossOf(player);
            if (boss.isEmpty()) {
                continue;
            }
            if (refreshPassives) {
                for (Boss.Passive passive : boss.get().passives()) {
                    player.addEffect(new MobEffectInstance(passive.effect(), PASSIVE_DURATION_TICKS,
                            passive.amplifier(), true, false, true));
                }
            }
            if (cleanseWither && boss.get() == Boss.WITHER) {
                player.removeEffect(MobEffects.WITHER);
            }
        }
    }

    private int showBoss(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Boss> boss = bossOf(player);
        if (boss.isEmpty()) {
            source.sendFailure(Component.literal("Du hast noch keinen Boss."));
            return 0;
        }
        describe(player, boss.get());
        return 1;
    }

    private int useAbility(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Optional<Boss> boss = bossOf(player);
        if (boss.isEmpty()) {
            source.sendFailure(Component.literal("Du hast noch keinen Boss."));
            return 0;
        }

        int now = source.getServer().getTickCount();
        Integer last = lastAbilityUse.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_TICKS) {
            int secondsLeft = (COOLDOWN_TICKS - (now - last) + 19) / 20;
            source.sendFailure(Component.literal("Abklingzeit: noch " + secondsLeft + " s"));
            return 0;
        }

        ServerLevel level = (ServerLevel) player.level();
        if (!boss.get().useAbility(player, level)) {
            source.sendFailure(Component.literal("Die Faehigkeit konnte hier nicht ausgeloest werden."));
            return 0;
        }
        lastAbilityUse.put(player.getUUID(), now);
        return 1;
    }
}
