package com.terracraft.geo.content.industry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Machine de l'industrie du carburant. Une fois par seconde : la pompe extrait du brut si elle est alimentée et posée
 * sur un gisement ; la raffinerie transforme 200 mB de brut en 140 mB d'essence et 60 mB de kérosène ; les liquides
 * produits sont poussés par les tuyaux vers les réservoirs (et le brut vers les raffineries).
 *
 * <p>Énergie : nombre de panneaux solaires qui voient le ciel en plein jour, reliés par des câbles (ou collés).
 */
public class MachineBlockEntity extends BlockEntity {
    /** Brut extrait par seconde et par point de richesse du gisement, si la pompe est alimentée. */
    static final int PUMP_RATE = 5;
    static final int REFINE_INPUT = 200;
    static final int REFINE_GASOLINE = 140;
    static final int REFINE_KEROSENE = 60;
    /** Débit maximal d'un tuyau par seconde et par liquide. */
    static final int PIPE_RATE = 1_000;
    private static final int SEARCH_LIMIT = 256;

    /** Charge maximale d'une batterie : 20 minutes d'un panneau (unités · seconde). */
    public static final int BATTERY_CAPACITY = 1_200;
    /** Énergie utile au plus par machine (une pompe ou une raffinerie ne tire pas plus de 2 unités). */
    static final int MAX_DRAW = 2;

