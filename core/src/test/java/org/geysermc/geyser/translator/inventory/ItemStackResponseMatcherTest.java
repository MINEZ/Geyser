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

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerSlotType;
import org.cloudburstmc.protocol.bedrock.data.inventory.FullContainerName;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequest;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.ItemStackRequestSlotData;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.ItemStackRequestAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.request.action.PlaceAction;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponse;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseContainer;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseSlot;
import org.cloudburstmc.protocol.bedrock.data.inventory.itemstack.response.ItemStackResponseStatus;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINEZ：物品请求的回应只含请求涉及的格子，并沿用请求中的容器名，见 {@link ItemStackResponseMatcher}。
 */
public class ItemStackResponseMatcherTest {

    @Test
    public void dropsTheUntouchedCursor() {
        // 网易客户端在格子间直接拖放：物品栏 22 格中的 1 个放到 32 格，Geyser 经光标完成
        ItemStackRequest request = request(new PlaceAction(1, slot(ContainerSlotType.INVENTORY, 22, 50), slot(ContainerSlotType.INVENTORY, 32, 0)));
        ItemStackResponse response = response(
            container(ContainerSlotType.INVENTORY, item(32, 1, 50), item(22, 2, 50)),
            container(ContainerSlotType.CURSOR, item(0, 0, 0)));

        IntSet resend = new IntOpenHashSet();
        ItemStackResponse matched = match(request, response, resend);

        assertEquals(1, matched.getContainers().size());
        assertEquals(ContainerSlotType.INVENTORY, matched.getContainers().get(0).getContainerName().getContainer());
        assertEquals(2, matched.getContainers().get(0).getItems().size());
        assertTrue(resend.isEmpty());
    }

    @Test
    public void usesTheRequestedContainerName() {
        // 客户端写作 HOTBAR_AND_INVENTORY，Geyser 的回应写作 INVENTORY
        ItemStackRequest request = request(new PlaceAction(1, slot(ContainerSlotType.CURSOR, 0, 2), slot(ContainerSlotType.HOTBAR_AND_INVENTORY, 21, 0)));
        ItemStackResponse response = response(
            container(ContainerSlotType.INVENTORY, item(21, 1, 2)),
            container(ContainerSlotType.CURSOR, item(0, 0, 0)));

        ItemStackResponse matched = match(request, response, new IntOpenHashSet());

        assertEquals(2, matched.getContainers().size());
        ItemStackResponseContainer inventoryEntry = matched.getContainers().get(0);
        assertEquals(ContainerSlotType.HOTBAR_AND_INVENTORY, inventoryEntry.getContainerName().getContainer());
        assertEquals(21, inventoryEntry.getItems().get(0).getSlot());
        assertEquals(ContainerSlotType.CURSOR, matched.getContainers().get(1).getContainerName().getContainer());
    }

    @Test
    public void resendsChangedSlotsTheRequestDidNotTouch() {
        // 请求只涉及快捷栏 4 格与副手，光标与物品栏 9 格却因 Java 版的操作变了
        ItemStackRequest request = request(new PlaceAction(1, slot(ContainerSlotType.HOTBAR, 4, 42), slot(ContainerSlotType.OFFHAND, 1, 0)));
        ItemStackResponse response = response(
            container(ContainerSlotType.HOTBAR, item(4, 0, 0)),
            container(ContainerSlotType.OFFHAND, item(1, 1, 42)),
            container(ContainerSlotType.INVENTORY, item(9, 3, 7)),
            container(ContainerSlotType.CURSOR, item(0, 1, 8)));

        IntSet resend = new IntOpenHashSet();
        ItemStackResponse matched = match(request, response, resend);

        assertEquals(2, matched.getContainers().size());
        assertEquals(IntSet.of(9, ItemStackResponseMatcher.CURSOR), resend);
    }

    /**
     * 请求之前物品栏（46 格）与光标都是空的；物品栏 9~35 格的编号与 Java 版相同。
     */
    private static ItemStackResponse match(ItemStackRequest request, ItemStackResponse response, IntSet resend) {
        ItemStackResponseMatcher.SlotState[] before = new ItemStackResponseMatcher.SlotState[47];
        Arrays.fill(before, ItemStackResponseMatcher.SlotState.EMPTY);
        return ItemStackResponseMatcher.match(request, response, before, ItemStackRequestSlotData::getSlot, resend);
    }

    private static ItemStackRequest request(ItemStackRequestAction... actions) {
        return new ItemStackRequest(-1, actions, new String[0]);
    }

    private static ItemStackRequestSlotData slot(ContainerSlotType container, int slot, int netId) {
        return new ItemStackRequestSlotData(container, slot, netId, new FullContainerName(container, null));
    }

    private static ItemStackResponse response(ItemStackResponseContainer... containers) {
        return new ItemStackResponse(ItemStackResponseStatus.OK, -1, List.of(containers));
    }

    private static ItemStackResponseContainer container(ContainerSlotType container, ItemStackResponseSlot... items) {
        return new ItemStackResponseContainer(container, List.of(items), new FullContainerName(container, null));
    }

    private static ItemStackResponseSlot item(int slot, int count, int netId) {
        return new ItemStackResponseSlot(slot, slot, count, netId, "", 0, "");
    }
}
