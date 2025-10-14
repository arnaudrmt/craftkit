package fr.arnaud.abstractify.api;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Message builder that allows constructing complex, multi-line
 * clickable, hoverable, and centered messages safely.
 */
public class AbstractMessageBuilder {

    private final List<List<MessagePart>> lines = new ArrayList<>();
    private boolean center = false;

    /**
     * Adds a new line to the message.
     *
     * @return this builder
     */
    public AbstractMessageBuilder newLine() {
        lines.add(new ArrayList<>());
        return this;
    }

    /**
     * Adds text to the current line. If no line exists, one is created.
     *
     * @param text Text content
     * @return this builder
     */
    public AbstractMessageBuilder addText(String text) {
        if (lines.isEmpty()) newLine();
        lines.get(lines.size() - 1).add(new MessagePart(text != null ? text : ""));
        return this;
    }

    /**
     * Adds a clickable & hoverable text part to the current line.
     *
     * @param text         Text
     * @param clickAction  Click action (e.g., RUN_COMMAND, OPEN_URL)
     * @param clickValue   Value to run/open
     * @param hoverText    Hover text (can be null)
     * @return this builder
     */
    public AbstractMessageBuilder addInteractiveText(String text, ClickEvent.Action clickAction, String clickValue, String hoverText) {
        if (lines.isEmpty()) newLine();
        MessagePart part = new MessagePart(text != null ? text : "");
        if (clickAction != null && clickValue != null) {
            part.setClickEvent(new ClickEvent(clickAction, clickValue));
        }
        if (hoverText != null && !hoverText.isEmpty()) {
            part.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', hoverText)).create()
            ));
        }
        lines.get(lines.size() - 1).add(part);
        return this;
    }

    /**
     * Adds a text part with a hover tooltip to the current line.
     *
     * @param text      The text to display
     * @param hoverText The text shown when hovering
     * @return this builder
     */
    public AbstractMessageBuilder addHoverText(String text, String hoverText) {
        if (lines.isEmpty()) newLine();
        MessagePart part = new MessagePart(text != null ? text : "");
        if (hoverText != null && !hoverText.isEmpty()) {
            part.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.translateAlternateColorCodes('&', hoverText)).create()
            ));
        }
        lines.get(lines.size() - 1).add(part);
        return this;
    }

    /**
     * Enables centering for all lines.
     *
     * @return this builder
     */
    public AbstractMessageBuilder center() {
        this.center = true;
        return this;
    }

    /**
     * Sends the built message to the player.
     *
     * @param player The player
     */
    public void send(Player player) {
        for (List<MessagePart> lineParts : lines) {
            if (lineParts.isEmpty()) {
                player.sendMessage(""); // empty line
                continue;
            }

            // Build final line component
            TextComponent lineComponent = new TextComponent("");

            StringBuilder plainText = new StringBuilder();
            for (MessagePart part : lineParts) {
                plainText.append(part.getText());
            }

            String lineText = plainText.toString();
            if (center && !lineText.isEmpty()) {
                int spaces = getCenteringSpaces(lineText);
                lineComponent.addExtra(repeatSpace(spaces));
            }

            for (MessagePart part : lineParts) {
                TextComponent comp = new TextComponent(ChatColor.translateAlternateColorCodes('&', part.getText()));
                if (part.getClickEvent() != null) comp.setClickEvent(part.getClickEvent());
                if (part.getHoverEvent() != null) comp.setHoverEvent(part.getHoverEvent());
                lineComponent.addExtra(comp);
            }

            player.spigot().sendMessage(lineComponent);
        }
    }

    // ------------------------------
    // Helper methods
    // ------------------------------

    private int getCenteringSpaces(String text) {
        int lineWidth = getTextWidth(text);
        int maxWidth = 154; // default chat box width in pixels
        int spaceWidth = DefaultFontInfo.SPACE.getLength() + 1;
        return Math.max(0, (maxWidth - lineWidth) / (2 * spaceWidth));
    }

    private int getTextWidth(String text) {
        if (text == null) text = "";
        int width = 0;
        for (char c : text.toCharArray()) {
            DefaultFontInfo dfi = DefaultFontInfo.getDefaultFontInfo(c);
            width += dfi.getLength() + 1;
        }
        return width;
    }

    private String repeatSpace(int amount) {
        if (amount <= 0) return "";
        char[] chars = new char[amount];
        Arrays.fill(chars, ' ');
        return new String(chars);
    }

    // ------------------------------
    // MessagePart inner class
    // ------------------------------

    private static class MessagePart {
        private final String text;
        private ClickEvent clickEvent;
        private HoverEvent hoverEvent;

        public MessagePart(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }

        public ClickEvent getClickEvent() {
            return clickEvent;
        }

        public HoverEvent getHoverEvent() {
            return hoverEvent;
        }

        public void setClickEvent(ClickEvent clickEvent) {
            this.clickEvent = clickEvent;
        }

        public void setHoverEvent(HoverEvent hoverEvent) {
            this.hoverEvent = hoverEvent;
        }
    }

    // ------------------------------
    // DefaultFontInfo (font width data)
    // ------------------------------

    private enum DefaultFontInfo {
        A('A', 5),
        a('a', 5),
        B('B', 5),
        b('b', 5),
        C('C', 5),
        c('c', 5),
        D('D', 5),
        d('d', 5),
        E('E', 5),
        e('e', 5),
        F('F', 5),
        f('f', 4),
        G('G', 5),
        g('g', 5),
        H('H', 5),
        h('h', 5),
        I('I', 3),
        i('i', 1),
        J('J', 5),
        j('j', 5),
        K('K', 5),
        k('k', 4),
        L('L', 5),
        l('l', 1),
        M('M', 5),
        m('m', 5),
        N('N', 5),
        n('n', 5),
        O('O', 5),
        o('o', 5),
        P('P', 5),
        p('p', 5),
        Q('Q', 5),
        q('q', 5),
        R('R', 5),
        r('r', 5),
        S('S', 5),
        s('s', 5),
        T('T', 5),
        t('t', 4),
        U('U', 5),
        u('u', 5),
        V('V', 5),
        v('v', 5),
        W('W', 5),
        w('w', 5),
        X('X', 5),
        x('x', 5),
        Y('Y', 5),
        y('y', 5),
        Z('Z', 5),
        z('z', 5),
        NUM_1('1', 5),
        NUM_2('2', 5),
        NUM_3('3', 5),
        NUM_4('4', 5),
        NUM_5('5', 5),
        NUM_6('6', 5),
        NUM_7('7', 5),
        NUM_8('8', 5),
        NUM_9('9', 5),
        NUM_0('0', 5),
        SPACE(' ', 3),
        DEFAULT('?', 4);

        private final char character;
        private final int length;

        DefaultFontInfo(char character, int length) {
            this.character = character;
            this.length = length;
        }

        public char getCharacter() {
            return this.character;
        }

        public int getLength() {
            return this.length;
        }

        public static DefaultFontInfo getDefaultFontInfo(char c) {
            for (DefaultFontInfo dFI : values()) {
                if (dFI.getCharacter() == c) return dFI;
            }
            return DEFAULT;
        }
    }
}