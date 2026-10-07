package com.terracraft.geo;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.List;

/**
 * Entrée dans le monde : le joueur tombe lentement du ciel au-dessus de la ville choisie,
 * un titre « Jour 1 » s'affiche, et il reçoit un kit de départ avec le carnet de survie.
 */
final class Arrival {
    /** Hauteur de largage au-dessus du sol, en blocs. */
    static final int DROP_HEIGHT = 48;

    private Arrival() {
    }

    static void welcome(ServerPlayer player, String place, boolean firstTime) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 50, 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 3, 0, false, false, false));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(30, 100, 40));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(firstTime ? "Jour 1" : place)
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(firstTime
                ? place + " — la ville s'est tue. La nature a repris ses droits."
                : "Nouveau point de chute").withStyle(ChatFormatting.GRAY)));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELYTRA_FLYING,
                SoundSource.PLAYERS, 0.6f, 0.8f);
        if (firstTime) {
            give(player, new ItemStack(Items.BREAD, 4));
            give(player, new ItemStack(Items.TORCH, 8));
            give(player, new ItemStack(Items.STONE_SWORD));
            give(player, new ItemStack(Items.COMPASS));
            give(player, guide());
        }
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.spawnAtLocation(player.level(), stack);
        }
    }

    /** Carnet de survie : ce qu'il faut savoir pour jouer, sans quitter le jeu. */
    static ItemStack guide() {
        List<String> pages = List.of(
                "CARNET DE SURVIE\n\nLe monde a redémarré sans humains. Les villes sont réelles : rues, ponts et immeubles "
                        + "suivent la vraie carte.\n\nLes ruines cachent du butin… et des monstres.",
                "LE DANGER\n\n• Zombies dans les immeubles et générateurs de monstres.\n• Caves à monstres sous les parcs.\n"
                        + "• La nuit, aucune lumière en ville.\n\nAllume tes torches.",
                "LE BUTIN\n\n• Coffres dans les immeubles.\n• Bunkers enterrés : cherche une trappe en bois au sol, "
                        + "dans un terrain vague. Armes et pièces y sont rares mais précieuses.",
                "VÉHICULES\n\n1. Pose un châssis.\n2. Clic droit : 4 roues, moteur, radiateur, batterie.\n"
                        + "3. Turbo en option.\n4. Bidon d'essence.\n5. Clic droit pour monter, ZQSD.\nAccroupi + clic : état.",
                "RECETTES\n\nRoue : algues séchées + fer\nMoteur : fer, piston, redstone\nRadiateur : cuivre, barreaux\n"
                        + "Batterie : cuivre, redstone, fer\nTurbo : fer, cuivre, blaze\nEssence : fer + 2 charbons",
                "ARMES\n\nPistolet, fusil, fusil à pompe.\nMunitions ×8 : cuivre + poudre à canon.\n\n"
                        + "Châssis voiture : 5 fer en U.\nChâssis camion : 8 fer.",
                "L'ESPACE\n\nDepuis la Terre, la fusée ne va qu'en orbite. Charge un KIT DE STATION ORBITALE (clic droit sur la fusée) : "
                        + "au premier vol, ta station se déploie (salle de travail, tunnels, quai). La Lune et Mars partent de ce quai.",
                "FUSÉES\n\n1 réservoir : légère, 8 doses, 1 charge.\n2 : moyenne, 12 doses.\n4 : lourde, 20 doses, 4 charges.\n"
                        + "CARTE DES ÉTOILES : Maj + clic droit sur la fusée (ou menu O) : coûts, obstacles, cap et décollage.",
                "PLANS DE FUSÉE\n\nSe débloquent en explorant (/plans). Atelier de station : clic droit pour installer "
                        + "réservoir étendu, moteur ionique, soute ou navigation martienne (obligatoire pour Mars).",
                "LUNE ET MARS\n\nDepuis ta station : charge un KIT DE BASE LUNAIRE (ou martienne) et un ROVER. "
                        + "À l'atterrissage, la base se déploie (aire, sas, salle de vie) et le rover est déposé. Maj + clic gauche : remballer le rover.",
                "SOUS LA LUNE\n\nSous la croûte : cavernes géantes, cristaux, donjons enfouis. Des puits marqués de "
                        + "quatre piliers lumineux mènent aux SANCTUAIRES : pyramide, gardiens, artefacts extraterrestres.",
                "DANGERS LUNAIRES\n\nPLUIE DE MICROMÉTÉORITES : annoncée 30 s avant, elle dure une minute. Mets-toi sous un toit "
                        + "(base, module, grotte, cabine de fusée). Les impacts laissent parfois des fragments.\nLa NUIT LUNAIRE, les rôdeurs sont plus rapides et plus forts.",
                "STATIONS\n\nBalise de station : tes fusées s'y posent.\nKit de module : clic droit sur le sol "
                        + "= module pressurisé 7×5×7 avec oxygène. Raccorde-les par les portes.\n/station : tes stations.",
                "FUSÉE : RECETTES\n\nCoque : fer + blocs de cuivre\nMoteur : fer, bloc de redstone, haut fourneau\n"
                        + "Réservoir : cuivre + seau\nCône, ailerons : fer\nCarburant : essence + bloc de charbon + poudre",
                "LA LUNE\n\nGravité 1/6. Pas d'air !\nTouche J : combinaison spatiale.\n• Casque (fer + verre) : obligatoire\n"
                        + "• Combinaison (laine, cuivre, fer) : −25 % d'O₂\n• Bottes magnétiques (fer, redstone)\n"
                        + "• 2 réserves de bouteilles, branchées seules à 50 %.",
                "AVION\n\nKit : ailerons, moteur, blocs de fer, batterie, roues.\nPose-le, remplis-le de bidons (clic droit).\n\n"
                        + "Z/S : gaz · Q/D : tourner\nEspace : monter · Ctrl : descendre\nTrop lent = décrochage !",
                "JETPACK\n\nModule dorsal de la combinaison (touche J).\nFer, batterie, redstone, 2 bidons.\n\n"
                        + "Maintiens SAUT en l'air pour monter.\n30 s de poussée ; clic droit avec un bidon d'essence pour le recharger.",
                "TOUCHES\n\nO : menu TerraCraft\nJ : combinaison spatiale\nV : coffre du véhicule\nK : fiche de personnage\nM : carte et claims\n"
                        + "' : menu des claims\n\n/tuto : premiers pas\n/terracraft ou : position réelle");
        List<Filterable<Component>> content = pages.stream().map(p -> Filterable.passThrough((Component) Component.literal(p))).toList();
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough("Carnet de survie"), "TerraCraft", 0, content, true));
        return book;
    }
}
