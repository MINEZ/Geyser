/*
 * Copyright (c) 2025 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.session.dialog;

import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.dialog.action.DialogAction;
import org.geysermc.geyser.session.dialog.input.ParsedInputs;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.translator.text.TextTranslator;
import org.geysermc.geyser.translator.text.MessageTranslator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Used to manage an open dialog. Handles dialog input, transferring to other dialogs, and dialog "submenus" for e.g. waiting on a new dialog or confirming a command.
 *
 * <p>This is passed to a {@link Dialog} when using it to send a form to the client, it uses the {@link DialogHolder#runButton(Optional, ParsedInputs)} and {@link DialogHolder#closeDialog(Optional)} methods.</p>
 *
 * <p>
 *     To make it easier to understand what this class does and why it does it, here is the summed up behaviour of dialogs on Java.
 *     Java dialogs consist of inputs and buttons that run actions. Dialogs also have an "on cancel"/closing action, which is usually executed when the user presses ESC, or presses an "exit" button, defined by the dialog
 *     (note: not all dialog types can have an exit button). Dialogs can disallow closing by pressing ESC. Geyser translates clicking the "X" in the corner of a form as pressing ESC.</p>
 *
 * <p>
 *     Dialog inputs are quite simple. The user can enter what they want, and the inputs will clear once the dialog has been closed (note: <em>only</em> once the dialog has been closed. This becomes important later!).
 * </p>
 *
 * <p>
 *     Dialog actions are more complicated. Dialogs can define what to do after an action has been executed (so-called "after action" behaviour). When executing an action, if the action:
 * </p>
 * <ul>
 *    <li>Opens a new dialog: the new dialog is opened, the old one is closed, its closing action not executed.</li>
 *    <li>Executes with "NONE" set as after action: the dialog is kept open, its current input kept. This means the dialog can only be closed by pressing ESC (when allowed), or by an exit button, if it exists.</li>
 *    <li>Executes with "CLOSE" as after action: the dialog is closed, its closing action not executed.</li>
 *    <li>Executes with "WAIT_FOR_RESPONSE" as after action: the dialog is closed, its closing action not executed. A new, temporary screen is opened telling the user Minecraft is waiting on a response from the server.<br>
 *        The server must then send a new dialog within 5 seconds. After this period, a "back" button will appear, allowing the user to go back into the game if no new dialog appears.
 *    </li>
 * </ul>
 *
 * <p>If a new dialog is opened whilst another dialog is open, the old dialog is closed and the new dialog takes its place. The closing action of the dialog is not executed.</p>
 *
 * <p>
 *     All of this behaviour must be emulated by Geyser. That said, here are some of the things that this class must handle:
 * </p>
 *
 * <ul>
 *     <li>Executing actions with after actions properly. Actions that run commands or the "WAIT_FOR_RESPONSE" after action make this especially complicated.<br>
 *         In the case of commands that require operator permissions or the previously mentioned after action, Geyser must open a temporary form asking the user for confirmation or telling the user to wait.
 *     </li>
 *     <li>Remember form input and restore it after returning to this dialog, e.g. by cancelling a command execution or by the "NONE" after action.</li>
 *     <li>
 *         Properly close this dialog and open other dialogs - for example, bedrock/Cumulus likes to call "close" handlers a lot, including when the client closes a currently open form
 *         to open a new one. As such, every time we do something, we must make sure this dialog is still considered open.
 *     </li>
 * </ul>
 *
 * <p>Final note: when reading through this code, a dialog is "valid" when it is still considered open.</p>
 */
@Accessors(fluent = true)
public final class DialogHolder implements TextTranslator {
    @Getter
    private final GeyserSession session;
    private final DialogManager manager;
    private final Dialog dialog;

    /**
     * The time at which the "wait for response" screen was sent. Used to track when to show the "back" button.
     */
    private long responseWaitTime = 0;
    /**
     * If the "wait for response" screen is currently open and the "back" button should be shown.
     */
    private boolean sendBackButton = false;
    /**
     * If the dialog should be closed as soon as possible (likely after a "confirm running command" screen).
     */
    private boolean shouldClose = false;
    private ParsedInputs lastInputs;
    /**
     * MINEZ：刚执行的按钮是否交给服务器处理，见 {@link #runButton(Optional, ParsedInputs)}。
     */
    private boolean waitForServer = false;
    /**
     * MINEZ：after action 为 none 的对话框执行了交给服务器的按钮后，等待服务器打开或关闭对话框的时长，之后才重新打开这一个。
     */
    private static final long SERVER_RESPONSE_WAIT_MILLIS = 1000;

