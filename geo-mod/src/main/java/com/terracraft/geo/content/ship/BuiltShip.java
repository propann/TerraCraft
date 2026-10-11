package com.terracraft.geo.content.ship;

import com.terracraft.geo.Plans;
import com.terracraft.geo.Space;
import com.terracraft.geo.SpaceTravel;
import com.terracraft.geo.StarMap;
import com.terracraft.geo.content.Craft;
import com.terracraft.geo.content.Seat;
import com.terracraft.geo.content.ShipBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vaisseau construit posé : une construction de blocs de vaisseau autour d'une console de navigation, repérée à la
 * demande (rien n'est stocké tant qu'il est au sol). Il suit les mêmes règles que les fusées : routes, navigation
 * martienne, carburant ; ses améliorations (moteur ionique, navigation martienne) sont gravées dans sa console.
 */
public final class BuiltShip implements Craft {
    /** Caps choisis à la console, par console (non sauvegardés : on rechoisit après un redémarrage). */
    private static final Map<GlobalPos, Byte> TARGETS = new ConcurrentHashMap<>();
    /** Identifiants envoyés à l'écran de la carte, à partir de -2 (-1 veut dire « l'engin le plus proche »). */
    private static final Map<GlobalPos, Integer> IDS = new ConcurrentHashMap<>();
    private static final Map<Integer, GlobalPos> CONSOLES = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final BlockPos console;
    private final ShipStructure.Scan scan;

    private BuiltShip(ServerLevel level, BlockPos console, ShipStructure.Scan scan) {
        this.level = level;
        this.console = console;
        this.scan = scan;
    }

    /** Vaisseau dont fait partie ce bloc, ou null s'il n'a pas de console de navigation. */
    public static @Nullable BuiltShip at(ServerLevel level, BlockPos pos) {
        ShipStructure.Scan scan = ShipStructure.scan(level, pos);
        if (scan.consoles().isEmpty()) {
            return null;
        }
        BlockPos console = level.getBlockState(pos).is(ShipBlocks.NAV_CONSOLE) ? pos.immutable() : scan.consoles().getFirst();
        return new BuiltShip(level, console, scan);
    }

    /** Vaisseau dont le joueur occupe le siège de pilote. */
    public static @Nullable BuiltShip piloted(ServerPlayer player) {
        if (player.getVehicle() instanceof Seat seat && player.level() instanceof ServerLevel level
                && level.getBlockState(seat.blockPosition()).is(ShipBlocks.PILOT_SEAT)) {
            return at(level, seat.blockPosition());
        }
        return null;
    }

    public static @Nullable BuiltShip byId(MinecraftServer server, int id) {
        GlobalPos pos = CONSOLES.get(id);
        ServerLevel level = pos == null ? null : server.getLevel(pos.dimension());
        return level == null || !level.getBlockState(pos.pos()).is(ShipBlocks.NAV_CONSOLE) ? null : at(level, pos.pos());
    }

    /** Clic droit sur une console : carte des étoiles du vaisseau (ou ce qui lui manque). */
    public static void openConsole(ServerPlayer player, BlockPos pos) {
        BuiltShip ship = at((ServerLevel) player.level(), pos);
        if (ship == null) {
            return;
        }
        String problem = ship.scan.problem();
        if (problem != null) {
            player.sendSystemMessage(Component.literal("✦ Vaisseau incomplet (" + ship.scan.blocks().size() + " blocs) : " + problem)
                    .withStyle(ChatFormatting.GOLD));
        }
        StarMap.open(player, ship);
    }

    private GlobalPos key() {
        return GlobalPos.of(level.dimension(), console);
    }

    @Override
    public int craftId() {
        return IDS.computeIfAbsent(key(), k -> {
            int id = -(CONSOLES.size() + 2);
            CONSOLES.put(id, k);
            return id;
        });
    }

    @Override
    public String tierName() {
        return "Vaisseau · " + scan.blocks().size() + " blocs · " + scan.thrusters().size() + " réacteur(s)";
    }

