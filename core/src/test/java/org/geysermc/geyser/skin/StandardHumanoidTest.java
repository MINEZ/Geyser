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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MINEZ：只有标准人形模型的基岩版皮肤才提供给 Java 版（网易的 4D 皮肤等自带模型的皮肤套在 Java 版的人形上是错位的色块）。
 */
class StandardHumanoidTest {
    @Test
    void standardHumanoidsAreRecognised() {
        assertTrue(SkinModels.isStandardHumanoid("{\"geometry\" :{\"default\" :\"geometry.humanoid.custom\"}}"));
        assertTrue(SkinModels.isStandardHumanoid("{\"geometry\":{\"default\":\"geometry.humanoid.customSlim\"}}"));
        assertTrue(SkinModels.isStandardHumanoid("{\"geometry\":{\"animated_face\":\"geometry.animated_face_persona\",\"default\":\"geometry.humanoid.custom\"}}"));
    }

    @Test
    void customModelsAndBrokenPatchesAreNot() {
        assertFalse(SkinModels.isStandardHumanoid("{\"geometry\":{\"default\":\"geometry.netease.skin.4d_dragon\"}}"));
        assertFalse(SkinModels.isStandardHumanoid(""));
        assertFalse(SkinModels.isStandardHumanoid("not json"));
        assertFalse(SkinModels.isStandardHumanoid("{\"geometry\":{}}"));
    }
}
