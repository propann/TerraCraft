package com.terracraft.geo;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Métiers : chaque joueur choisit une spécialité qui renforce un style de jeu. Premier choix libre,
 * puis un changement toutes les 24 heures. Les bonus sont lus par les systèmes concernés
 * (véhicules, armes, jetpack, avion, attributs du joueur).
 */
public final class Jobs {
    public enum Job {
        MECANICIEN("Mécanicien", "−25 % d'essence pour les voitures, camions et motos"),
        ECLAIREUR("Éclaireur", "+8 % de vitesse de marche"),
        RECUPERATEUR("Récupérateur", "Meilleur butin dans les coffres (+1,5 chance)"),
        COMBATTANT("Combattant", "+15 % de dégâts des armes à feu"),
        PILOTE("Pilote", "−25 % de carburant pour l'avion et le jetpack");

        public final String label;
        public final String bonus;

        Job(String label, String bonus) {
            this.label = label;
            this.bonus = bonus;
        }

        static Job parse(String name) {
            for (Job job : values()) {
                if (job.name().equalsIgnoreCase(name) || job.label.equalsIgnoreCase(name)) {
                    return job;
                }
            }
            return null;
        }
    }

    private static final long CHANGE_DELAY_MS = 24L * 3600 * 1000;

    private Jobs() {
    }

    /** Métier du joueur, ou null s'il n'en a pas encore choisi. */
    /** Compétence qui mesure la réputation d'un métier (offres réservées du comptoir). */
    public static Progression.Skill skillOf(Job job) {
        return switch (job) {
            case MECANICIEN -> Progression.Skill.MECHANICS;
            case COMBATTANT -> Progression.Skill.COMBAT;
            case PILOTE -> Progression.Skill.SPACE;
            case ECLAIREUR, RECUPERATEUR -> Progression.Skill.EXPLORATION;
        };
    }

    public static Job of(ServerPlayer player) {
        return Job.parse(Progression.get().jobName(player));
    }

    public static boolean is(ServerPlayer player, Job job) {
        return of(player) == job;
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("metier")
                .executes(c -> show(c.getSource().getPlayerOrException()))
                .then(Commands.literal("choisir").then(Commands.argument("metier", StringArgumentType.word())
                        .suggests((c, builder) -> {
                            for (Job job : Job.values()) {
                                builder.suggest(job.name().toLowerCase(java.util.Locale.ROOT));
                            }
                            return builder.buildFuture();
                        })
                        .executes(c -> choose(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "metier"))))));
    }

    private static int show(ServerPlayer player) {
        Job current = of(player);
        player.sendSystemMessage(Component.literal("✦ Métiers").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        player.sendSystemMessage(Component.literal(current == null ? "Tu n'as pas encore de métier : le premier choix est libre."
                : "Ton métier : " + current.label + " — " + current.bonus).withStyle(ChatFormatting.GRAY));
        for (Job job : Job.values()) {
            String command = "/metier choisir " + job.name().toLowerCase(java.util.Locale.ROOT);
            player.sendSystemMessage(Component.literal(job == current ? "✓ " : "[Choisir] ")
                    .withStyle(s -> s.withColor(job == current ? ChatFormatting.DARK_GREEN : ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.RunCommand(command)))
                    .append(Component.literal(job.label + " : " + job.bonus).withStyle(ChatFormatting.WHITE)));
        }
        return 1;
    }

    private static int choose(ServerPlayer player, String name) {
        Job job = Job.parse(name);
        if (job == null) {
            player.sendSystemMessage(Component.literal("Métier inconnu. /metier pour la liste.").withStyle(ChatFormatting.RED));
            return 0;
        }
        Job current = of(player);
        if (current == job) {
            player.sendSystemMessage(Component.literal("C'est déjà ton métier.").withStyle(ChatFormatting.GRAY));
            return 0;
        }
        long since = System.currentTimeMillis() - Progression.get().jobChangedAt(player);
        if (current != null && since < CHANGE_DELAY_MS) {
            long hours = (CHANGE_DELAY_MS - since + 3_599_999) / 3_600_000;
            player.sendSystemMessage(Component.literal("Tu pourras changer de métier dans " + hours + " h.").withStyle(ChatFormatting.RED));
            return 0;
        }
        Progression.get().setJob(player, job.name());
        if (Progression.get().stat(player, "job") == 0) {
            Progression.get().count(player, "job", 1, 0);
        }
        Progression.get().applyPerks(player);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
        player.sendSystemMessage(Component.literal("✦ Tu es maintenant " + job.label + " : " + job.bonus + ".").withStyle(ChatFormatting.GOLD));
        GeoMod.LOGGER.info("[METIER] {} devient {}", player.getName().getString(), job.label);
        return 1;
    }
}