    @Override
    public byte target() {
        byte target = TARGETS.getOrDefault(key(), (byte) -1);
        return target < 0 || target == Space.id(level) ? Space.defaultDestination(level) : target;
    }

    @Override
    public void setTarget(byte to) {
        TARGETS.put(key(), to);
    }

    @Override
    public int fuel() {
        return scan.fuel();
    }

    @Override
    public int maxFuel() {
        return scan.maxFuel();
    }

    @Override
    public boolean isComplete() {
        return scan.problem() == null;
    }

    @Override
    public boolean isPilot(ServerPlayer player) {
        return player.getVehicle() instanceof Seat seat && scan.seats().contains(seat.blockPosition());
    }

    @Override
    public int costTo(byte to) {
        int cost = Space.travelCost(Space.id(level), to);
        return hasUpgrade(Plans.Plan.ION) ? Math.max(1, cost - 1) : cost;
    }

    @Override
    public @Nullable String problemFor(ServerPlayer player, byte to) {
        if (to == Space.id(level)) {
            return "Tu y es.";
        }
        String structure = scan.problem();
        if (structure != null) {
            return structure;
        }
        String route = SpaceTravel.routeProblem(player, level, console, to);
        if (route != null) {
            return route;
        }
        if (SpaceTravel.needsMarsNavigation(player, to, hasUpgrade(Plans.Plan.MARS_NAV))) {
            return "Installe la navigation martienne dans la console, à l'atelier de station (/plans).";
        }
        int cost = costTo(to);
        if (cost > maxFuel()) {
            return "Trop loin pour tes réservoirs (" + maxFuel() + " doses) : fais escale ou ajoute un réservoir.";
        }
        if (fuel() < cost) {
            return "Carburant insuffisant : " + fuel() + "/" + cost + " doses (clic droit sur un réservoir avec du carburant de fusée).";
        }
        return null;
    }

    @Override
    public boolean requestLaunch(ServerPlayer player, boolean explain) {
        byte to = target();
        String problem = problemFor(player, to);
        if (problem == null && !isPilot(player)) {
            problem = "Assieds-toi au siège de pilote pour décoller.";
        }
        if (problem == null && !Space.hasLifeSupport(player)) {
            problem = "Décollage impossible : équipe le casque spatial et de l'oxygène (touche J).";
        }
        if (problem != null) {
            if (explain) {
                player.sendOverlayMessage(Component.literal(problem).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
            return false;
        }
        ShipCarrier.launch(level, scan, player, to, costTo(to));
        TARGETS.remove(key());
        return true;
    }

    @Override
    public Component status() {
        String problem = scan.problem();
        if (problem != null) {
            return Component.literal(problem).withStyle(ChatFormatting.GOLD);
        }
        int cost = costTo(target());
        return Component.literal(tierName() + " · carburant " + fuel() + "/" + maxFuel() + " · " + Space.name(target()) + " : "
                + cost + " dose(s)" + (fuel() >= cost ? " — prêt" : "")).withStyle(fuel() >= cost ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
    }

    private static @Nullable BooleanProperty property(Plans.Plan plan) {
        return switch (plan) {
            case ION -> ShipBlocks.NavConsoleBlock.ION;
            case MARS_NAV -> ShipBlocks.NavConsoleBlock.MARS_NAV;
            default -> null; // Réservoirs et soute : un vaisseau ajoute des réservoirs et des coffres.
        };
    }

    @Override
    public boolean hasUpgrade(Plans.Plan plan) {
        BooleanProperty property = property(plan);
        BlockState state = level.getBlockState(console);
        return property != null && state.is(ShipBlocks.NAV_CONSOLE) && state.getValue(property);
    }

    @Override
    public boolean accepts(Plans.Plan plan) {
        return property(plan) != null;
    }

    @Override
    public boolean installUpgrade(Plans.Plan plan) {
        BooleanProperty property = property(plan);
        BlockState state = level.getBlockState(console);
        if (property == null || !state.is(ShipBlocks.NAV_CONSOLE)) {
            return false;
        }
        level.setBlock(console, state.setValue(property, true), Block.UPDATE_ALL);
        return true;
    }

    public BlockPos console() {
        return console;
    }
}