    private final Map<FluidKind, Integer> fluids = new EnumMap<>(FluidKind.class);
    private int power;
    private int charge;
    private int ticks;

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(IndustryBlocks.MACHINE_ENTITY, pos, state);
    }

    public MachineKind kind() {
        return getBlockState().getBlock() instanceof MachineBlock machine ? machine.kind : MachineKind.FUEL_TANK;
    }

    public int amount(FluidKind fluid) {
        return fluids.getOrDefault(fluid, 0);
    }

    public int power() {
        return power;
    }

    public int charge() {
        return charge;
    }

    /** Ajoute (ou retire si négatif) du liquide ; renvoie la quantité réellement transférée. */
    public int add(FluidKind fluid, int amount) {
        int now = amount(fluid);
        int room = kind().capacity - now;
        int moved = amount >= 0 ? Math.min(amount, Math.max(0, room)) : -Math.min(-amount, now);
        if (moved != 0) {
            fluids.put(fluid, now + moved);
            setChanged();
        }
        return moved;
    }

    /** Ce liquide peut-il entrer ici ? Réservoir : un seul liquide à la fois ; raffinerie : seulement le brut. */
    public boolean accepts(FluidKind fluid) {
        return switch (kind()) {
            case FUEL_TANK -> fluids.entrySet().stream().allMatch(e -> e.getKey() == fluid || e.getValue() == 0)
                    && amount(fluid) < kind().capacity;
            case REFINERY -> fluid == FluidKind.CRUDE && amount(fluid) < kind().capacity;
            default -> false;
        };
    }

    /** Liquide contenu par un réservoir (null s'il est vide). */
    public FluidKind content() {
        return fluids.entrySet().stream().filter(e -> e.getValue() > 0).map(Map.Entry::getKey).findFirst().orElse(null);
    }

    void serverTick(ServerLevel level) {
        if (++ticks % 20 != 0) {
            return;
        }
        if (kind() == MachineKind.BATTERY) {
            // Le jour, les panneaux reliés rechargent la batterie (énergie en surplus, simplifiée).
            int panels = power(level, worldPosition);
            if (panels > 0 && charge < BATTERY_CAPACITY) {
                charge = Math.min(BATTERY_CAPACITY, charge + panels);
                setChanged();
            }
            return;
        }
        if (kind().powered) {
            // N'utilise l'énergie (et ne vide les batteries) que s'il y a du travail.
            boolean busy = switch (kind()) {
                case OIL_PUMP -> amount(FluidKind.CRUDE) < kind().capacity
                        && Oil.richness(worldPosition.getX(), worldPosition.getZ()) > 0;
                case REFINERY -> amount(FluidKind.CRUDE) >= REFINE_INPUT;
                default -> false;
            };
            power = energy(level, worldPosition, busy ? MAX_DRAW : 0);
        }
        switch (kind()) {
            case OIL_PUMP -> {
                int richness = Oil.richness(worldPosition.getX(), worldPosition.getZ());
                if (power > 0 && richness > 0 && level.dimension() == net.minecraft.world.level.Level.OVERWORLD) {
                    add(FluidKind.CRUDE, PUMP_RATE * richness * Math.min(power, 2));
                }
                push(level, FluidKind.CRUDE);
            }
            case REFINERY -> {
                if (power >= 2 && amount(FluidKind.CRUDE) >= REFINE_INPUT
                        && amount(FluidKind.GASOLINE) + REFINE_GASOLINE <= kind().capacity
                        && amount(FluidKind.KEROSENE) + REFINE_KEROSENE <= kind().capacity) {
                    add(FluidKind.CRUDE, -REFINE_INPUT);
                    add(FluidKind.GASOLINE, REFINE_GASOLINE);
                    add(FluidKind.KEROSENE, REFINE_KEROSENE);
                }
                push(level, FluidKind.GASOLINE);
                push(level, FluidKind.KEROSENE);
            }
            case FUEL_TANK -> {
                if (amount(FluidKind.CRUDE) > 0) {
                    push(level, FluidKind.CRUDE); // Un réservoir de brut alimente les raffineries reliées.
                }
            }
            default -> {
            }
        }
    }

    /** Pousse un liquide vers les machines reliées par des tuyaux qui l'acceptent (les plus proches d'abord). */
    private void push(ServerLevel level, FluidKind fluid) {
        int budget = Math.min(PIPE_RATE, amount(fluid));
        if (budget <= 0) {
            return;
        }
        for (MachineBlockEntity sink : connected(level, worldPosition, false)) {
            if (budget <= 0) {
                break;
            }
            if (sink == this || !sink.accepts(fluid) || kind() == MachineKind.FUEL_TANK && sink.kind() == MachineKind.FUEL_TANK) {
                continue; // Un réservoir ne se vide pas dans un autre (pas d'aller-retour sans fin).
            }
            int moved = sink.add(fluid, budget);
            add(fluid, -moved);
            budget -= moved;
        }
    }

    /**
     * Machines reliées à {@code start} : par des tuyaux ({@code electric} faux) ou des câbles. Les machines collées
     * comptent aussi. Parcours en largeur, limité à 256 blocs.
     */
    public static List<MachineBlockEntity> connected(ServerLevel level, BlockPos start, boolean electric) {
        List<MachineBlockEntity> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof ConnectorBlock connector && connector.electric == electric) {
                    queue.add(next);
                } else if (level.getBlockEntity(next) instanceof MachineBlockEntity machine) {
                    found.add(machine);
                }
            }
        }
        return found;
    }

    /**
     * Énergie d'une machine pour cette seconde : panneaux d'abord, puis batteries reliées pour combler le manque
     * (jusqu'à {@code need} unités). Les unités prises aux batteries sont consommées.
     */
    public static int energy(ServerLevel level, BlockPos start, int need) {
        int panels = power(level, start);
        int deficit = need - Math.min(need, panels);
        if (deficit > 0) {
            for (MachineBlockEntity battery : connected(level, start, true)) {
                if (deficit <= 0) {
                    break;
                }
                if (battery.kind() == MachineKind.BATTERY && battery.charge > 0) {
                    int taken = Math.min(deficit, battery.charge);
                    battery.charge -= taken;
                    battery.setChanged();
                    deficit -= taken;
                }
            }
        }
        return need - deficit;
    }

    /** Énergie disponible : panneaux solaires reliés (câbles) qui voient le ciel, en plein jour. */
    public static int power(ServerLevel level, BlockPos start) {
        if (!level.isBrightOutside()) {
            return 0;
        }
        int panels = 0;
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && seen.size() < SEARCH_LIMIT) {
            BlockPos pos = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!seen.add(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof ConnectorBlock connector && connector.electric) {
                    queue.add(next);
                } else if (state.getBlock() instanceof SolarPanelBlock
                        && level.getHeight(Heightmap.Types.MOTION_BLOCKING, next.getX(), next.getZ()) <= next.getY() + 1) {
                    panels++;
                }
            }
        }
        return panels;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (FluidKind fluid : FluidKind.values()) {
            output.putInt(fluid.name(), amount(fluid));
        }
        output.putInt("Power", power);
        output.putInt("Charge", charge);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fluids.clear();
        for (FluidKind fluid : FluidKind.values()) {
            int amount = input.getIntOr(fluid.name(), 0);
            if (amount > 0) {
                fluids.put(fluid, amount);
            }
        }
        power = input.getIntOr("Power", 0);
        charge = input.getIntOr("Charge", 0);
    }
}
