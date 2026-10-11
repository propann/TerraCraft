package com.terracraft.geo.content;

import com.terracraft.geo.Plans;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

/**
 * Engin spatial vu par la carte des étoiles et l'atelier de station : une fusée (entité) ou un vaisseau construit
 * en blocs. Les règles de route sont communes (voir {@link com.terracraft.geo.SpaceTravel}).
 */
public interface Craft {
    /** Identifiant envoyé à l'écran de la carte : id d'entité (fusée), -2 ou moins pour un vaisseau construit. */
    int craftId();

    String tierName();

    /** Destination choisie, ou -1. */
    byte target();

    void setTarget(byte to);

    int fuel();

    int maxFuel();

    /** Prêt à recevoir un cap : au sol, assemblé. */
    boolean isComplete();

    /** Ce joueur est-il aux commandes (assis dans la fusée, ou au siège de pilote) ? */
    boolean isPilot(ServerPlayer player);

    int costTo(byte to);

    /** Ce qui empêche d'aller vers {@code to}, ou null si le vol est possible. */
    @Nullable String problemFor(ServerPlayer player, byte to);

    /** Demande de décollage vers la destination choisie ; vrai si le compte à rebours démarre. */
    boolean requestLaunch(ServerPlayer player, boolean explain);

    /** Résumé lisible (atelier, messages). */
    Component status();

    boolean hasUpgrade(Plans.Plan plan);

    /** Cet engin peut-il recevoir ce plan ? (Vérifié avant de prendre les matériaux du joueur.) */
    default boolean accepts(Plans.Plan plan) {
        return true;
    }

    /** Installe une amélioration de l'atelier ; faux si cet engin ne peut pas la recevoir. */
    boolean installUpgrade(Plans.Plan plan);
}
