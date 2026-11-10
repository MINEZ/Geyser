/*
 * Copyright (c) 2019-2026 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.translator.inventory;

import it.unimi.dsi.fastutil.ints.IntSet;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ConsumeAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DestroyAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.DropAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.MineBlockAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.SwapAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.TransferItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.session.GeyserSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * MINEZ：让物品请求的回应与请求中的格子一一对应，与基岩版服务端一致。
 * <p>
 * 客户端为请求涉及的每一格记下预测，按回应中的容器与格子逐一核对。Geyser 按 Java 版的点击生成回应，总带上光标与计划中经过的各格
 * （例如格子间直接拖放时经过光标、拿起时借用的临时格），容器名也按 Geyser 的划分（快捷栏、物品栏），客户端的请求中却可能写作
 * HOTBAR_AND_INVENTORY。网易开发版客户端因此弹出“_processResponseSlot - Could not find request id in historic predictions”；
 * 没有核对上的预测留在客户端，之后再点这一格时弹出“SparseContainerClient::tryPushSlotPrediction”。
 * <p>
 * 这里把请求涉及的格子改用请求中的容器名；请求没有涉及的格子从回应中去掉，其中内容确实变了的，回应之后另发一次格子内容。
 */
final class ItemStackResponseMatcher {
    /**
     * 要另发的格子中表示光标的编号（其余为 Java 版的格子编号）。
     */
    static final int CURSOR = -1;

    private ItemStackResponseMatcher() {
    }

    /**
     * 请求之前各格与光标（最后一项）中的物品。
     */
    static SlotState[] snapshot(GeyserSession session, Inventory inventory) {
        SlotState[] states = new SlotState[inventory.getSize() + 1];
        for (int i = 0; i < inventory.getSize(); i++) {
            states[i] = SlotState.of(inventory.getItem(i));
        }
        states[inventory.getSize()] = SlotState.of(session.getPlayerInventory().getCursor());
        return states;
    }

    /**
     * @param before      请求之前各格的物品，见 {@link #snapshot}
     * @param toJavaSlot  把基岩版的格子换成 Java 版的格子编号，即 {@link InventoryTranslator#bedrockSlotToJava}
     * @param resendSlots 收集回应之后要另发的格子
     * @return 只含请求涉及的格子、沿用请求中容器名的回应
     */
    static ItemStackResponse match(ItemStackRequest request, ItemStackResponse response, SlotState[] before,
                                   ToIntFunction<ItemStackRequestSlotData> toJavaSlot, IntSet resendSlots) {
        Map<SlotKey, FullContainerName> requested = new HashMap<>();
        for (ItemStackRequestAction action : request.getActions()) {
            for (ItemStackRequestSlotData slot : requestedSlots(action)) {
                requested.putIfAbsent(SlotKey.of(slot.getContainerName(), slot.getSlot()), slot.getContainerName());
            }
        }

        int size = before.length - 1;
        Map<FullContainerName, List<ItemStackResponseSlot>> containers = new LinkedHashMap<>();
        for (ItemStackResponseContainer container : response.getContainers()) {
            FullContainerName name = container.getContainerName();
            for (ItemStackResponseSlot slot : container.getItems()) {
                FullContainerName requestedName = requested.get(SlotKey.of(name, slot.getSlot()));
                if (requestedName != null) {
                    containers.computeIfAbsent(requestedName, $ -> new ArrayList<>()).add(slot);
                    continue;
                }
                int javaSlot = name.getContainer() == ContainerSlotType.CURSOR ? CURSOR
                    : toJavaSlot.applyAsInt(new ItemStackRequestSlotData(name.getContainer(), slot.getSlot(), 0, name));
                int index = javaSlot == CURSOR ? size : javaSlot;
                if (index >= 0 && index <= size && !before[index].matches(slot)) {
                    resendSlots.add(javaSlot);
                }
            }
        }

        List<ItemStackResponseContainer> entries = new ArrayList<>(containers.size());
        for (Map.Entry<FullContainerName, List<ItemStackResponseSlot>> entry : containers.entrySet()) {
            entries.add(new ItemStackResponseContainer(entry.getKey().getContainer(), entry.getValue(), entry.getKey()));
        }
        return new ItemStackResponse(response.getResult(), response.getRequestId(), entries);
    }

    /**
     * 请求中的一个操作涉及的格子。
     */
    private static List<ItemStackRequestSlotData> requestedSlots(ItemStackRequestAction action) {
        if (action instanceof TransferItemStackRequestAction transfer) {
            return List.of(transfer.getSource(), transfer.getDestination());
        } else if (action instanceof SwapAction swap) {
            return List.of(swap.getSource(), swap.getDestination());
        } else if (action instanceof DropAction drop) {
            return List.of(drop.getSource());
        } else if (action instanceof ConsumeAction consume) {
            return List.of(consume.getSource());
        } else if (action instanceof DestroyAction destroy) {
            return List.of(destroy.getSource());
        } else if (action instanceof MineBlockAction mine) {
            // 挖掉方块时预测了耐久度的手中工具（快捷栏中的一格）。
            FullContainerName hotbar = new FullContainerName(ContainerSlotType.HOTBAR, null);
            return List.of(new ItemStackRequestSlotData(ContainerSlotType.HOTBAR, mine.getHotbarSlot(), mine.getStackNetworkId(), hotbar));
        }
        return List.of();
    }

    /**
     * 客户端眼中的一格。快捷栏、物品栏与 HOTBAR_AND_INVENTORY 是同一组 36 格的不同叫法，按同一个容器比较。
     */
    private record SlotKey(ContainerSlotType container, @Nullable Integer dynamicId, int slot) {
        static SlotKey of(FullContainerName name, int slot) {
            ContainerSlotType container = switch (name.getContainer()) {
                case HOTBAR, INVENTORY, HOTBAR_AND_INVENTORY -> ContainerSlotType.HOTBAR_AND_INVENTORY;
                default -> name.getContainer();
            };
            return new SlotKey(container, name.getDynamicId(), slot);
        }
    }

    /**
     * 一格中物品的数量与 net id，用来判断请求没有涉及的格子是否变了（换成另一种物品时 net id 也会变）。
     */
    record SlotState(int count, int netId) {
        static final SlotState EMPTY = new SlotState(0, 0);

        static SlotState of(GeyserItemStack item) {
            return item.isEmpty() ? EMPTY : new SlotState(item.getAmount(), item.getNetId());
        }

        boolean matches(ItemStackResponseSlot slot) {
            return count == slot.getCount() && (count == 0 || netId == slot.getStackNetworkId());
        }
    }
}
