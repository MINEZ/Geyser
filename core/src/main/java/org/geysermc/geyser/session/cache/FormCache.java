/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.session.cache;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.RequiredArgsConstructor;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.ModalFormCancelReason;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundCloseFormPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.impl.FormDefinitions;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.session.GeyserSession;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

@RequiredArgsConstructor
public class FormCache {
    /**
     * The magnitude of this doesn't actually matter, but it must be negative so that
     * BedrockNetworkStackLatencyTranslator can detect the hack.
     */
    private static final long MAGIC_FORM_IMAGE_HACK_TIMESTAMP = -1234567890L;
    /**
     * Shortest intervals between settings form builds and container close resends
     */
    private static final long SETTINGS_REQUEST_COOLDOWN_MILLIS = 1000;
    private static final long CONTAINER_CLOSE_RESEND_COOLDOWN_MILLIS = 500;

    private final FormDefinitions formDefinitions = FormDefinitions.instance();
    private final AtomicInteger formIdCounter = new AtomicInteger(0);
    private final Int2ObjectMap<Form> forms = new Int2ObjectOpenHashMap<>();
    /**
     * MINEZ：客户端以“正忙”拒绝表单时重新发送：前 BUSY_FAST_RETRIES 次每 BUSY_FAST_DELAY_MILLIS 毫秒一次（通常是上一个表单或界面
     * 还没有关完，很快就好），此后每 BUSY_SLOW_DELAY_MILLIS 毫秒一次，直到第一次被拒后 BUSY_RETRY_WINDOW_MILLIS 毫秒仍忙，才照关闭
     * 处理。客户端可能忙得更久：例如网易账号第一次进入服务器时，客户端自己的界面还没有关，进服就要显示的表单（创建角色）一连被拒
     * 了 6 秒，此前重试约 5 秒就照关闭处理，登录闸门以“关闭了主表单”断开了网易的审核账号。等待的上限与登录闸门创建角色的时限
     * （默认 10 分钟）相同。
     */
    private static final int BUSY_FAST_RETRIES = 20;
    private static final long BUSY_FAST_DELAY_MILLIS = 250;
    private static final long BUSY_SLOW_DELAY_MILLIS = 1000;
    private static final long BUSY_RETRY_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(10);
    /**
     * MINEZ：各表单因客户端正忙已重新发送的次数与第一次被拒的时刻。
     */
    private final Int2IntMap busyRetries = new Int2IntOpenHashMap();
    private final Int2LongMap busySince = new Int2LongOpenHashMap();
    /**
     * MINEZ：判断客户端忙了多久用的时钟（测试中替换）。
     */
    private LongSupplier clock = System::currentTimeMillis;
    private final GeyserSession session;
    private long lastSettingsRequest;
    private long lastContainerCloseResend;

    public boolean hasFormOpen() {
        // If forms is empty it implies that there are no forms to show
        // so technically this returns "has forms to show" or "has open"
        // Forms are only queued in specific circumstances, such as waiting on
        // previous inventories to close
        return !forms.isEmpty();
    }

    public int addForm(Form form) {
        int formId = formIdCounter.getAndIncrement();
        forms.put(formId, form);
        return formId;
    }

    public void showForm(Form form) {
        int formId = addForm(form);

        if (session.getUpstream().isInitialized()) {
            sendForm(formId, form);
        }
    }

