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

package org.geysermc.geyser.session.dialog;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MINEZ：把对话框正文中的 Markdown 转成基岩版表单能显示的文字。
 * <p>
 * MINEZ 的 Java 版客户端（MINEZ/Bootstrap 的 Bridge 模组）把对话框的纯文本正文按 Markdown 排版，服务器的正文因此写着标题、列表、表格、引用等标记；
 * 基岩版的表单只能显示带颜色代码的纯文本，标记会原样显示。这里按 Bridge 的规则认出这些标记，换成基岩版能显示的样子：
 * <ul>
 *     <li>标题：去掉“#”，整行加粗。</li>
 *     <li>列表：“-”“*”“+”换成“•”，有序列表保留序号，子列表按缩进加空格。</li>
 *     <li>引用：去掉“>”，行首加灰色的竖条，没有颜色的文字为灰色。</li>
 *     <li>表格：有表头时每行数据写成一个列表项，第一格之后接“：”，多列时其余各格前写上表头，表头与对齐行不输出；没有表头时各格以灰色的“|”隔开。</li>
 *     <li>分隔线：换成一行灰色的横线。</li>
 *     <li>行内：“**”加粗、“*”斜体、“~~”删除线，行内代码与代码块原样显示，链接写出文字与地址，反斜杠转义的字符原样显示。</li>
 * </ul>
 * 与 Bridge 相同：换行即换行；下划线不表示强调；删除线须用两个波浪号；不解析 HTML。服务器的颜色代码常写在块标记之前（例如“§6## 标题”“§7- §f内容”），
 * 它们照常生效，列表的圆点或序号取行首的颜色。
 * <p>
 * 输入与输出都是 Java 版语义的颜色代码文本（颜色代码会清除粗体等格式），交给 {@code MessageTranslator} 转成基岩版的文字。
 */
public final class DialogMarkdown {
    private static final char SECTION = '§';
    private static final String COLORS = "0123456789abcdefr";
    private static final String FORMATS = "klmno";
    /** 引用行首的竖条与它的颜色。 */
    private static final String QUOTE_PREFIX = "§7▎ ";
    /** 引用中没有颜色的文字的颜色。 */
    private static final String QUOTE_TEXT = "§7";
    /** 表格各格之间的分隔符的颜色。 */
    private static final String CELL_SEPARATOR = " §7|";
    private static final String RULE = "§7────────────";
    /** 每一级子列表缩进的空格。 */
    private static final String LIST_INDENT = "    ";

