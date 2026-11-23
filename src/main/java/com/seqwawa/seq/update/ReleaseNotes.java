package com.seqwawa.seq.update;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A release's notes, read from the Markdown its GitHub release holds (docs/development.md, "Write
 * the release notes"): a short summary of plain lines under the release's heading, then the full
 * notes as section headings, items, sub-items and paragraphs, with bold, code and link text inline.
 *
 * <p>Reading never fails: anything outside that subset is kept as plain text, so a release written
 * before the summary existed shows its items as they are.
 */
public record ReleaseNotes(List<String> summary, List<Block> blocks) {
    public static final ReleaseNotes EMPTY = new ReleaseNotes(List.of(), List.of());

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$");
    private static final Pattern ITEM = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    /** Deeper items are drawn at this depth: the notes keep to items and sub-items. */
    private static final int MAX_DEPTH = 2;

    public ReleaseNotes {
        summary = List.copyOf(summary);
        blocks = List.copyOf(blocks);
    }

    /** One piece of the full notes, in reading order. */
    public sealed interface Block {}

    /** A section's name, such as New, Changed or Fixed. */
    public record Heading(String text) implements Block {}

    /** An item: depth 0 is a top-level item, 1 a sub-item under the one before. */
    public record Item(int depth, List<Span> text) implements Block {
        public Item {
            text = List.copyOf(text);
        }
    }

    /** Text that is not an item, such as a closing remark. */
    public record Paragraph(List<Span> text) implements Block {
        public Paragraph {
            text = List.copyOf(text);
        }
    }

    /** A run of text, bold or not. */
    public record Span(String text, boolean bold) {}

    public boolean isEmpty() {
        return summary.isEmpty() && blocks.isEmpty();
    }

    /** Reads {@code markdown}; null or blank gives {@link #EMPTY}. */
    public static ReleaseNotes parse(String markdown) {
        if (markdown == null || markdown.isBlank()) return EMPTY;
        String[] lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int at = 0;
        while (at < lines.length && lines[at].isBlank()) at++;
        // The release's own heading names it; the screens showing the notes name it themselves.
        Matcher title = at < lines.length ? HEADING.matcher(lines[at]) : null;
        if (title != null && title.matches() && title.group(1).length() <= 2) at++;

        List<String> summary = new ArrayList<>();
        for (; at < lines.length; at++) {
            String line = lines[at];
            if (HEADING.matcher(line).matches() || ITEM.matcher(line).matches()) break;
            if (!line.isBlank()) summary.add(plain(inline(line.strip())));
        }

        List<Block> blocks = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int depth = -1; // the open item's depth, or -1 for an open paragraph
        boolean afterBlank = false;
        for (; at < lines.length; at++) {
            String line = lines[at];
            Matcher heading = HEADING.matcher(line);
            Matcher item = ITEM.matcher(line);
            if (line.isBlank()) {
                afterBlank = true;
                continue;
            }
            if (heading.matches()) {
                flush(blocks, text, depth);
                if (!heading.group(2).isBlank()) blocks.add(new Heading(plain(inline(heading.group(2)))));
                depth = -1;
            } else if (item.matches()) {
                flush(blocks, text, depth);
                depth = Math.min(MAX_DEPTH - 1, indent(item.group(1)) / 2);
                text.append(item.group(2).strip());
            } else if (!text.isEmpty() && (depth >= 0 ? !afterBlank || Character.isWhitespace(line.charAt(0)) : !afterBlank)) {
                // A line straight after an item or a paragraph, or indented under an item, carries it on.
                text.append(' ').append(line.strip());
            } else {
                flush(blocks, text, depth);
                depth = -1;
                text.append(line.strip());
            }
            afterBlank = false;
        }
        flush(blocks, text, depth);
        return new ReleaseNotes(summary, blocks);
    }

    private static void flush(List<Block> blocks, StringBuilder text, int depth) {
        if (text.isEmpty()) return;
        List<Span> spans = inline(text.toString());
        blocks.add(depth >= 0 ? new Item(depth, spans) : new Paragraph(spans));
        text.setLength(0);
    }

    /** Columns of indentation, a tab counting as four. */
    private static int indent(String whitespace) {
        int columns = 0;
        for (char c : whitespace.toCharArray()) columns += c == '\t' ? 4 : 1;
        return columns;
    }

    /**
     * Splits {@code text} into plain and bold runs. {@code **bold**} and {@code __bold__} are bold,
     * {@code `code`} keeps its text, a link keeps its text, and a backslash keeps the character
     * after it. A marker without its closing pair stays as written.
     */
    static List<Span> inline(String text) {
        List<Span> spans = new ArrayList<>();
        StringBuilder run = new StringBuilder();
        boolean bold = false;
        int boldStart = -1; // where the open bold run began in the source, to put back an unclosed marker
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && "\\`*_[]()#+-.!".indexOf(text.charAt(i + 1)) >= 0) {
                run.append(text.charAt(i + 1));
                i += 2;
            } else if ((c == '*' || c == '_') && text.startsWith(String.valueOf(c).repeat(2), i)) {
                String marker = String.valueOf(c).repeat(2);
                if (bold) {
                    add(spans, run, true);
                    bold = false;
                    i += 2;
                } else if (text.indexOf(marker, i + 2) > i + 2) {
                    add(spans, run, false);
                    bold = true;
                    boldStart = i;
                    i += 2;
                } else {
                    run.append(marker);
                    i += 2;
                }
            } else if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    run.append(text, i + 1, end);
                    i = end + 1;
                } else {
                    run.append(c);
                    i++;
                }
            } else if (c == '[') {
                int close = text.indexOf("](", i + 1);
                int end = close < 0 ? -1 : text.indexOf(')', close + 2);
                if (close > i && end > close) {
                    run.append(text, i + 1, close);
                    i = end + 1;
                } else {
                    run.append(c);
                    i++;
                }
            } else {
                run.append(c);
                i++;
            }
        }
        if (bold) {
            // Never closed after all (the closing pair was inside code or a link): put the marker back.
            run.insert(0, text, boldStart, boldStart + 2);
        }
        add(spans, run, false);
        return merge(spans);
    }

    private static void add(List<Span> spans, StringBuilder run, boolean bold) {
        if (!run.isEmpty()) spans.add(new Span(run.toString(), bold));
        run.setLength(0);
    }

    /** Joins neighbouring runs of the same weight. */
    private static List<Span> merge(List<Span> spans) {
        List<Span> merged = new ArrayList<>();
        for (Span span : spans) {
            if (!merged.isEmpty() && merged.getLast().bold() == span.bold()) {
                merged.set(merged.size() - 1, new Span(merged.getLast().text() + span.text(), span.bold()));
            } else {
                merged.add(span);
            }
        }
        return merged;
    }

    /** The text of {@code spans} without their weight. */
    public static String plain(List<Span> spans) {
        StringBuilder text = new StringBuilder();
        for (Span span : spans) text.append(span.text());
        return text.toString();
    }
}
