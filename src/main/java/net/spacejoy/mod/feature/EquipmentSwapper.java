package net.spacejoy.mod.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 换装工具：自动切鞘翅要用到的那些背包操作（移植自 tweakeroo 的 InventoryUtils 子集）。
 *
 * <p>全部走 {@code handleContainerInput} 的 SWAP 动作 —— 也就是把槽位和某个快捷栏槽对调，
 * 这是原版「数字键换位」同一条路径，服务端会照常校验，不涉及任何非标准包。
 *
 * <p>只保留本次移植需要的部分：找最好的鞘翅、把鞘翅和胸甲对调、按物品引用找回某个槽。
 */
public final class EquipmentSwapper {
    /** 玩家背包里「快捷栏之后」的第一个槽位号，9 格合成网格 + 27 格背包。 */
    private static final int MAIN_INVENTORY_START = 9;
    /** 胸甲槽在 {@code inventoryMenu} 里的固定槽位号。 */
    private static final int CHEST_SLOT_INDEX = 6;
    /** 耐久余量：低于这个值就当「快坏了」，不再考虑。 */
    private static final int MIN_DURABILITY_MARGIN = 10;

    private EquipmentSwapper() {
    }

    /**
     * 把背包里最好的鞘翅换到胸甲槽。
     *
     * <p>「最好」的判据和原版附魔一致：先看耐久修补等级，再比剩余耐久。
     */
    public static void equipBestElytra(Player player) {
        if (player == null) {
            return;
        }

        AbstractContainerMenu container = player.containerMenu;
        if (container != player.inventoryMenu) {
            // 开着箱子之类的界面时不动背包，避免和玩家的手动操作打架。
            return;
        }

        int target = findBestElytraSlot(container);
        if (target >= 0) {
            swapToEquipmentSlot(player, EquipmentSlot.CHEST, target);
        }
    }

    /**
     * 把先前缓存下来的那件胸甲换回去。
     *
     * <p>找不到原物时退回「重新挑一件最好的胸甲」，保证玩家不会光着上身。
     */
    public static void swapElytraFromChest(Player player, ItemStack chestplate) {
        if (player == null) {
            return;
        }

        AbstractContainerMenu container = player.containerMenu;
        if (container != player.inventoryMenu) {
            return;
        }

        int slot = findSlotWithItem(container, chestplate);
        if (slot >= 0) {
            swapToEquipmentSlot(player, EquipmentSlot.CHEST, slot);
        } else {
            swapElytraAndChestPlate(player);
        }
    }

    /**
     * 鞘翅换回胸甲：优先换回「最好的那件胸甲」。
     *
     * <p>当前胸甲槽已经是普通护甲时不必再动。
     */
    public static void swapElytraAndChestPlate(Player player) {
        if (player == null) {
            return;
        }

        AbstractContainerMenu container = player.containerMenu;
        if (container != player.inventoryMenu) {
            return;
        }

        ItemStack current = player.getItemBySlot(EquipmentSlot.CHEST);
        if (current.isEmpty() || isChestArmor(current)) {
            // 已经是普通胸甲了，那就顺手把鞘翅找回来。
            equipBestElytra(player);
            return;
        }

        int target = findBestChestPlateSlot(container);
        if (target >= 0) {
            swapToEquipmentSlot(player, EquipmentSlot.CHEST, target);
        }
    }

    // ---------------------------------------------------------------- 选槽

    private static int findBestElytraSlot(AbstractContainerMenu container) {
        int best = -1;
        ItemStack bestStack = ItemStack.EMPTY;

        int last = container.slots.size() - 1;
        for (int i = MAIN_INVENTORY_START; i <= last; i++) {
            ItemStack stack = container.getSlot(i).getItem();
            if (!isUsableElytra(stack)) {
                continue;
            }

            if (best < 0 || isBetterElytra(stack, bestStack)) {
                best = i;
                bestStack = stack;
            }
        }

        return best;
    }

    private static int findBestChestPlateSlot(AbstractContainerMenu container) {
        int best = -1;
        ItemStack bestStack = ItemStack.EMPTY;

        int last = container.slots.size() - 1;
        for (int i = MAIN_INVENTORY_START; i <= last; i++) {
            ItemStack stack = container.getSlot(i).getItem();
            if (!isUsableChestPlate(stack)) {
                continue;
            }

            if (best < 0 || isBetterChestPlate(stack, bestStack)) {
                best = i;
                bestStack = stack;
            }
        }

        return best;
    }