    public DialogHolder(GeyserSession session, DialogManager manager, Dialog dialog) {
        this.session = session;
        this.manager = manager;
        this.dialog = dialog;
    }

    @Override
    public String convertText(Component component) {
        return MessageTranslator.convertMessage(component, session.locale());
    }

    /**
     * MINEZ：转换对话框的纯文本正文，按 Markdown 排版，见 {@link DialogMarkdown}。
     */
    public String convertBody(Component component) {
        return FormColors.onDark(MessageTranslator.convertDialogBody(component, session.locale()));
    }

    /**
     * MINEZ：转换对话框的标题。标题栏是浅色的，颜色按 {@link FormColors#onLight(String)} 替换。
     */
    public String convertTitle(Component component) {
        return FormColors.onLight(convertText(component));
    }

    /**
     * MINEZ：转换下拉框中按钮的文字，颜色按 {@link FormColors#onLight(String)} 替换。
     */
    public String convertOption(DialogButton button) {
        return FormColors.onLight(convertText(button.label()));
    }

    /**
     * MINEZ：基岩版表单按钮每行能完整显示的宽度，按 {@link #displayWidth(String)} 计（全角字符算 2）。按钮固定宽度、不折行，超出的部分
     * 以省略号截掉（2026-11-18 实测，电脑端：商城的“出售超值礼包，礼包中的物品与冷却重置次数仅限当…”在第 22 个字处截断，
     * “出售仅限当前周目、当前分区使用的物品。”19 个字完整显示）。取 40，即 20 个汉字。
     */
    static final int BUTTON_LINE_WIDTH = 40;

    /**
     * MINEZ：按钮的文字。基岩版的按钮没有悬停提示，最多也只显示两行：有提示时把提示的各行以两个半角空格连成一行，写在按钮文字的下一行，
     * 免得其中的说明（例如在线人数）看不到。{@code common} 中的行（各按钮共同的一行，见 {@link #commonTooltipLines(List)}）不写在按钮上，
     * 由 {@link #convertTooltipNotes(List)} 在正文之后写一次。提示在按钮上一行写不下时（{@link #BUTTON_LINE_WIDTH}），按钮只写名称，
     * 提示由 {@link #convertOverflowNotes(List, List, boolean)} 完整地写在正文之后，免得被截断。
     */
    public String convertButton(DialogButton button, List<String> common) {
        // 按钮是浅色的，颜色按 FormColors#onLight 替换。
        String label = convertText(button.label());
        String detail = buttonDetail(button, common);
        return FormColors.onLight(detail.isEmpty() || !fitsButtonLine(detail) ? label : label + "\n" + detail);
    }

    /** 按钮上写在名称下一行的提示：不在 {@code common} 中的各行，以两个半角空格连成一行（全角空格在基岩版显示为方框，见 MessageTranslator）；没有时为空文本。 */
    private String buttonDetail(DialogButton button, List<String> common) {
        return button.tooltip()
            .map(tooltip -> Arrays.stream(tooltipLines(tooltip)).filter(line -> !common.contains(line)).collect(Collectors.joining("  ")))
            .orElse("");
    }

    /**
     * MINEZ：按钮上一行写不下的提示（见 {@link #convertButton(DialogButton, List)}），每个按钮一行“• 名称：提示”，写在正文之后；
     * {@code all} 为 true 时不论写不写得下都列出，用于下拉框（选项只显示名称，见 {@link #convertOption(DialogButton)}）。没有时为空文本。
     */
    public String convertOverflowNotes(List<DialogButton> buttons, List<String> common, boolean all) {
        List<String> lines = new ArrayList<>();
        for (DialogButton button : buttons) {
            String detail = buttonDetail(button, common);
            if (!detail.isEmpty() && (all || !fitsButtonLine(detail))) {
                lines.add("§r• " + convertText(button.label()) + "§r：" + detail);
            }
        }
        return FormColors.onDark(String.join("\n", lines));
    }

    /** 文字能否在按钮的一行中完整显示，见 {@link #BUTTON_LINE_WIDTH}。 */
    static boolean fitsButtonLine(String text) {
        return displayWidth(text) <= BUTTON_LINE_WIDTH;
    }

