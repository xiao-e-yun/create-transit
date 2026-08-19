package me.xiaoeyun.createtransit.content.transit;

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
 * Labels may only appear at the head of an address, and everything from the first token that
 * is not a complete label onwards is the path, which may contain spaces. A name cannot contain
 * {@code ]>}, since the first occurrence always terminates the token.
 *
 * Two names are special. A blank name is a label of its own — the default lane, matched only by
 * itself — while {@code *} matches any label. Unlabelled addresses behave exactly as in vanilla.
 */
public final class AddressLabels {

    private static final String OPEN = "<[";
    private static final String CLOSE = "]>";

    /**
     * A label name meaning "any label", read on either side the way {@code *}
     * means "any address" everywhere else in Create.
     */
    public static final String WILDCARD = "*";

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

    /**
     * True when an address is one label and nothing else — what a package port configured as a
     * transit endpoint holds. A label with a path behind it does not qualify; a labelled address
     * is compared on its head label alone, so that path could never be matched against.
     */
    public static boolean isEndpoint(String address) {
        return startsWithLabel(address) && stripHeadLabel(address).isEmpty();
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
        return join(OPEN + sanitized + CLOSE, address);
    }

    /**
     * The address a package port holds as a transit endpoint. A blank name is a label in its own
     * right here — the default lane — which is the opposite of what {@link #push} means by one,
     * so the two cannot share an entry point.
     */
    public static String endpoint(String name) {
        return OPEN + sanitizeName(name) + CLOSE;
    }

    /**
     * Prefixes the label a port would hold for {@code name}, so a blank name
     * stamps the default lane rather than nothing at all.
     */
    public static String pushEndpoint(String name, String address) {
        return join(endpoint(name), address);
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
     * Reads a label name off a sign, accepting either a bare name or a fully
     * delimited label, so a player copying an address verbatim off a package
     * still gets what they meant.
     */
    public static String signLabel(String signText) {
        String text = signText == null ? "" : signText.trim();
        String name = headLabelName(text);
        return sanitizeName(name != null ? name : text);
    }

    /**
     * The rule injected in front of {@code PackageItem#matchAddress}. Null when neither side is
     * labelled, leaving vanilla untouched. Otherwise only the head labels are compared — the path
     * behind one stays invisible to local address hardware until a gate peels the layer off — and
     * either side naming {@link #WILDCARD} matches any label. A labelled address and an unlabelled
     * one never match, in either direction.
     */
    @Nullable
    public static Boolean match(String boxAddress, String address) {
        boolean boxLabelled = startsWithLabel(boxAddress);
        boolean addressLabelled = startsWithLabel(address);
        if (!boxLabelled && !addressLabelled)
            return null;
        if (!boxLabelled || !addressLabelled)
            return false;
        if (WILDCARD.equals(headLabelName(address)) || WILDCARD.equals(headLabelName(boxAddress)))
            return true;
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

    /** Puts a label token in front of an address; the separator only keeps a path off the delimiter. */
    private static String join(String label, String address) {
        String path = trimLeading(address);
        return path.isEmpty() ? label : label + " " + path;
    }

    private static String trimLeading(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i)))
            i++;
        return text.substring(i);
    }

}
