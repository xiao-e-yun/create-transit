package me.xiaoeyun.createnestnetwork.content.transit;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Grammar and matching rules for transit labels.
 *
 * <pre>
 * address := label* path
 * label   := "&lt;[" name "]&gt;"
 * </pre>
 *
 * Labels may only appear at the head of an address. A name cannot contain the
 * sequence {@code ]>} because the first occurrence always terminates the token.
 * Everything from the first token that is not a complete label onwards is the
 * path, which may contain spaces.
 *
 * A label is a transit door number inside one transport domain, not a requester
 * identity: each layer is stamped by the transit link that declared the source
 * foreign, routed by that domain's vanilla hardware, and consumed by a transit
 * gate on its boundary. Addresses without labels behave exactly as in vanilla.
 */
public final class AddressLabels {

    public static final String OPEN = "<[";
    public static final String CLOSE = "]>";

    private AddressLabels() {}

    /** True if the address begins with a complete label token. */
    public static boolean startsWithLabel(String address) {
        return headLabelEnd(address) != -1;
    }

    /** The head label including its delimiters, or null if there is none. */
    @Nullable
    public static String headLabel(String address) {
        int end = headLabelEnd(address);
        return end == -1 ? null : address.substring(0, end);
    }

    /** The head label's name without delimiters, or null if there is none. */
    @Nullable
    public static String headLabelName(String address) {
        int end = headLabelEnd(address);
        return end == -1 ? null : address.substring(OPEN.length(), end - CLOSE.length());
    }

    /**
     * Removes the head label and any whitespace that followed it. Returns the
     * address unchanged when it does not begin with a complete label — stripping
     * is always exactly one layer, LIFO, and never touches the path.
     */
    public static String stripHeadLabel(String address) {
        int end = headLabelEnd(address);
        if (end == -1)
            return address;
        return trimLeading(address.substring(end));
    }

    /** Prefixes a label onto an address. A blank name is pure forwarding. */
    public static String push(String name, String address) {
        String sanitized = sanitizeName(name);
        if (sanitized.isEmpty())
            return address;
        return OPEN + sanitized + CLOSE + " " + address;
    }

    /** Every head label name, outermost first. */
    public static List<String> labelNames(String address) {
        List<String> names = new ArrayList<>();
        String remaining = address;
        while (true) {
            int end = headLabelEnd(remaining);
            if (end == -1)
                return names;
            names.add(remaining.substring(OPEN.length(), end - CLOSE.length()));
            remaining = trimLeading(remaining.substring(end));
        }
    }

    /** The address with every head label removed. */
    public static String path(String address) {
        String remaining = address;
        while (true) {
            int end = headLabelEnd(remaining);
            if (end == -1)
                return remaining;
            remaining = trimLeading(remaining.substring(end));
        }
    }

    /**
     * Strips what a player may not type into a label name. The closing sequence
     * would terminate the token early, so it cannot be part of a name.
     */
    public static String sanitizeName(String name) {
        return name.replace(CLOSE, "")
            .trim();
    }

    /**
     * The narrow matching rule injected in front of {@code PackageItem#matchAddress}.
     *
     * Returns null when neither side is labelled, leaving vanilla semantics
     * completely untouched. Otherwise the head label short-circuits everything:
     * only an identical head label (or two identical addresses) matches, so
     * {@code *}, globs and blanks never catch a package in transit — and an
     * unstripped label shadows the path behind it, keeping foreign packages
     * invisible to local address hardware until a gate peels the layer off.
     */
    @Nullable
    public static Boolean match(String boxAddress, String address) {
        boolean boxLabelled = startsWithLabel(boxAddress);
        boolean addressLabelled = startsWithLabel(address);
        if (!boxLabelled && !addressLabelled)
            return null;
        if (boxAddress.equals(address))
            return true;
        if (!boxLabelled || !addressLabelled)
            return false;
        return headLabel(boxAddress).equals(headLabel(address));
    }

    /**
     * Index just past the head label's closing delimiter, or -1 when the address
     * does not begin with a complete label. An unterminated {@code <[} is not a
     * label — the whole string is then an ordinary path.
     */
    private static int headLabelEnd(String address) {
        if (!address.startsWith(OPEN))
            return -1;
        int close = address.indexOf(CLOSE, OPEN.length());
        return close == -1 ? -1 : close + CLOSE.length();
    }

    private static String trimLeading(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i)))
            i++;
        return text.substring(i);
    }

}
