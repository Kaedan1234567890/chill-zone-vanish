package com.chillzone.homes;

public record Home(String name, String dimension, double x, double y, double z,
                   float yaw, float pitch, String icon) {
    public Home withName(String newName) {
        return new Home(newName, dimension, x, y, z, yaw, pitch, icon);
    }
    public Home withIcon(String newIcon) {
        return new Home(name, dimension, x, y, z, yaw, pitch, newIcon);
    }
}