    /**
     * 文字显示的宽度：颜色代码（§ 与其后一个字符）不计，全角字符（汉字、假名、谚文、全角标点与符号）算 2，其余算 1。
     */
    static int displayWidth(String text) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int c = text.codePointAt(i);
            i += Character.charCount(c);
            if (c == '§') {
                if (i < text.length()) {
                    i += Character.charCount(text.codePointAt(i));
                }
                continue;
            }
            width += isWide(c) ? 2 : 1;
        }
        return width;
    }

    private static boolean isWide(int c) {
        return (c >= 0x1100 && c <= 0x115F) || (c >= 0x2E80 && c <= 0xA4CF) || (c >= 0xAC00 && c <= 0xD7A3)
            || (c >= 0xF900 && c <= 0xFAFF) || (c >= 0xFE30 && c <= 0xFE4F) || (c >= 0xFF00 && c <= 0xFF60)
            || (c >= 0xFFE0 && c <= 0xFFE6) || (c >= 0xE000 && c <= 0xF8FF) || c >= 0x1F300;
    }

    /** MINEZ：按钮的文字，提示的各行都写在按钮上，见 {@link #convertButton(DialogButton, List)}。 */
    public String convertButton(DialogButton button) {
        return convertButton(button, List.of());
    }

    /**
     * MINEZ：两个以上的按钮的提示都有的行（例如各分区共同的注意事项）。这些行不逐个写在按钮上，只在正文之后写一次。
     */
    public List<String> commonTooltipLines(List<DialogButton> buttons) {
        List<List<String>> all = new ArrayList<>();
        for (DialogButton button : buttons) {
            button.tooltip().ifPresent(tooltip -> all.add(List.of(tooltipLines(tooltip))));
        }
        List<String> common = new ArrayList<>();
        if (all.size() >= 2) {
            for (String line : all.getFirst()) {
                if (all.stream().allMatch(lines -> lines.contains(line)) && !common.contains(line)) {
                    common.add(line);
                }
            }
        }
        return common;
    }

    /**
     * MINEZ：各按钮共同的提示（{@link #commonTooltipLines(List)}），写在正文之后；没有时为空文本。
     */
    public String convertTooltipNotes(List<String> common) {
        return FormColors.onDark(String.join("\n", common));
    }

    /** 提示的各行，没有颜色的文字为灰色。 */
    private String[] tooltipLines(Component tooltip) {
        return convertText(tooltip.colorIfAbsent(NamedTextColor.GRAY)).split("\n");
    }

    /**
     * Checks if this dialog is still valid, and if so, runs the given button (if present) with the given inputs.
     * These inputs can be {@link ParsedInputs#EMPTY} when the dialog has no inputs, but can never be {@code null}. This method also runs the dialog's after action.
     */
    public void runButton(Optional<DialogButton> button, @NonNull ParsedInputs inputs) {
        lastInputs = inputs;
        if (stillValid()) {
            if (runAction(button, lastInputs)) {
                // MINEZ：按钮交给服务器处理时（执行命令、自定义动作），记下来，after action 为 none 时稍后再重新打开，见 runAfterAction。
                waitForServer = button.flatMap(DialogButton::action).map(DialogHolder::isHandledByServer).orElse(false);
                runAfterAction();
            }
        }
    }

    /**
     * MINEZ：按钮的动作是否交给服务器处理，服务器多半会接着打开别的对话框或关闭这一个。
     */
    private static boolean isHandledByServer(DialogAction action) {
        return action instanceof DialogAction.CommandAction || action instanceof DialogAction.Custom || action instanceof DialogAction.DynamicCustom;
    }

    /**
     * Ticks this dialog. Ticks are only used to check when to show the "back" button on the "waiting for response" screen.
     */
    public void tick() {
        // Replace wait form with one with a back button if no replacement dialog was given
        if (responseWaitTime > 0 && !sendBackButton && System.currentTimeMillis() - responseWaitTime > 5000) {
            sendBackButton = true;
            session.closeForm(); // Automatically reopens with a back button
        }
    }

    // Called when clicking the X in the corner on a form, which we interpret as clicking escape
    // Note that this method is called from the "closedOrInvalidResultHandler",
    // meaning it can also be called when e.g. the bedrock client opens another form or is unable to open the form sent to it
    /**
     * Should be called when pressing "ESC", i.e., clicking the X in the corner of the form. This method checks if the dialog is still valid, and if so,
     * closes it if the dialog allows closing by pressing ESC. If not, the dialog is reopened.
     *
     * <p>If the dialog was closed successfully, the given close action is also executed, if present.</p>
     */
    public void closeDialog(Optional<DialogButton> onCancel) {
        if (!stillValid()) {
            return;
        }

        // Don't run close functionality if we're asking for command confirmation
        if (dialog.canCloseWithEscape()) {
            shouldClose = true;
            if (runAction(onCancel, lastInputs == null ? dialog.defaultInputs() : lastInputs)) {
                manager.close();
            }
            return;
        }

        // If player should not have been able to close the dialog, reopen it with the last inputs
        reopenDialog();
    }

    /**
     * Tries to reopen the dialog. First checks if the dialog is still valid. If it is, it checks if the dialog should close,
     * and if so, closes the dialog. If not, the dialog is reopened, with its inputs restored if possible.
     */
    private void reopenDialog() {
        if (stillValid()) {
            if (shouldClose) {
                manager.close();
            } else {
                responseWaitTime = 0;

                // lastInputs might be null here since it's possible none were sent yet
                // Bedrock doesn't send them when just closing the form
                if (lastInputs == null) {
                    dialog.sendForm(this);
                } else {
                    dialog.restoreForm(this, lastInputs);
                }
            }
        }
    }

    /**
     * Runs the dialog's after action. This method assumes the dialog is still valid!
     */
    private void runAfterAction() {
        switch (dialog.afterAction()) {
            case NONE -> {
                // If no new dialog was opened, reopen this one
                if (waitForServer) {
                    // MINEZ：Java 版点了按钮后对话框留在原处，直到服务器打开别的对话框；基岩版的表单点了按钮就已关闭。
                    // 立即重新打开的话，服务器紧接着打开的对话框（例如“返回”通向的上一级）与它几乎同时到达，客户端来不及关掉它，
                    // 它便留在新对话框之下：看起来像是同一个对话框打开了两次，关掉新对话框后露出的这一个已经失效，点什么都没有反应。
                    // 因此先等服务器一会儿，期间服务器打开或关闭了对话框就不再打开。
                    waitForServer = false;
                    session.scheduleInEventLoop(() -> {
                        if (stillValid()) {
                            dialog.restoreForm(this, lastInputs);
                        }
                    }, SERVER_RESPONSE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
                } else {
                    dialog.restoreForm(this, lastInputs);
                }
            }
            case CLOSE -> {
                // If no new dialog was opened, tell the manager this one is now closed
                manager.close();
            }
            case WAIT_FOR_RESPONSE -> {
                // If no new dialog was opened, open a form telling the user we're waiting on a response from the server
                // This dialog is replaced with a similar form with a "back" button after 5 seconds, matching Java behaviour
                responseWaitTime = System.currentTimeMillis();
                sendBackButton = false;
                waitForResponse();
            }
        }
    }

    /**
     * Opens a "waiting for response" form. This method assumes the dialog is still valid!
     */
    private void waitForResponse() {
        String content;
        if (sendBackButton) {
            content = GeyserLocale.getPlayerLocaleString("geyser.dialogs.waiting_for_a_while", session.locale());
        } else {
            content = GeyserLocale.getPlayerLocaleString("geyser.dialogs.waiting_for_response", session.locale());
        }

        session.sendDialogForm(SimpleForm.builder()
            .translator(MinecraftLocale::getLocaleString, session.locale())
            .title("gui.waitingForResponse.title")
            .content(content)
            .optionalButton("gui.back", sendBackButton)
            .closedOrInvalidResultHandler(() -> {
                if (stillValid()) { // If still waiting on a new dialog
                    waitForResponse();
                }
            })
            .validResultHandler(response -> manager.close()) // Back button was pressed, meaning no new dialog was sent
            .build());
    }

    /**
     * This method runs the given action, if present, with the given inputs.
     *
     * <p>These inputs can be {@link ParsedInputs#EMPTY} when the dialog has no inputs, but can never be {@code null}.
     * The method returns {@code true} if the dialog's after action can be executed, and {@code false} if not. The latter is the case when the action opened a new
     * dialog or screen, in which case the after action will not be handled or be handled by the screen, respectively.</p>
     *
     * <p>This method assumes the dialog is still valid!</p>
     */
    private boolean runAction(Optional<DialogButton> button, @NonNull ParsedInputs inputs) {
        DialogAction action = button.flatMap(DialogButton::action).orElse(null);
        if (action != null) {
            // Ask the user for confirmation if the dialog wants to run an unknown command or a command that requires operator permissions
            if (action instanceof DialogAction.CommandAction runCommand) {
                String command = runCommand.trimmedCommand(session, inputs);
                String root = command.split(" ")[0];

                // This check is not perfect. Ideally we'd check the entire command and see if any of its arguments require operator permissions, but, that's complicated
                if (session.getRestrictedCommands().contains(root)) {
                    showCommandConfirmation(command, false);
                    return false;
                } else if (!session.getKnownCommands().contains(root)) {
                    showCommandConfirmation(command, true);
                    return false;
                }

                session.sendCommand(command);
                return true;
            } else if (action instanceof DialogAction.OpenUrl(String url)) {
                showUrl(url);
                return false;
            } else if (action instanceof DialogAction.CopyToClipboard(String value)) {
                // MINEZ：由客户端模组复制；不能时显示文字，让玩家自己选中复制。
                if (session.getMinezClientUi().copyToClipboard(value)) {
                    return true;
                }
                showCopyText(value);
                return false;
            } else {
                action.run(session, inputs);
                return !(action instanceof DialogAction.ShowDialog);
            }
        }
        return true;
    }

    /**
     * Opens an "are you sure you want to do this?" form. After confirmation, runs the command and the after action, or closes
     * the dialog if it should be closed. When cancelled, returns back to the dialog, matching Java behaviour. This method assumes the dialog is still valid!
     */
    private void showCommandConfirmation(String trimmedCommand, boolean unknown) {
        Component content = Component.translatable(unknown ? "multiplayer.confirm_command.parse_errors" : "multiplayer.confirm_command.permissions_required",
            Component.text(trimmedCommand).color(NamedTextColor.YELLOW));

        session.sendDialogForm(ModalForm.builder()
            .translator(MinecraftLocale::getLocaleString, session.locale())
            .title("multiplayer.confirm_command.title")
            .content(MessageTranslator.convertMessage(session, content))
            .button1("gui.yes")
            .button2("gui.no")
            .closedOrInvalidResultHandler(() -> {
                // Upon pressing "no" (or closing the form), we should return back to the dialog, even if it was supposed to close
                shouldClose = false;
                // Checks stillValid
                reopenDialog();
            })
            .validResultHandler(response -> {
                // stillValid check not needed here - valid result means the button was pressed, meaning no new dialog took over and closed this form
                if (response.clickedFirst()) {
                    session.sendCommand(trimmedCommand);
                    if (shouldClose) {
                        manager.close();
                    } else {
                        waitForServer = true;
                        runAfterAction();
                    }
                } else {
                    // Pressed no, go back to dialog
                    shouldClose = false;
                    reopenDialog();
                }
            })
            .build());
    }

    /**
     * Opens a form to let the user know they should open a URL. Runs the after action when closed, or closes the dialog if it should be
     * closed. This method assumes the dialog is still valid!
     */
    private void showUrl(String url) {
        String content = MessageTranslator.convertMessage(session,
                Component.text(GeyserLocale.getPlayerLocaleString("geyser.dialogs.open_url", session.locale()))
                        .append(Component.text("\n\n"))
                        .append(Component.text(url))
                        .append(Component.text("\n\n"))
                        .append(Component.translatable("chat.link.warning").color(NamedTextColor.RED)));

        session.sendDialogForm(SimpleForm.builder()
                .translator(MinecraftLocale::getLocaleString, session.locale())
                .title("chat.link.open")
                .content(content)
                .button("gui.ok")
                .resultHandler((form, result) -> {
                    if (stillValid()) {
                        if (shouldClose) {
                            manager.close();
                        } else {
                            runAfterAction();
                        }
                    }
                })
                .build());
    }

    /**
     * MINEZ：不能直接复制到剪贴板时，在输入框中显示要复制的文字，玩家可以自己选中复制。关闭后照常执行 after action。
     */
    private void showCopyText(String value) {
        session.sendDialogForm(CustomForm.builder()
                .translator(MinecraftLocale::getLocaleString, session.locale())
                .title("chat.copy")
                .input("", "", value)
                .resultHandler((form, result) -> {
                    if (stillValid()) {
                        if (shouldClose) {
                            manager.close();
                        } else {
                            runAfterAction();
                        }
                    }
                })
                .build());
    }

    /**
     * @return true if the dialog currently open is this dialog.
     */
    private boolean stillValid() {
        return manager.open() == this;
    }
}
