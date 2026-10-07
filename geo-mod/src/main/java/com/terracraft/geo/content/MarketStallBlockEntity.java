package com.terracraft.geo.content;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/**
 * Étal de marché : propriétaire, prix à l'unité et stock de 27 cases. Le stock n'est pas un {@code Container} exposé
 * au monde : ni entonnoir ni wagonnet ne peuvent le vider ; seul le propriétaire l'ouvre (voir Stalls).
 */
public class MarketStallBlockEntity extends BlockEntity {
    private UUID owner;
    private String ownerName = "";
    private long price;
    private final SimpleContainer stock = new SimpleContainer(27) {
        @Override
        public void setChanged() {
            super.setChanged();
            MarketStallBlockEntity.this.setChanged();
        }
    };

    public MarketStallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MARKET_STALL_ENTITY, pos, state);
    }

    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public void setOwner(UUID owner, String name) {
        this.owner = owner;
        this.ownerName = name;
        setChanged();
    }

    public long price() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
        setChanged();
    }

    public SimpleContainer stock() {
        return stock;
    }

    /** Objet proposé : la première pile non vide du stock (ou vide). */
    public ItemStack offer() {
        for (ItemStack stack : stock.getItems()) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Quantité disponible de l'objet proposé (toutes les piles identiques). */
    public int available() {
        ItemStack offer = offer();
        int count = 0;
        for (ItemStack stack : stock.getItems()) {
            if (!offer.isEmpty() && ItemStack.isSameItemSameComponents(stack, offer)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Retire {@code amount} exemplaires de l'objet proposé ; renvoie ce qui a été retiré. */
    public ItemStack take(int amount) {
        ItemStack offer = offer().copy();
        int left = amount;
        for (ItemStack stack : stock.getItems()) {
            if (left > 0 && ItemStack.isSameItemSameComponents(stack, offer)) {
                int n = Math.min(left, stack.getCount());
                stack.shrink(n);
                left -= n;
            }
        }
        stock.setChanged();
        return offer.copyWithCount(amount - left);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (owner != null) {
            output.putString("Owner", owner.toString());
        }
        output.putString("OwnerName", ownerName);
        output.putLong("Price", price);
        ContainerHelper.saveAllItems(output, stock.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        owner = input.getString("Owner").map(UUID::fromString).orElse(null);
        ownerName = input.getStringOr("OwnerName", "");
        price = input.getLongOr("Price", 0);
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        for (int i = 0; i < items.size(); i++) {
            stock.getItems().set(i, items.get(i));
        }
    }
}
