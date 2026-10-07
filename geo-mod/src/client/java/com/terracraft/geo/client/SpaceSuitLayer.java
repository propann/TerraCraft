package com.terracraft.geo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.terracraft.geo.GeoMod;
import com.terracraft.geo.SpaceSuit;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Dessine l'équipement spatial sur le joueur comme une armure : même modèle que les armures
 * vanilla, un peu plus large, pour passer par-dessus un casque ou un plastron normal.
 * Texture : {@code textures/entity/equipment/humanoid[_leggings]/space_suit.png}.
 */
public final class SpaceSuitLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "space_suit");
    private static final ArmorModelSet<ModelLayerLocation> LAYERS = new ArmorModelSet<>(
            new ModelLayerLocation(ID, "head"), new ModelLayerLocation(ID, "chest"),
            new ModelLayerLocation(ID, "legs"), new ModelLayerLocation(ID, "feet"));
    private static final ResourceKey<EquipmentAsset> ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, ID);
    /** Jetpack : dessiné sur un modèle encore plus large, par-dessus la combinaison. */
    private static final Identifier JETPACK_ID = Identifier.fromNamespaceAndPath(GeoMod.MOD_ID, "jetpack");
    private static final ArmorModelSet<ModelLayerLocation> JETPACK_LAYERS = new ArmorModelSet<>(
            new ModelLayerLocation(JETPACK_ID, "head"), new ModelLayerLocation(JETPACK_ID, "chest"),
            new ModelLayerLocation(JETPACK_ID, "legs"), new ModelLayerLocation(JETPACK_ID, "feet"));
    private static final ResourceKey<EquipmentAsset> JETPACK_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, JETPACK_ID);

    private final ArmorModelSet<PlayerModel> models;
    private final ArmorModelSet<PlayerModel> jetpackModels;
    private final EquipmentLayerRenderer equipment;

    public SpaceSuitLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityRendererProvider.Context context) {
        super(parent);
        this.models = ArmorModelSet.bake(LAYERS, context.getModelSet(), part -> new PlayerModel(part, false));
        this.jetpackModels = ArmorModelSet.bake(JETPACK_LAYERS, context.getModelSet(), part -> new PlayerModel(part, false));
        this.equipment = context.getEquipmentRenderer();
    }

    /** Modèles « armure » élargis : l'armure vanilla est à 0,5 / 1,0, la combinaison à 0,75 / 1,3. */
    public static void registerModels() {
        ModelLayerRegistry.registerArmorModelLayers(LAYERS, () -> PlayerModel
                .createArmorMeshSet(new CubeDeformation(0.75f), new CubeDeformation(1.3f))
                .map(mesh -> LayerDefinition.create(mesh, 64, 32)));
        ModelLayerRegistry.registerArmorModelLayers(JETPACK_LAYERS, () -> PlayerModel
                .createArmorMeshSet(new CubeDeformation(0.75f), new CubeDeformation(1.8f))
                .map(mesh -> LayerDefinition.create(mesh, 64, 32)));
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (state.isInvisible || state.isSpectator || Minecraft.getInstance().level == null) {
            return;
        }
        if (!(Minecraft.getInstance().level.getEntity(state.id) instanceof Player player)) {
            return;
        }
        piece(SpaceSuit.get(player, SpaceSuit.HELMET), EquipmentSlot.HEAD, EquipmentClientInfo.LayerType.HUMANOID,
                poseStack, collector, light, state);
        ItemStack suit = SpaceSuit.get(player, SpaceSuit.SUIT);
        piece(suit, EquipmentSlot.CHEST, EquipmentClientInfo.LayerType.HUMANOID, poseStack, collector, light, state);
        piece(suit, EquipmentSlot.LEGS, EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, poseStack, collector, light, state);
        piece(SpaceSuit.get(player, SpaceSuit.BOOTS), EquipmentSlot.FEET, EquipmentClientInfo.LayerType.HUMANOID,
                poseStack, collector, light, state);
        ItemStack jetpack = SpaceSuit.jetpack(player);
        if (!jetpack.isEmpty()) {
            equipment.renderLayers(EquipmentClientInfo.LayerType.HUMANOID, JETPACK_ASSET, jetpackModels.get(EquipmentSlot.CHEST),
                    state, jetpack, poseStack, collector, light, state.outlineColor);
        }
    }

    private void piece(ItemStack stack, EquipmentSlot slot, EquipmentClientInfo.LayerType type, PoseStack poseStack,
                       SubmitNodeCollector collector, int light, AvatarRenderState state) {
        if (stack.isEmpty()) {
            return;
        }
        equipment.renderLayers(type, ASSET, models.get(slot), state, stack, poseStack, collector, light, state.outlineColor);
    }
}
