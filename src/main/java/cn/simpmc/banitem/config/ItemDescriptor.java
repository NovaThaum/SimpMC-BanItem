package cn.simpmc.banitem.config;

import java.util.Objects;

/** A Bukkit-independent description of an item type submitted for checking. */
public record ItemDescriptor(String material) {
    public ItemDescriptor {
        material = MaterialPattern.normalizeMaterial(material);
        Objects.requireNonNull(material, "material");
    }

    public static ItemDescriptor of(String material) {
        return new ItemDescriptor(material);
    }
}