    private static final Pattern HEADING = Pattern.compile("(#{1,6})(?:[ \\t]+(.*?))?(?:[ \\t]+#+)?[ \\t]*");
    private static final Pattern BULLET = Pattern.compile("[-*+](?:[ \\t]+(.*))?");
    private static final Pattern ORDERED = Pattern.compile("(\\d{1,9})[.)](?:[ \\t]+(.*))?");
    private static final Pattern THEMATIC_BREAK = Pattern.compile("([-*_])[ \\t]*(?:\\1[ \\t]*){2,}");
    private static final Pattern DELIMITER_ROW = Pattern.compile("\\|?[ \\t]*:?-+:?[ \\t]*(?:\\|[ \\t]*:?-+:?[ \\t]*)*\\|?[ \\t]*");
    private static final Pattern FENCE = Pattern.compile("(?:```|~~~).*");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\(([^)\\s]*)\\)");
    private static final Pattern AUTOLINK = Pattern.compile("<([A-Za-z][A-Za-z0-9.+-]{1,31}:[^\\x00-\\x20<>]*)>");

    /** 当前来源文字的样式：最近一个颜色代码（或重置）及其后的格式代码，Java 版语义。 */
    private String style = "";
    private final StringBuilder out = new StringBuilder();

    private DialogMarkdown() {
    }

    /**
     * @param text 带颜色代码的正文。
     * @return 排版后的文字，仍是带颜色代码的纯文本。
     */
    public static String render(String text) {
        DialogMarkdown renderer = new DialogMarkdown();
        renderer.renderLines(text.split("\n", -1));
        return renderer.out.toString();
    }

    private void renderLines(String[] lines) {
        boolean inFence = false;
        // 不输出的一行（代码围栏）中的颜色代码，移到下一行的开头。
        StringBuilder carried = new StringBuilder();
        boolean first = true;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (inFence) {
                if (FENCE.matcher(stripCodes(line).strip()).matches()) {
                    inFence = false;
                    carried.append(codesOf(line));
                    applyCodes(line);
                    continue;
                }
                newLine(first);
                first = false;
                out.append(carried);
                carried.setLength(0);
                appendRaw(line);
                continue;
            }

            Leading leading = leading(line);
            String rest = moveCodesAfterToken(line.substring(leading.end));
            String plainRest = stripCodes(rest).strip();

            if (FENCE.matcher(plainRest).matches() && !plainRest.startsWith("~~~~")) {
                inFence = true;
                carried.append(leading.codes).append(codesOf(rest));
                applyCodes(rest);
                continue;
            }

            newLine(first);
            first = false;
            out.append(carried);
            carried.setLength(0);
            String codes = leading.codes;

            if (rest.isBlank()) {
                out.append(codes);
                continue;
            }
            if (rest.startsWith("|")) {
                // 连续的表格行作为一个表格处理。
                int end = index + 1;
                while (end < lines.length && stripCodes(lines[end]).strip().startsWith("|")) {
                    end++;
                }
                List<String> rows = new ArrayList<>();
                rows.add(rest);
                for (int row = index + 1; row < end; row++) {
                    rows.add(lines[row]);
                }
                out.append(codes);
                renderTable(rows, codes);
                index = end - 1;
                continue;
            }
            if (THEMATIC_BREAK.matcher(plainRest).matches() && rest.equals(plainRest)) {
                out.append(codes).append(RULE);
                restore("");
                continue;
            }
            Matcher heading = HEADING.matcher(rest);
            if (rest.startsWith("#") && heading.matches()) {
                out.append(codes).append("§l");
                inline(heading.group(2) == null ? "" : heading.group(2), "l");
                restore("");
                continue;
            }
            if (rest.startsWith(">")) {
                String content = rest.substring(1);
                if (content.startsWith(" ")) {
                    content = content.substring(1);
                }
                out.append(QUOTE_PREFIX).append(codes.isEmpty() ? QUOTE_TEXT : codes);
                inline(content, "");
                restore("");
                continue;
            }
            String indent = LIST_INDENT.repeat(leading.indent / 2);
            Matcher bullet = BULLET.matcher(rest);
            if (bullet.matches()) {
                String content = bullet.group(1) == null ? "" : bullet.group(1);
                // 与 Bridge 相同，圆点取这一行的第一个颜色：块标记之前没有颜色代码时，取内容开头的。
                out.append(indent).append(codes.isEmpty() ? codesOf(leadingCodes(content)) : codes).append("• ");
                inline(content, "");
                continue;
            }
            Matcher ordered = ORDERED.matcher(rest);
            if (ordered.matches()) {
                String content = ordered.group(2) == null ? "" : ordered.group(2);
                out.append(indent).append(codes.isEmpty() ? codesOf(leadingCodes(content)) : codes).append(ordered.group(1)).append(". ");
                inline(content, "");
                continue;
            }
            out.append(codes);
            inline(rest, "");
        }
        out.append(carried);
    }

    private void newLine(boolean first) {
        if (!first) {
            out.append('\n');
        }
    }

    /**
     * 块标记与其后的空白之间夹着颜色代码时（例如“§7-§f 内容”“§e1.§f 内容”），把这些代码移到空白之后，块标记才能被认出。
     */
    private static String moveCodesAfterToken(String rest) {
        int end = tokenEnd(rest);
        if (end < 0 || end >= rest.length() || rest.charAt(end) != SECTION) {
            return rest;
        }
        int next = end;
        while (next + 1 < rest.length() && rest.charAt(next) == SECTION) {
            next += 2;
        }
        if (next < rest.length() && rest.charAt(next) != ' ' && rest.charAt(next) != '\t') {
            return rest;
        }
        int textStart = next;
        while (textStart < rest.length() && (rest.charAt(textStart) == ' ' || rest.charAt(textStart) == '\t')) {
            textStart++;
        }
        return rest.substring(0, end) + rest.substring(next, textStart) + rest.substring(end, next) + rest.substring(textStart);
    }

    /** 若行首是块标记（标题、列表、引用），返回其后的位置，否则返回 -1。 */
    private static int tokenEnd(String rest) {
        if (rest.isEmpty()) {
            return -1;
        }
        char c = rest.charAt(0);
        if (c == '>' || c == '-' || c == '*' || c == '+') {
            return 1;
        }
        int end = 0;
        if (c == '#') {
            while (end < rest.length() && rest.charAt(end) == '#' && end < 6) {
                end++;
            }
            return end;
        }
        while (end < rest.length() && Character.isDigit(rest.charAt(end)) && end < 9) {
            end++;
        }
        if (end > 0 && end < rest.length() && (rest.charAt(end) == '.' || rest.charAt(end) == ')')) {
            return end + 1;
        }
        return -1;
    }

    /** 行首的颜色代码与空白。 */
    private record Leading(String codes, int indent, int end) {
    }

    /** 读出行首的颜色代码与空白，颜色代码同时生效。 */
    private Leading leading(String line) {
        StringBuilder codes = new StringBuilder();
        int indent = 0;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == SECTION && i + 1 < line.length()) {
                char code = Character.toLowerCase(line.charAt(i + 1));
                codes.append(SECTION).append(code);
                applyCode(code);
                i += 2;
            } else if (c == ' ') {
                indent++;
                i++;
            } else if (c == '\t') {
                indent += 4;
                i++;
            } else {
                break;
            }
        }
        return new Leading(codes.toString(), indent, i);
    }

    /**
     * 输出一个表格。有表头（第二行为对齐行）时，每行数据写成一个列表项：第一格之后接“：”，两列以上时其余各格前写上表头，
     * 例如“• /back 冷却：无月卡 7 天，黄金月卡 1 小时”，表头与对齐行本身不输出；基岩版的表单较窄，横排的多列很快就会折行得难以阅读。
     * 没有表头时，各行的各格以灰色的“|”隔开。
     *
     * @param rows  表格的各行，第一行已去掉行首的颜色代码与空白（它们在 leadingCodes 中，已经输出）。
     */
    private void renderTable(List<String> rows, String leadingCodes) {
        boolean hasHeader = rows.size() >= 2 && DELIMITER_ROW.matcher(stripCodes(rows.get(1)).strip()).matches()
            && stripCodes(rows.get(1)).contains("-");
        if (!hasHeader) {
            for (int i = 0; i < rows.size(); i++) {
                if (i > 0) {
                    out.append('\n');
                    Leading leading = leading(rows.get(i));
                    out.append(leading.codes);
                    renderTableRow(rows.get(i).substring(leading.end));
                } else {
                    renderTableRow(rows.getFirst());
                }
            }
            return;
        }
        List<String> headers = new ArrayList<>();
        for (String cell : splitCells(rows.getFirst())) {
            headers.add(unescape(stripCodes(cell)).strip());
        }
        // 表头与对齐行中的颜色代码照常生效。
        applyCodes(rows.getFirst());
        applyCodes(rows.get(1));
        // 表头与对齐行不输出；之前已经输出的行首颜色代码留给第一行数据。
        boolean firstRow = true;
        for (int i = 2; i < rows.size(); i++) {
            Leading leading = leading(rows.get(i));
            List<String> cells = splitCells(rows.get(i).substring(leading.end));
            if (!firstRow) {
                out.append('\n');
            }
            firstRow = false;
            String firstCell = cells.getFirst().strip();
            String bulletCodes = leading.codes.isEmpty() ? (leadingCodes.isEmpty() || i > 2 ? codesOf(leadingCodes(firstCell)) : "") : leading.codes;
            out.append(bulletCodes).append("• ");
            inline(firstCell, "");
            for (int c = 1; c < cells.size(); c++) {
                out.append(c == 1 ? "§7：" : "§7，");
                String header = c < headers.size() ? headers.get(c) : "";
                if (cells.size() > 2 && !header.isEmpty()) {
                    out.append(header).append(' ');
                }
                restore("");
                inline(cells.get(c).strip(), "");
            }
        }
    }

    /** 文字开头的颜色代码（含其间的空白之前的部分）。 */
    private static String leadingCodes(String text) {
        int i = 0;
        while (i + 1 < text.length() && text.charAt(i) == SECTION) {
            i += 2;
        }
        return text.substring(0, i);
    }

    /** 去掉反斜杠转义。 */
    private static String unescape(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && isEscapable(text.charAt(i + 1))) {
                builder.append(text.charAt(++i));
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private void renderTableRow(String row) {
        List<String> cells = splitCells(row);
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(CELL_SEPARATOR);
                restore("");
                out.append(' ');
            }
            inline(cells.get(i).strip(), "");
        }
    }

    /** 以没有转义、不在行内代码中的“|”分格，去掉行首与行尾的空格。 */
    private static List<String> splitCells(String row) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inCode = false;
        for (int i = 0; i < row.length(); i++) {
            char c = row.charAt(i);
            if (c == '\\' && i + 1 < row.length()) {
                cell.append(c).append(row.charAt(++i));
            } else if (c == '`') {
                inCode = !inCode;
                cell.append(c);
            } else if (c == '|' && !inCode) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        if (!cells.isEmpty() && stripCodes(cells.getFirst()).isBlank()) {
            String leadingCodes = codesOf(cells.removeFirst());
            if (!cells.isEmpty()) {
                cells.set(0, leadingCodes + cells.getFirst());
            }
        }
        if (cells.size() > 1 && stripCodes(cells.getLast()).isBlank()) {
            String trailingCodes = codesOf(cells.removeLast());
            cells.set(cells.size() - 1, cells.getLast() + trailingCodes);
        }
        return cells;
    }

    /**
     * 处理行内的格式。
     *
     * @param extras 外层由 Markdown 加上的格式代码（不含“§”），来源文字的颜色代码清除格式之后要重新加上。
     */
    private void inline(String text, String extras) {
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == SECTION && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                out.append(SECTION).append(code);
                applyCode(code);
                if (COLORS.indexOf(code) >= 0) {
                    appendFormats(extras);
                }
                i += 2;
                continue;
            }
            if (c == '\\' && i + 1 < text.length() && isEscapable(text.charAt(i + 1))) {
                out.append(text.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '`') {
                int close = text.indexOf('`', i + 1);
                if (close > i + 1) {
                    appendRaw(text.substring(i + 1, close));
                    i = close + 1;
                    continue;
                }
            }
            if (text.startsWith("**", i)) {
                int close = findClosing(text, "**", i + 2);
                if (close > i + 2) {
                    wrap(text.substring(i + 2, close), extras, 'l');
                    i = close + 2;
                    continue;
                }
            }
            if (text.startsWith("~~", i)) {
                int close = findClosing(text, "~~", i + 2);
                if (close > i + 2) {
                    wrap(text.substring(i + 2, close), extras, 'm');
                    i = close + 2;
                    continue;
                }
            }
            if (c == '*' && i + 1 < text.length() && text.charAt(i + 1) != ' ' && text.charAt(i + 1) != '*') {
                int close = findSingleStar(text, i + 1);
                if (close > i + 1) {
                    wrap(text.substring(i + 1, close), extras, 'o');
                    i = close + 1;
                    continue;
                }
            }
            if (c == '[') {
                Matcher link = LINK.matcher(text).region(i, text.length());
                if (link.lookingAt()) {
                    inline(link.group(1), extras);
                    if (!link.group(2).isEmpty()) {
                        out.append("§7（").append(link.group(2)).append("）");
                        restore(extras);
                    }
                    i = link.end();
                    continue;
                }
            }
            if (c == '<') {
                Matcher autolink = AUTOLINK.matcher(text).region(i, text.length());
                if (autolink.lookingAt()) {
                    out.append(autolink.group(1));
                    i = autolink.end();
                    continue;
                }
            }
            out.append(c);
            i++;
        }
    }

    /** 以给定的格式输出一段文字，之后恢复原来的样式。 */
    private void wrap(String content, String extras, char format) {
        String inner = extras.indexOf(format) >= 0 ? extras : extras + format;
        out.append(SECTION).append(format);
        inline(content, inner);
        restore(extras);
    }

    /** 清除 Markdown 加上的格式，恢复来源文字当前的样式，再加上外层仍然有效的格式。 */
    private void restore(String extras) {
        out.append(SECTION).append('r').append(style);
        appendFormats(extras);
    }

    private void appendFormats(String formats) {
        for (int i = 0; i < formats.length(); i++) {
            out.append(SECTION).append(formats.charAt(i));
        }
    }

    /** 原样输出，颜色代码照常生效。 */
    private void appendRaw(String text) {
        out.append(text);
        applyCodes(text);
    }

    private void applyCodes(String text) {
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == SECTION) {
                applyCode(Character.toLowerCase(text.charAt(i + 1)));
                i++;
            }
        }
    }

    private void applyCode(char code) {
        if (COLORS.indexOf(code) >= 0) {
            style = code == 'r' ? "" : String.valueOf(SECTION) + code;
        } else if (FORMATS.indexOf(code) >= 0) {
            style += String.valueOf(SECTION) + code;
        }
    }

    /** 找到没有转义的结束标记。 */
    private static int findClosing(String text, String marker, int from) {
        int index = text.indexOf(marker, from);
        while (index > 0 && text.charAt(index - 1) == '\\') {
            index = text.indexOf(marker, index + marker.length());
        }
        return index;
    }

    /** 找到单个星号（不是“**”的一部分、没有转义、前面不是空格）。 */
    private static int findSingleStar(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == '*') {
                if (i + 1 < text.length() && text.charAt(i + 1) == '*') {
                    i++;
                } else if (text.charAt(i - 1) != ' ') {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Markdown 中可以用反斜杠转义的 ASCII 标点。 */
    private static boolean isEscapable(char c) {
        return c < 128 && "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~".indexOf(c) >= 0;
    }

    private static String stripCodes(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == SECTION && i + 1 < text.length()) {
                i++;
            } else {
                builder.append(c);
            }
        }
        return builder.toString();
    }

    private static String codesOf(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == SECTION) {
                builder.append(SECTION).append(Character.toLowerCase(text.charAt(i + 1)));
                i++;
            }
        }
        return builder.toString();
    }
}
