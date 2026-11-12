package com.terracraft.geo;

import com.google.gson.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import com.terracraft.geo.content.ModBlocks;
import com.terracraft.geo.content.ModContent;
import com.terracraft.geo.content.industry.MachineBlockEntity;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Campagne coopérative : preuves serveur, journal durable et restauration régionale. */
public final class ExodusCampaign {
    private static final ExodusCampaign INSTANCE = new ExodusCampaign();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String GUARDIAN = "terracraft_orion_guardian";
    private ExodusState state = new ExodusState();
    private Path file;
    private Towns towns;
    private AuctionHouse bank;
    private boolean dirty;
    public static ExodusCampaign get() { return INSTANCE; }
    private ExodusCampaign() { }

    void wire(Towns towns, AuctionHouse bank) { this.towns = towns; this.bank = bank; }
    void load(MinecraftServer server) {
        file = server.getWorldPath(LevelResource.ROOT).resolve(GeoMod.MOD_ID).resolve("terra-exodus.json");
        ExodusState loaded = JsonStore.load(file, json -> {
            ExodusState value = GSON.fromJson(json, ExodusState.class);
            if (value == null || value.version != 1 || value.players == null || value.regions == null)
                throw new IllegalArgumentException("Campagne incompatible ou incomplète");
            return value;
        });
        if (loaded == null && (Files.exists(file) || Files.exists(file.resolveSibling("terra-exodus.json.bak"))))
            throw new IllegalStateException("Campagne illisible : restaurer une sauvegarde avant de jouer");
        state = loaded == null ? new ExodusState() : loaded;
        dirty = false;
    }
    void save() {
        if (file == null || !dirty) return;
        try { JsonStore.write(file, GSON.toJson(state)); dirty = false; }
        catch (IOException e) { throw new IllegalStateException("Campagne non enregistrée",e); }
    }
    private boolean transaction(Runnable change) {
        String before = GSON.toJson(state);
        try { change.run(); dirty = true; save(); return true; }
        catch (RuntimeException e) {
            state = GSON.fromJson(before, ExodusState.class);
            GeoMod.LOGGER.error("[EXODUS] Opération annulée", e);
            return false;
        }
    }
    private ExodusState.Journal journal(ServerPlayer player) { return state.journal(player.getUUID()); }
    public void fact(ServerPlayer player, String key, long amount) {
        if (file == null || amount <= 0 || player.isSpectator()) return;
        bump(journal(player), key, amount);
    }
    private void bump(ExodusState.Journal j, String key, long amount) {
        j.facts.merge(key, amount, (a,b) -> Math.min(1_000_000_000L,a+b)); dirty = true;
    }
    private void witness(ExodusState.Journal j, String key, boolean value) {
        if (value && j.facts.getOrDefault(key,0L) == 0) bump(j,key,1);
    }
    void registerMachine(ServerPlayer player, MachineBlockEntity machine) {
        // Propriétaire enregistré dans le bloc : une machine cassée n'attribue pas sa remplaçante.
        boolean mine=machine.attribute(player.getUUID());
        player.sendSystemMessage(Component.literal("Télémétrie : " + (mine
                ? "production attribuée à ton journal." : "machine déjà attribuée.")));
    }
    public void machineFact(ServerLevel level, BlockPos pos, String fact, long amount) {
        if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) return;
        String owner=machine.telemetryOwner();
        if (!owner.isEmpty()) bump(state.journal(UUID.fromString(owner)),fact,amount);
    }
    void stationReady(ServerPlayer owner, ServerLevel level, BlockPos beacon) {
        if (level.dimension() == Space.ORBIT && level.getBlockState(beacon).is(ModBlocks.STATION_BEACON)
                && level.getBlockState(beacon.offset(-26,2,-4)).is(ModBlocks.STATION_WORKSHOP)
                && level.getBlockState(beacon.offset(-22,1,0)).is(ModBlocks.OXYGEN_DISTRIBUTOR)) fact(owner,"station",1);
    }
    private void observe(ServerPlayer player) {
        ExodusState.Journal j = journal(player);
        Progression p = Progression.get();
        for (String key : List.of("loot","kills","vehicles","supplies","launches","helium","titanium")) {
            long now = p.stat(player,key);
            if (now > j.facts.getOrDefault(key,0L)) { j.facts.put(key,now); dirty = true; }
        }
        for (String key : List.of("rampart","orbit","moon","mars","alien_sanctuary"))
            witness(j,key,p.hasDiscovered(player,key));
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            witness(j,"weapon",stack.has(DataComponents.WEAPON) || stack.is(ModContent.PISTOL) || stack.is(ModContent.RIFLE));
        }
        witness(j,"town_claim",towns.townOf(player.getUUID()) != null && Claims.claimCount(player) > 0);
        witness(j,"eva",!SpaceSuit.helmet(player).isEmpty() && SpaceSuit.hasSuit(player) && SpaceSuit.hasBoots(player)
                && SpaceSuit.tanks(player) > 0);
        if (player.level().dimension() == Space.ASTEROIDS && SpaceSuit.hasBoots(player) && SpaceSuit.hasJetpackFuel(player)
                && !SpaceSuit.helmet(player).isEmpty() && SpaceSuit.hasSuit(player)) {
            boolean dock = Stations.get().dockedNear(player.level(),player.blockPosition(),16);
            if (!dock && player.getVehicle() == null && !j.evaOut) { j.evaOut = true; dirty = true; }
            witness(j,"eva_return",j.evaOut && dock && player.getVehicle()==null);
        }
        // Reprise des bases des joueurs existants ; les déploiements ne rejouent jamais leur kit.
        if (Stations.get().hasStation(player,player.level())) {
            if (player.level().dimension() == Space.ORBIT && j.facts.getOrDefault("station",0L)==0) {
                BlockPos beacon=Stations.get().beaconNear(player.level(),player.blockPosition(),48);
                if (beacon!=null) stationReady(player,player.level(),beacon);
            }
            if (player.level().dimension() == Space.MOON) witness(j,"moon_base",true);
            if (player.level().dimension() == Space.MARS) witness(j,"mars_base",true);
        }
    }
    void tick(MinecraftServer server) {
        if (file == null || server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            observe(player);
            ExodusState.Journal j=journal(player);
            if (!j.guardian.isEmpty() && player.level().dimension()==Space.ASTEROIDS) {
                var guardian=player.level().getEntity(UUID.fromString(j.guardian));
                if (guardian!=null && guardian.blockPosition().distSqr(new BlockPos(j.trialX,j.trialY,j.trialZ))>24*24)
                    guardian.teleportTo(j.trialX+0.5,j.trialY,j.trialZ+0.5);
            }
            if (!journal(player).pending.isEmpty()) claim(player,journal(player).pending);
        }
        if (server.getTickCount() % 200 == 0) save();
    }
    boolean hasCompletedAny(ServerPlayer player) { return !journal(player).done.isEmpty(); }
    int chapter(ServerPlayer player) { return ExodusQuests.chapter(journal(player).done); }
    List<ExodusQuests.Quest> available(ServerPlayer player) {
        ExodusState.Journal j = journal(player);
        return ExodusQuests.ALL.stream().filter(q -> !j.done.contains(q.id()) && ExodusQuests.unlocked(q,j.done)).toList();
    }
    boolean allowed(ServerPlayer player, String id) {
        ExodusQuests.Quest q = ExodusQuests.find(id);
        return q != null && available(player).contains(q);
    }
    int show(ServerPlayer player) {
        observe(player);
        say(player,"TERRA EXODUS — Acte " + chapter(player) + " · /exodus livre pour actualiser ton carnet");
        for (ExodusQuests.Quest q : available(player)) {
            say(player,q.id() + " — " + q.title());
            for (ExodusQuests.Goal g : q.goals()) say(player,"  " + g.label() + " : "
                    + Math.min(g.target(),journal(player).facts.getOrDefault(g.fact(),0L)) + "/" + g.target());
            say(player,"Récompense : " + q.credits() + " crédits, " + (q.act()*25) + " points. /exodus reclamer " + q.id());
        }
        if (journal(player).done.size() == ExodusQuests.ALL.size()) say(player,"Les Vestiges d’Orion — campagne achevée. Ta ville garde la mémoire de votre expédition.");
        return 1;
    }
    int claim(ServerPlayer player, String id) {
        ExodusQuests.Quest q = ExodusQuests.find(id);
        if (q == null) return say(player,"Quête inconnue.");
        observe(player);
        ExodusState.Journal j = journal(player);
        if (!j.pending.isEmpty() && !j.pending.equals(q.id())) return say(player,"Une remise est en cours : réessaie après son enregistrement.");
        if (!ExodusQuests.ready(q,j.done,j.facts)) return say(player,"Quête verrouillée, incomplète ou déjà réclamée. Consulte /exodus.");
        if (j.pending.isEmpty() && !transaction(() -> j.pending = q.id())) return 0;
        String receipt = "exodus:" + player.getUUID() + ":" + q.id();
        if (!bank.creditOnce(player,q.credits(),receipt) || !Progression.get().campaignReward(player,receipt,q.act()*25)) return 0;
        if (!transaction(() -> { journal(player).done.add(q.id()); journal(player).pending = ""; })) return 0;
        say(player,"Quête accomplie : " + q.title() + " — " + q.credits() + " crédits. /exodus livre");
        GeoMod.LOGGER.info("[EXODUS] {} termine {}",player.getName().getString(),q.id());
        return 1;
    }
    JsonArray toJson(ServerPlayer player) {
        JsonArray array = new JsonArray();
        ExodusState.Journal j = journal(player);
        for (ExodusQuests.Quest q : ExodusQuests.ALL) {
            if (!ExodusQuests.unlocked(q,j.done) && !j.done.contains(q.id())) continue;
            JsonObject entry = new JsonObject();
            entry.addProperty("id",ExodusQuests.PREFIX+q.id()); entry.addProperty("title","Acte " + q.act() + " · " + q.title());
            entry.addProperty("description",String.join(" · ",q.goals().stream().map(g -> g.label()+" "
                    + Math.min(g.target(),j.facts.getOrDefault(g.fact(),0L))+"/"+g.target()).toList()));
            entry.addProperty("progress",q.goals().stream().filter(g -> j.facts.getOrDefault(g.fact(),0L)>=g.target()).count());
            entry.addProperty("target",q.goals().size()); entry.addProperty("reward",q.credits()); entry.addProperty("claimed",j.done.contains(q.id()));
            array.add(entry);
        }
        return array;
    }
    JsonObject context(ServerPlayer player) {
        JsonObject result = new JsonObject();
        result.addProperty("chapter",chapter(player));
        result.addProperty("dimension",player.level().dimension().identifier().toString());
        result.addProperty("canon", switch(chapter(player)) {
            case 1 -> "2089 : effondrement écologique et énergétique. L'origine exacte reste inconnue. Reconstruire un refuge.";
            case 2 -> "Le refuge prépare une infrastructure énergétique pour rejoindre l'orbite.";
            case 3 -> "Une archive de l'expédition de 2089 attend dans les sanctuaires lunaires.";
            case 4 -> "Les archives martiennes indiquent un relais d'Orion dans les astéroïdes.";
            default -> "Le Noyau d'Orion peut restaurer une région terrestre avec l'accord de sa ville.";
        });
        return result;
    }
    void giveBook(ServerPlayer player) {
        List<String> pages = new ArrayList<>(List.of(
            "TERRA EXODUS\nLes Vestiges d’Orion\n\nJournal de " + player.getName().getString(),
            "2089. Le Grand Effondrement a brisé les réseaux et ravagé les villes. Une contamination gagne les ruines. Son origine reste inconnue.",
            "Dernier signal : « Le berceau se meurt. » Une anomalie a été repérée sur la Lune et Mars. La suite du message manque.",
            "ORDRE DE MISSION\n\nSurvis. Retrouve les autres. Rebâtis une ville. Les réponses attendront : commence par une arme, un coffre fouillé et un abri.",
            "JOURNAL\nActe " + chapter(player) + "\n" + journal(player).done.size() + "/19 quêtes accomplies.\n\n/exodus : objectifs\n/exodus livre : mise à jour\n/exodus reclamer <id> : récompense"));
        for (ExodusQuests.Quest q : available(player)) pages.add(q.title()+"\n\n"+String.join("\n",q.goals().stream().map(ExodusQuests.Goal::label).toList()));
        pages.add("LES VOIX DU REFUGE\nHélène Vance coordonne les survivants. Boulon répare leurs machines. Aris Thorne prépare les vols.\n\n/pnj : parler à un contact proche.");
        if (chapter(player)>=3) pages.add("ARCHIVE 2089\nSujet-09 conserve les fragments du voyage. Sur la Lune, entre dans un sanctuaire puis /exodus archives. Préserve tes réserves d’oxygène.");
        if (chapter(player)>=4) pages.add("LA PISTE D’ORION\nSoren Kael cherche les archives martiennes. /exodus signal indique un site ; lis ses quatre glyphes avec /exodus archives.");
        if (chapter(player)>=5) pages.add("LE RELAIS FINAL\n/exodus signal\n/exodus epreuve\n/exodus noyau\n\nSur Terre, près du registre de ta ville : /exodus contribuer. Le maire ou un adjoint active /exodus restaurer.");
        if (journal(player).done.size()==ExodusQuests.ALL.size()) pages.add(
            "L’ÉVEIL COSMIQUE\n\nVotre expédition a rejoint les Archives d’Orion. Une région terrestre retrouve un avenir. Le berceau peut renaître, ville après ville.\n\nLes origines du signal restent à explorer.");
        ItemStack book = Arrival.guide(pages);
        CompoundTag tag = new CompoundTag(); tag.putString("exodus_book",player.getUUID().toString());
        book.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        for (int i=0;i<player.getInventory().getContainerSize();i++) {
            ItemStack old = player.getInventory().getItem(i);
            if (old.is(Items.WRITTEN_BOOK) && data(old,"exodus_book").equals(player.getUUID().toString())) {
                player.getInventory().setItem(i,book); say(player,"Journal TERRA EXODUS actualisé."); return;
            }
        }
        if (!player.getInventory().add(book)) player.spawnAtLocation(player.level(),book);
        say(player,"Journal TERRA EXODUS reçu. /exodus pour tes objectifs.");
    }
    private static String data(ItemStack item, String key) {
        CustomData data = item.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getString(key).orElse("");
    }
    private int archives(ServerPlayer player) {
        if (player.level().dimension() == Space.MOON && Progression.get().hasDiscovered(player,"alien_sanctuary")) {
            var site = com.terracraft.geo.world.MoonUnderground.nearest(player.getBlockX(),player.getBlockZ());
            if (site == null || player.blockPosition().distSqr(site.chest()) > 48*48) return say(player,"Rapproche-toi du trésor du sanctuaire lunaire.");
            fact(player,"archive_2089",1); save();
            return say(player,"Archive 2089 : « Nous avons suivi le signal. Mars porte les marques de ceux qui nous observaient. Sujet-09, fin de transmission. »");
        }
        if (player.level().dimension() == Space.MARS && allowed(player,"q4_3_orion_glyphs") && OrionSites.isInside(player.level(),player.blockPosition(),false)) {
            fact(player,"mars_archive",1); save();
            return say(player,"Les quatre glyphes dessinent une route vers la ceinture d’astéroïdes. Un Archiviste y attend les voyageurs. /exodus signal");
        }
        return say(player,"Aucune archive accessible ici. Consulte /exodus puis /exodus signal.");
    }
    private int signal(ServerPlayer player) {
        boolean mars = player.level().dimension() == Space.MARS;
        boolean space = player.level().dimension() == Space.ASTEROIDS;
        if (!(mars && chapter(player)>=4 || space && chapter(player)>=5)) return say(player,"Le signal exige Mars (acte IV) ou les astéroïdes (acte V).");
        BlockPos at = OrionSites.nearest(player.getBlockX(),player.getBlockZ(),!mars);
        return say(player,(mars ? "Archive martienne" : "Temple d’Orion")+" : "+at.toShortString()+". Sites présents dans les nouveaux chunks de cette version.");
    }
    private int trial(ServerPlayer player) {
        if (!allowed(player,"q5_2_sanctuary") || player.level().dimension()!=Space.ASTEROIDS
                || !OrionSites.isInside(player.level(),player.blockPosition(),true)) return say(player,"Épreuve verrouillée ou temple absent. /exodus signal");
        ExodusState.Journal j = journal(player);
        if (j.facts.getOrDefault("guardian",0L)>0) return say(player,"Ton gardien est déjà vaincu. Réclame la quête.");
        if (!j.guardian.isEmpty() && player.level().getEntity(UUID.fromString(j.guardian)) != null)
            return say(player,"Ton gardien est toujours présent.");
        BlockPos at = OrionSites.nearest(player.getBlockX(),player.getBlockZ(),true).above();
        var boss = Bosses.commander(player.level(),at);
        if (boss == null) return 0;
        boss.addTag(GUARDIAN);
        boss.setCustomName(Component.literal("Gardien d’Orion — " + player.getName().getString()));
        if (!transaction(() -> { j.guardian=boss.getUUID().toString(); j.trialX=at.getX();j.trialY=at.getY();j.trialZ=at.getZ(); })) return 0;
        player.level().addFreshEntity(boss);
        return say(player,"L’Archiviste : affronte le gardien. Les autres voyageurs présents peuvent t’aider.");
    }
    static boolean isGuardian(LivingEntity entity) { return entity.entityTags().contains(GUARDIAN); }
    void onDeath(LivingEntity entity, DamageSource source) {
        if (!isGuardian(entity) || entity.level().dimension()!=Space.ASTEROIDS || !(source.getEntity() instanceof ServerPlayer)) return;
        for (var entry : state.players.entrySet()) {
            ExodusState.Journal j = entry.getValue();
            if (!j.guardian.equals(entity.getUUID().toString())) continue;
            ServerPlayer owner = entity.level().getServer().getPlayerList().getPlayer(UUID.fromString(entry.getKey()));
            if (owner == null || owner.level()!=entity.level() || owner.distanceToSqr(entity)>48*48) return;
            if (transaction(() -> { witness(j,"guardian",true); j.guardian=""; })) say(owner,"Le gardien s’effondre. Réclame q5_2_sanctuary, puis /exodus noyau.");
            return;
        }
    }
    private int core(ServerPlayer player) {
        ExodusState.Journal j = journal(player);
        if (!allowed(player,"q5_3_artifact") || !j.contribution.isEmpty()) return say(player,"Le Noyau n’est pas disponible pour ton journal.");
        if (j.core.isEmpty() && !transaction(() -> j.core=UUID.randomUUID().toString())) return 0;
        ItemStack item = new ItemStack(ModBlocks.ALIEN_ARTIFACT);
        item.set(DataComponents.CUSTOM_NAME,Component.literal("Noyau d’Orion"));
        CompoundTag tag = new CompoundTag();tag.putString("orion_core",j.core);tag.putString("orion_owner",player.getUUID().toString());
        item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
        if (!player.getInventory().add(item)) player.spawnAtLocation(player.level(),item);
        return say(player,"Noyau récupéré. Il ne peut restaurer qu’une seule région, même après récupération ou copie.");
    }
    private Towns.Town townAtRegistry(ServerPlayer player) {
        Towns.Town town = towns.townOf(player.getUUID());
        if (town==null || player.level().dimension()!=Level.OVERWORLD || !town.center.dimension().equals("minecraft:overworld")
                || Math.hypot(town.center.x()-player.getX(),town.center.z()-player.getZ())>64) return null;
        for (BlockPos pos : BlockPos.betweenClosed(player.blockPosition().offset(-4,-3,-4),player.blockPosition().offset(4,3,4)))
            if (player.level().getBlockState(pos).is(ModBlocks.TOWN_REGISTRY)) return town;
        return null;
    }
    private static String townKey(Towns.Town town) { return town.founded+":"+town.name.toLowerCase(java.util.Locale.ROOT); }
    private int contribute(ServerPlayer player) {
        Towns.Town town = townAtRegistry(player);
        ExodusState.Journal j = journal(player);
        if (town==null || !allowed(player,"q5_3_artifact") || j.core.isEmpty() || !j.contribution.isEmpty()) return say(player,"Rejoins le registre de ta ville avec ta quête finale ouverte et ton Noyau.");
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModBlocks.ALIEN_ARTIFACT) || !data(held,"orion_core").equals(j.core)
                || !data(held,"orion_owner").equals(player.getUUID().toString())) return say(player,"Ce Noyau n’est pas authentifié pour ton journal.");
        String key=townKey(town);
        if (!transaction(() -> {
            ExodusState.Region region=state.regions.computeIfAbsent(key,k -> {
                ExodusState.Region r=new ExodusState.Region();r.name=town.name;r.x=(int)Math.floor(town.center.x());r.z=(int)Math.floor(town.center.z());return r;
            });
            region.contributors.add(player.getUUID().toString());
            region.names.put(player.getUUID().toString(),player.getName().getString());j.contribution=key;
            witness(j,"restoration",region.active);
        })) return 0;
        held.shrink(1);
        return say(player,"Noyau confié à "+town.name+". Le maire ou un adjoint peut activer /exodus restaurer. Avant activation, /exodus retirer permet de reprendre ta contribution.");
    }
    private int withdrawContribution(ServerPlayer player) {
        ExodusState.Journal j=journal(player);
        ExodusState.Region region=state.regions.get(j.contribution);
        if (region==null || region.active || !region.contributors.contains(player.getUUID().toString()))
            return say(player,"Aucune contribution non activée à retirer.");
        if (!transaction(() -> {
            region.contributors.remove(player.getUUID().toString());region.names.remove(player.getUUID().toString());
            if (region.contributors.isEmpty()) state.regions.remove(j.contribution);
            j.contribution="";
        })) return 0;
        return say(player,"Contribution retirée avant activation. /exodus noyau récupère ton Noyau avec le même identifiant.");
    }
    private int restore(ServerPlayer player) {
        Towns.Town town = townAtRegistry(player);
        if (town==null || !town.canManage(player.getUUID())) return say(player,"Seuls le maire et les adjoints, près du registre de leur ville, peuvent restaurer la région.");
        ExodusState.Region region=state.regions.get(townKey(town));
        if (region==null || region.contributors.isEmpty() || region.active) return say(player,"Aucun Noyau en attente ou région déjà restaurée.");
        if (!transaction(() -> {
            region.active=true;
            for (String owner : region.contributors) witness(state.journal(UUID.fromString(owner)),"restoration",true);
        })) return 0;
        player.level().getServer().getPlayerList().broadcastSystemMessage(Component.literal("TERRA EXODUS — "+town.name+" a restauré une région de 64 blocs de rayon. Les contributeurs rejoignent les Archives d’Orion."),false);
        return 1;
    }
    boolean restored(int x,int z) { return state.regions.values().stream().anyMatch(r -> ExodusState.contains(r,x,z)); }
    private static int say(ServerPlayer player,String text) { player.sendSystemMessage(Component.literal(text)); return 0; }
    private int ledger(ServerPlayer player) {
        for (ExodusState.Region r:state.regions.values()) say(player,r.name+" : "+(r.active?"restaurée":"Noyau en attente")+", centre "+r.x+" / "+r.z+", rayon "+r.radius+", "+String.join(", ",r.contributors.stream().map(id -> r.names.getOrDefault(id,id)).toList()));
        return 1;
    }
    void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("exodus")
            .executes(c -> show(c.getSource().getPlayerOrException()))
            .then(Commands.literal("livre").executes(c -> {giveBook(c.getSource().getPlayerOrException());return 1;}))
            .then(Commands.literal("reclamer").then(Commands.argument("quete",StringArgumentType.word())
                .executes(c -> claim(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"quete")))))
            .then(Commands.literal("archives").executes(c -> archives(c.getSource().getPlayerOrException())))
            .then(Commands.literal("signal").executes(c -> signal(c.getSource().getPlayerOrException())))
            .then(Commands.literal("epreuve").executes(c -> trial(c.getSource().getPlayerOrException())))
            .then(Commands.literal("noyau").executes(c -> core(c.getSource().getPlayerOrException())))
            .then(Commands.literal("contribuer").executes(c -> contribute(c.getSource().getPlayerOrException())))
            .then(Commands.literal("retirer").executes(c -> withdrawContribution(c.getSource().getPlayerOrException())))
            .then(Commands.literal("restaurer").executes(c -> restore(c.getSource().getPlayerOrException())))
            .then(Commands.literal("registre").executes(c -> ledger(c.getSource().getPlayerOrException())))
            .then(Commands.literal("admin").requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                .then(Commands.literal("valider").then(Commands.argument("joueur",EntityArgument.player())
                    .then(Commands.argument("quete",StringArgumentType.word()).executes(c -> {
                        ServerPlayer target=EntityArgument.getPlayer(c,"joueur");
                        ExodusQuests.Quest q=ExodusQuests.find(StringArgumentType.getString(c,"quete"));
                        if(q==null || !journal(target).pending.isEmpty()) return 0;
                        boolean ok=transaction(() -> journal(target).done.add(q.id()));
                        GeoMod.LOGGER.warn("[EXODUS ADMIN] {} valide {} pour {} sans récompense",c.getSource().getTextName(),q.id(),target.getName().getString());
                        return ok?1:0;
                    }))))
                .then(Commands.literal("reinitialiser").then(Commands.argument("joueur",EntityArgument.player()).executes(c -> {
                    ServerPlayer target=EntityArgument.getPlayer(c,"joueur");
                    if (!journal(target).pending.isEmpty()) return 0;
                    return transaction(() -> {journal(target).done.clear();journal(target).facts.clear();})?1:0;
                })))));
    }
}
