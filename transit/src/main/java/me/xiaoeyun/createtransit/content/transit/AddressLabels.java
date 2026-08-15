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
 * Labels may only appear at the head of an address. A name cannot contain the
 * sequence {@code ]>} because the first occurrence always terminates the token.
 * Everything from the first token that is not a complete label onwards is the
 * path, which may contain spaces.
 *
 * A label is a transit door number inside one transport domain, not a requester
 * identity: each layer is stamped by the transit link that declared the source
 * foreign, routed by that domain's vanilla hardware, and consumed by a transit
 * gate on its boundary. Addresses without labels behave exactly as in vanilla.
 *
 * Two names are special, and they are vanilla's own two special filters read
 * one layer up. A blank name is the default lane, matched only by itself, the
 * way a blank address filter matches only unaddressed packages. {@code *} takes
 * any label, the way {@code *} takes any address.
 */
public final class AddressLabels {

    private static final String OPEN = "<[";
    private static final String CLOSE = "]>";

    /**
     * A label name meaning "any label", the way {@code *} means "any address"
     * everywhere else in Create. Only a port's filter is read this way; a
     * package addressed to every border at once is not a thing.
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
     * True when an address is one label and nothing else — what a package port
     * configured as a transit endpoint holds.
     *
     * A label with a path behind it does not qualify: the path could never be
     * matched against anyway, since a labelled address is compared on its head
     * label alone.
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
     * The address a package port holds as a transit endpoint.
     *
     * A blank name is a label in its own right here — the default lane, which
     * unnamed border traffic is addressed to — and that is the opposite of what
     * {@link #push} means by a blank name, which is why the two cannot share an
     * entry point. Vanilla draws the same distinction one layer down: a blank
     * address filter matches unaddressed packages rather than all of them.
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
     * The narrow matching rule injected in front of {@code PackageItem#matchAddress}.
     *
     * Returns null when neither side is labelled, leaving vanilla semantics
     * completely untouched. Otherwise the head label short-circuits everything:
     * only an identical head label matches, so
     * {@code *}, globs and blanks never catch a package in transit — and an
     * unstripped label shadows the path behind it, keeping foreign packages
     * invisible to local address hardware until a gate peels the layer off.
     *
     * A filter labelled {@link #WILDCARD} is the one exception, and it takes
     * any label. Only the filter side, never the package's: every caller in
     * Create passes the box first and the filter second, and a package that
     * every border post would claim is a hole rather than a feature. An
     * unlabelled package is still refused — transit is a space of its own, and
     * a port that wants all local traffic too is a second port.
     */
    @Nullable
    public static Boolean match(String boxAddress, String address) {
        boolean boxLabelled = startsWithLabel(boxAddress);
        boolean addressLabelled = startsWithLabel(address);
        if (!boxLabelled && !addressLabelled)
            return null;
        if (!boxLabelled || !addressLabelled)
            return false;
        if (WILDCARD.equals(headLabelName(address)))
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

    /**
     * Puts a complete label token in front of an address. A label with nothing
     * behind it is an address in its own right; the separator only exists to
     * keep a path from touching the delimiter.
     */
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
