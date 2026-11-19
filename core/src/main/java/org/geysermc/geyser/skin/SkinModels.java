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

package org.geysermc.geyser.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * MINEZ：基岩版皮肤的模型。
 */
public final class SkinModels {
    private SkinModels() {
    }

    /**
     * MINEZ：皮肤的 SkinResourcePatch（形如 {"geometry":{"default":"geometry.humanoid.custom"}}）中默认的几何体是否为标准人形。
     * 网易的 4D 皮肤等自带模型的皮肤是别的名字；读不出时视为不是。
     */
    public static boolean isStandardHumanoid(String resourcePatch) {
        try {
            JsonObject root = JsonParser.parseString(resourcePatch).getAsJsonObject();
            String name = root.getAsJsonObject("geometry").get("default").getAsString();
            return name.equalsIgnoreCase("geometry.humanoid.custom") || name.equalsIgnoreCase("geometry.humanoid.customSlim");
        } catch (RuntimeException e) {
            return false;
        }
    }
}
