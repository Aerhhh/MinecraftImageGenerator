package net.aerh.imagegenerator.pack;

/**
 * The damage state of a damageable item: the {@code minecraft:damage} and
 * {@code minecraft:max_damage} component values that item model definitions evaluate through
 * {@code range_dispatch} nodes with {@code property: minecraft:damage} (Wynncraft-style weapon
 * skin selection). With {@code normalize: true} (the vanilla default) the property reads
 * {@code damage / maxDamage} clamped to 0..1; with {@code normalize: false} it reads the raw
 * {@code damage} value.
 *
 * @param damage    current damage, 0 (pristine) to {@code maxDamage} (about to break)
 * @param maxDamage the item's maximum damage; a value of 0 makes the normalized property read 0
 */
public record ItemDamage(int damage, int maxDamage) {

    public ItemDamage {
        if (damage < 0 || maxDamage < 0) {
            throw new IllegalArgumentException(
                "damage and maxDamage must not be negative, got " + damage + "/" + maxDamage);
        }
        if (damage > maxDamage) {
            throw new IllegalArgumentException(
                "damage must not exceed maxDamage, got " + damage + "/" + maxDamage);
        }
    }
}
