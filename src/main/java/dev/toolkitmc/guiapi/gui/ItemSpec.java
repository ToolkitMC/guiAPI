package dev.toolkitmc.guiapi.gui;

/**
 * Parsed form of the {@code "itemId[:amount]"} strings used by {@code give_item}
 * and {@code take_item}.
 *
 * Item ids normally contain a colon themselves ({@code minecraft:gold_nugget}),
 * so the amount can only be the segment after the <em>last</em> colon, and only
 * when that segment is an integer. Splitting on the first colon would turn
 * {@code minecraft:gold_nugget:5} into item {@code minecraft} / amount
 * {@code gold_nugget:5}, which silently matches nothing.
 *
 * @param itemId item id, e.g. {@code minecraft:gold_nugget} or {@code gold_nugget}
 * @param amount parsed amount, or the caller's default when none was given
 */
public record ItemSpec(String itemId, int amount) {

    public static ItemSpec parse(String raw, int defaultAmount) {
        String value = raw == null ? "" : raw.trim();
        int last = value.lastIndexOf(':');
        if (last > 0 && last < value.length() - 1) {
            try {
                int amount = Integer.parseInt(value.substring(last + 1));
                return new ItemSpec(value.substring(0, last), amount);
            } catch (NumberFormatException ignored) {
                // Last segment is part of the id (e.g. "minecraft:diamond" with no amount).
            }
        }
        return new ItemSpec(value, defaultAmount);
    }
}