    private void sendForm(int formId, Form form) {
        String jsonData = formDefinitions.codecFor(form).jsonData(form);

        ModalFormRequestPacket formRequestPacket = new ModalFormRequestPacket();
        formRequestPacket.setFormId(formId);
        formRequestPacket.setFormData(jsonData);
        session.sendUpstreamPacket(formRequestPacket);

        // Hack to fix the (url) image loading bug
        if (form instanceof SimpleForm) {
            // Two delays:
            // First, 500ms, before we send the network stack latency packet
            session.scheduleInEventLoop(() -> session.sendNetworkLatencyStackPacket(MAGIC_FORM_IMAGE_HACK_TIMESTAMP, false, () -> {
                    // Then, wait 500ms after we receive the response, then update attributes to get the image to show
                    session.scheduleInEventLoop(() -> {
                        // Hack to fix the url image loading bug
                        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
                        attributesPacket.setRuntimeEntityId(session.getPlayerEntity().geyserId());

                        AttributeData attribute = session.getPlayerEntity().getAttributes().get(GeyserAttributeType.EXPERIENCE_LEVEL);
                        if (attribute != null) {
                            attributesPacket.setAttributes(Collections.singletonList(attribute));
                        } else {
                            attributesPacket.setAttributes(Collections.singletonList(GeyserAttributeType.EXPERIENCE_LEVEL.getAttribute(0)));
                        }

                        session.sendUpstreamPacket(attributesPacket);
                    }, 500, TimeUnit.MILLISECONDS);
                }), 500, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * @return whether a settings form may be built for a request now
     */
    public boolean allowSettingsRequest() {
        long now = System.currentTimeMillis();
        if (now - lastSettingsRequest < SETTINGS_REQUEST_COOLDOWN_MILLIS) {
            return false;
        }
        lastSettingsRequest = now;
        return true;
    }

    /**
     * Resends pending forms after the client closed a container, which would have hidden them.
     */
    public void resendAfterContainerClose() {
        long now = System.currentTimeMillis();
        if (forms.isEmpty() || now - lastContainerCloseResend < CONTAINER_CLOSE_RESEND_COOLDOWN_MILLIS) {
            return;
        }
        lastContainerCloseResend = now;
        resendAllForms();
    }

    public void resendAllForms() {
        for (Int2ObjectMap.Entry<Form> entry : forms.int2ObjectEntrySet()) {
            sendForm(entry.getIntKey(), entry.getValue());
        }
    }

    public void handleResponse(ModalFormResponsePacket response) {
        int formId = response.getFormId();
        Form form = forms.get(formId);
        if (form == null) {
            return;
        }
        // MINEZ：客户端正忙（例如上一个表单或界面还没有关完）时拒绝打开，回复的理由为 USER_BUSY。这不是玩家关闭了表单，
        // 照关闭处理的话，对话框会执行“关闭”的动作，例如点了“返回”，服务器打开的上一级对话框却被立即关闭。稍后重新发送，多次仍忙时才照关闭处理。
        if (response.getCancelReason().orElse(null) == ModalFormCancelReason.USER_BUSY) {
            long now = clock.getAsLong();
            if (!busySince.containsKey(formId)) {
                busySince.put(formId, now);
            }
            if (now - busySince.get(formId) < BUSY_RETRY_WINDOW_MILLIS) {
                int retries = busyRetries.get(formId) + 1;
                busyRetries.put(formId, retries);
                // 同一个表单只在开始放慢时与此后每分钟记一次，免得客户端长时间忙时刷日志。
                if (retries <= BUSY_FAST_RETRIES || retries % 60 == 0) {
                    session.getGeyser().getLogger().debug("The client was busy and rejected form " + formId + " for " + session.bedrockUsername()
                        + ", sending it again (" + retries + ")");
                }
                session.scheduleInEventLoop(() -> {
                    if (forms.get(formId) == form) {
                        sendForm(formId, form);
                    }
                }, retries <= BUSY_FAST_RETRIES ? BUSY_FAST_DELAY_MILLIS : BUSY_SLOW_DELAY_MILLIS, TimeUnit.MILLISECONDS);
                return;
            }
            session.getGeyser().getLogger().debug("The client stayed busy for form " + formId + " of " + session.bedrockUsername()
                + ", treating it as closed");
        }
        forms.remove(formId);
        busyRetries.remove(formId);
        busySince.remove(formId);

        try {
            formDefinitions.definitionFor(form)
                    .handleFormResponse(form, response.getFormData());
        } catch (Exception e) {
            GeyserImpl.getInstance().getLogger().error("Error while processing form response!", e);
        }
    }

    /**
     * MINEZ：关掉所有表单，但不调用它们的处理器。登录闸门结束时用，免得插件的关闭处理器（例如重新显示表单）在闸门结束后还被调用。
     */
    @VisibleForTesting
    void setClock(LongSupplier clock) {
        this.clock = clock;
    }

    public void discardForms() {
        busyRetries.clear();
        busySince.clear();
        if (!this.forms.isEmpty()) {
            this.forms.clear();
            session.sendUpstreamPacket(new ClientboundCloseFormPacket());
        }
    }

    public void closeForms() {
        busyRetries.clear();
        busySince.clear();
        if (!this.forms.isEmpty()) {
            // Copy them to ensure any response handler's sent form isn't instantly cleared
            Int2ObjectMap<Form> copy = new Int2ObjectOpenHashMap<>(this.forms);
            this.forms.clear();
            // Now close it
            session.sendUpstreamPacket(new ClientboundCloseFormPacket());

            for (Form form : copy.values()) {
                try {
                    formDefinitions.definitionFor(form).handleFormResponse(form, "");
                } catch (Exception e) {
                    GeyserImpl.getInstance().getLogger().error("Error while closing form!", e);
                }
            }
        }
    }
}