    /** 按物品与组件（不看耐久）找回某个槽，用来把「原来那件胸甲」放回去。 */
    private static int findSlotWithItem(AbstractContainerMenu container, ItemStack reference) {
        if (reference == null || reference.isEmpty()) {
            return -1;
        }

        int last = container.slots.size() - 1;
        for (int i = 0; i <= last; i++) {
            Slot slot = container.getSlot(i);
            if (ItemStack.isSameItemSameComponents(slot.getItem(), reference)) {
                return i;
            }
        }

        return -1;
    }

    // ---------------------------------------------------------------- 判据

    private static boolean isUsableElytra(ItemStack stack) {
        if (!stack.is(Items.ELYTRA)) {
            return false;
        }
        if (!canEquipOnPlayer(stack)) {
            return false;
        }
        return stack.getDamageValue() < stack.getMaxDamage() - MIN_DURABILITY_MARGIN;
    }

    private static boolean isUsableChestPlate(ItemStack stack) {
        return isChestArmor(stack) && stack.getDamageValue() < stack.getMaxDamage() - MIN_DURABILITY_MARGIN;
    }

    private static boolean isChestArmor(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot() == EquipmentSlot.CHEST;
    }

    private static boolean canEquipOnPlayer(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.canBeEquippedBy(
                net.minecraft.world.entity.EntityTypes.PLAYER.builtInRegistryHolder());
    }

    private static boolean isBetterElytra(ItemStack candidate, ItemStack current) {
        if (current.isEmpty()) {
            return true;
        }

        int candidateLevel = enchantmentLevel(candidate, Enchantments.UNBREAKING);
        int currentLevel = enchantmentLevel(current, Enchantments.UNBREAKING);
        if (candidateLevel != currentLevel) {
            return candidateLevel > currentLevel;
        }

        return candidate.getDamageValue() <= current.getDamageValue();
    }

    private static boolean isBetterChestPlate(ItemStack candidate, ItemStack current) {
        if (current.isEmpty()) {
            return true;
        }

        double candidateArmor = armorValue(candidate);
        double currentArmor = armorValue(current);
        if (candidateArmor != currentArmor) {
            return candidateArmor > currentArmor;
        }

        return enchantmentLevel(candidate, Enchantments.PROTECTION)
                > enchantmentLevel(current, Enchantments.PROTECTION);
    }

    /**
     * 读某个附魔的等级。
     *
     * <p>原版附魔表是「等级 0 就是不生效」，所以找不到时返回 0 而不是 -1 ——
     * 比较逻辑里 0 与「没附魔」等价，用不着区分。
     */
    private static int enchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        ItemEnchantments enchantments = stack.getEnchantments();
        if (enchantments.isEmpty()) {
            return 0;
        }

        for (Holder<Enchantment> holder : enchantments.keySet()) {
            if (holder.is(enchantment)) {
                return enchantments.getLevel(holder);
            }
        }

        return 0;
    }

    /** 护甲值与韧性之和，用来横向比较两件胸甲。 */
    private static double armorValue(ItemStack stack) {
        final double[] total = {0.0D};
        stack.forEachModifier(EquipmentSlot.CHEST, (attribute, modifier) -> {
            if (attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)
                    || attribute.is(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)) {
                total[0] += modifier.amount();
            }
        });
        return total[0];
    }

    // ---------------------------------------------------------------- 换位

    /**
     * 把背包里的某个槽换到指定装备槽。
     *
     * <p>只在 {@code inventoryMenu} 上有效 —— 装备槽号是按玩家背包界面写死的。
     */
    private static void swapToEquipmentSlot(Player player, EquipmentSlot type, int sourceSlot) {
        if (type != EquipmentSlot.CHEST) {
            return;
        }

        swapSlots(player, sourceSlot, CHEST_SLOT_INDEX);
    }

    /**
     * 用三次 SWAP 完成「两个槽互换」。
     *
     * <p>SWAP 的语义是「把槽位和某个快捷栏槽对调」，因此两次交换就能把两件东西换过来；
     * 第三次把临时借用的快捷栏槽还原。这是原版数字键换位走同一条路径。
     */
    private static void swapSlots(Player player, int slotNum, int otherSlot) {
        AbstractContainerMenu container = player.containerMenu;
        int hotbarSlot = -1;

        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            int inventorySlot = 36 + i;
            if (inventorySlot != slotNum && inventorySlot != otherSlot) {
                hotbarSlot = i;
                break;
            }
        }

        if (hotbarSlot < 0) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) {
            return;
        }

        mc.gameMode.handleContainerInput(container.containerId, slotNum, hotbarSlot, ContainerInput.SWAP, player);
        mc.gameMode.handleContainerInput(container.containerId, otherSlot, hotbarSlot, ContainerInput.SWAP, player);
        mc.gameMode.handleContainerInput(container.containerId, slotNum, hotbarSlot, ContainerInput.SWAP, player);
    }
}
