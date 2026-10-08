package com.terracraft.geo;

import com.terracraft.geo.content.ModContent;
import com.terracraft.geo.content.industry.FluidKind;
import com.terracraft.geo.content.industry.IndustryBlocks;
import com.terracraft.geo.content.industry.MachineBlock;
import com.terracraft.geo.content.industry.MachineBlockEntity;
import com.terracraft.geo.content.industry.MachineKind;
import com.terracraft.geo.content.industry.Oil;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Industrie du carburant, côté jeu : état des machines au clic droit, pompe à essence (bidon vide → bidon d'essence,
 * ou carburant de fusée avec Maj), détecteur de pétrole. Les machines elles-mêmes sont dans content.industry.
 */
final class Industry {
    static final int CAN = 1_000;

    private Industry() {
    }

    static void register() {
        IndustryBlocks.init();
        MachineBlock.interaction = Industry::use;
        UseItemCallback.EVENT.register((player, level, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(IndustryBlocks.OIL_DETECTOR) && player instanceof ServerPlayer server) {
                detect(server);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    private static InteractionResult use(ServerPlayer player, MachineBlockEntity machine, ItemStack stack, InteractionHand hand) {
        if (machine.kind() == MachineKind.FUEL_PUMP && stack.is(IndustryBlocks.EMPTY_FUEL_CAN)) {
            FluidKind fluid = player.isShiftKeyDown() ? FluidKind.KEROSENE : FluidKind.GASOLINE;
            if (!draw(player.level(), machine, fluid, CAN)) {
                player.sendOverlayMessage(Component.literal("Pas assez de " + fluid.label.toLowerCase() + " dans les réservoirs reliés (1 000 mB).")
                        .withStyle(ChatFormatting.RED));
                return InteractionResult.FAIL;
            }
            stack.consume(1, player);
            ItemStack full = new ItemStack(fluid == FluidKind.KEROSENE ? ModContent.ROCKET_FUEL : ModContent.FUEL_CAN);
            if (!player.getInventory().add(full)) {
                player.spawnAtLocation(player.level(), full);
            }
            player.level().playSound(null, machine.getBlockPos(), SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8f, 1f);
            GeoMod.LOGGER.info("[CARBURANT] {} remplit un bidon de {} à la pompe {}", player.getName().getString(), fluid.label,
                    machine.getBlockPos().toShortString());
            player.sendOverlayMessage(Component.literal(fluid == FluidKind.KEROSENE ? "Carburant de fusée prêt (1 dose)."
                    : "Bidon d'essence rempli. Maj + clic droit : carburant de fusée.").withStyle(ChatFormatting.GREEN));
            return InteractionResult.SUCCESS;
        }
        if (!stack.isEmpty()) {
            return InteractionResult.PASS; // Poser un bloc contre une machine reste possible.
        }
        sendScreen(player, machine);
        return InteractionResult.SUCCESS;
    }

    /** Écran à jauges : liquides (et contenance), énergie, charge, gisement, conseil. */
    static void sendScreen(ServerPlayer player, MachineBlockEntity machine) {
        var level = player.level();
        MachineKind kind = machine.kind();
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
        data.addProperty("label", kind.label);
        data.add("pos", posJson(machine.getBlockPos()));
        com.google.gson.JsonArray gauges = new com.google.gson.JsonArray();
        java.util.function.BiConsumer<FluidKind, Integer> gauge = (fluid, capacity) -> {
            com.google.gson.JsonObject g = new com.google.gson.JsonObject();
            g.addProperty("label", fluid.label);
            g.addProperty("fluid", fluid.name());
            g.addProperty("amount", machine.amount(fluid));
            g.addProperty("capacity", capacity);
            gauges.add(g);
        };
        switch (kind) {
            case OIL_PUMP -> gauge.accept(FluidKind.CRUDE, kind.capacity);
            case REFINERY -> {
                gauge.accept(FluidKind.CRUDE, kind.capacity);
                gauge.accept(FluidKind.GASOLINE, kind.capacity);
                gauge.accept(FluidKind.KEROSENE, kind.capacity);
            }
            case FUEL_TANK -> gauge.accept(machine.content() == null ? FluidKind.GASOLINE : machine.content(), kind.capacity);
            case FUEL_PUMP -> {
                List<MachineBlockEntity> tanks = MachineBlockEntity.connected(level, machine.getBlockPos(), false);
                for (FluidKind fluid : new FluidKind[]{FluidKind.GASOLINE, FluidKind.KEROSENE}) {
                    com.google.gson.JsonObject g = new com.google.gson.JsonObject();
                    g.addProperty("label", fluid.label + " (réservoirs reliés)");
                    g.addProperty("fluid", fluid.name());
                    g.addProperty("amount", tanks.stream().mapToInt(m -> m.amount(fluid)).sum());
                    g.addProperty("capacity", Math.max(1, tanks.stream().filter(m -> m.kind() == MachineKind.FUEL_TANK).count()) * 16_000);
                    gauges.add(g);
                }
            }
            case BATTERY -> {
            }
            case GENERATOR -> gauge.accept(FluidKind.GASOLINE, kind.capacity);
            case GREENHOUSE -> {
            }
        }
        data.add("gauges", gauges);
        if (kind == MachineKind.GREENHOUSE) {
            data.addProperty("growth", machine.growth());
            data.addProperty("growthMax", MachineBlockEntity.GROWTH_PER_HARVEST);
        }
        if (kind.powered) {
            data.addProperty("panels", MachineBlockEntity.power(level, machine.getBlockPos()));
            data.addProperty("power", machine.power());
        }
        if (kind == MachineKind.BATTERY) {
            data.addProperty("charge", machine.charge());
            data.addProperty("chargeMax", MachineBlockEntity.BATTERY_CAPACITY);
        }
        if (kind == MachineKind.OIL_PUMP) {
            data.addProperty("richness", Oil.richness(machine.getBlockPos().getX(), machine.getBlockPos().getZ()));
        }
        data.addProperty("hint", switch (kind) {
            case OIL_PUMP -> "Posée sur un gisement et alimentée (1 à 2 panneaux) : le brut part par les tuyaux.";
            case REFINERY -> "2 panneaux : 200 mB de brut → 140 essence + 60 kérosène par seconde.";
            case FUEL_TANK -> "Contient un seul liquide. Relié à une pompe à essence par tuyaux.";
            case FUEL_PUMP -> "Bidon vide en main : clic droit = essence, Maj + clic droit = carburant de fusée.";
            case BATTERY -> "Se charge le jour avec les panneaux reliés ; alimente les machines la nuit (câbles).";
            case GENERATOR -> "Brûle de l'essence (tuyau) quand panneaux et batteries ne suffisent pas : 5 mB par unité et par seconde.";
            case GREENHOUSE -> "Alimentée (câbles), elle récolte blé, pommes de terre, carottes, betteraves dans un coffre ou tonneau collé.";
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new MachinePayload(data.toString()));
    }

    private static com.google.gson.JsonArray posJson(net.minecraft.core.BlockPos pos) {
        com.google.gson.JsonArray array = new com.google.gson.JsonArray();
        array.add(pos.getX());
        array.add(pos.getY());
        array.add(pos.getZ());
        return array;
    }

    /** Rafraîchissement demandé par l'écran ouvert : seulement si la machine est à portée. */
    static void refresh(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        if (player.blockPosition().distSqr(pos) <= 64 && player.level().getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            sendScreen(player, machine);
        }
    }

    /** Prélève {@code amount} d'un liquide dans les réservoirs reliés par des tuyaux (tout ou rien). */
    static boolean draw(net.minecraft.server.level.ServerLevel level, MachineBlockEntity pump, FluidKind fluid, int amount) {
        List<MachineBlockEntity> tanks = MachineBlockEntity.connected(level, pump.getBlockPos(), false).stream()
                .filter(m -> m.kind() == MachineKind.FUEL_TANK || m.kind() == MachineKind.REFINERY).toList();
        int available = tanks.stream().mapToInt(m -> m.amount(fluid)).sum();
        if (available < amount) {
            return false;
        }
        int left = amount;
        for (MachineBlockEntity tank : tanks) {
            left += tank.add(fluid, -left);
            if (left <= 0) {
                break;
            }
        }
        return true;
    }

    static Component status(net.minecraft.server.level.ServerLevel level, MachineBlockEntity machine) {
        MachineKind kind = machine.kind();
        StringBuilder text = new StringBuilder(kind.label + " · ");
        switch (kind) {
            case OIL_PUMP -> {
                int richness = Oil.richness(machine.getBlockPos().getX(), machine.getBlockPos().getZ());
                text.append(richness == 0 ? "aucun gisement ici (détecteur de pétrole)" : "gisement " + switch (richness) {
                    case 1 -> "faible";
                    case 2 -> "moyen";
                    default -> "riche";
                }).append(" · brut ").append(machine.amount(FluidKind.CRUDE)).append("/").append(kind.capacity).append(" mB");
            }
            case REFINERY -> text.append("brut ").append(machine.amount(FluidKind.CRUDE)).append(" · essence ")
                    .append(machine.amount(FluidKind.GASOLINE)).append(" · kérosène ").append(machine.amount(FluidKind.KEROSENE))
                    .append(" mB (2 panneaux requis)");
            case FUEL_TANK -> {
                FluidKind content = machine.content();
                text.append(content == null ? "vide" : content.label + " " + machine.amount(content) + "/" + kind.capacity + " mB");
            }
            case GREENHOUSE -> text.append("croissance ").append(machine.growth()).append("/").append(MachineBlockEntity.GROWTH_PER_HARVEST);
            case GENERATOR -> text.append("essence ").append(machine.amount(FluidKind.GASOLINE)).append("/").append(kind.capacity).append(" mB");
            case BATTERY -> text.append("charge ").append(machine.charge()).append("/").append(MachineBlockEntity.BATTERY_CAPACITY);
            case FUEL_PUMP -> {
                List<MachineBlockEntity> tanks = MachineBlockEntity.connected(level, machine.getBlockPos(), false);
                text.append("essence ").append(tanks.stream().mapToInt(m -> m.amount(FluidKind.GASOLINE)).sum())
                        .append(" · kérosène ").append(tanks.stream().mapToInt(m -> m.amount(FluidKind.KEROSENE)).sum())
                        .append(" mB disponibles · bidon vide : clic droit (Maj : carburant de fusée)");
            }
        }
        if (kind.powered) {
            int power = MachineBlockEntity.power(level, machine.getBlockPos());
            text.append(" · énergie : ").append(power).append(power > 1 ? " panneaux" : " panneau").append(power == 0 ? " (nuit, ou câbles ?)" : "");
        }
        return Component.literal(text.toString()).withStyle(ChatFormatting.AQUA);
    }

    private static void detect(ServerPlayer player) {
        if (player.level().dimension() != Level.OVERWORLD) {
            player.sendOverlayMessage(Component.literal("Pas de pétrole ici : seulement sur Terre.").withStyle(ChatFormatting.GRAY));
            return;
        }
        int x = player.getBlockX();
        int z = player.getBlockZ();
        int here = Oil.richness(x, z);
        if (here > 0) {
            player.sendOverlayMessage(Component.literal("⛽ Gisement sous tes pieds (richesse " + here + "/3) : pose une pompe à pétrole ici.")
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        Oil.Deposit d = Oil.nearest(x, z);
        if (d == null) {
            player.sendOverlayMessage(Component.literal("Aucun gisement à moins de 200 blocs.").withStyle(ChatFormatting.GRAY));
            return;
        }
        int dx = d.x() - x;
        int dz = d.z() - z;
        String direction = (dz < -Math.abs(dx) / 2 ? "nord" : dz > Math.abs(dx) / 2 ? "sud" : "")
                + (dx > Math.abs(dz) / 2 ? (dz != 0 && Math.abs(dz) > Math.abs(dx) / 2 ? "-est" : "est")
                : dx < -Math.abs(dz) / 2 ? (Math.abs(dz) > Math.abs(dx) / 2 ? "-ouest" : "ouest") : "");
        player.sendOverlayMessage(Component.literal("⛽ Gisement à " + (int) Math.hypot(dx, dz) + " blocs vers le " + direction
                + " (richesse " + d.richness() + "/3)").withStyle(ChatFormatting.GOLD));
    }
}
