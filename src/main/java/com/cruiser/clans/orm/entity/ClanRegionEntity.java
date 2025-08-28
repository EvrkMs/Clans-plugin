package com.cruiser.clans.orm.entity;

import org.bukkit.Location;

public class ClanRegionEntity {

    private Integer id;
    private ClanEntity clan;
    private String worldName;
    private String markerType;
    private int marker1X;
    private int marker1Y;
    private int marker1Z;
    private Integer marker2X;
    private Integer marker2Y;
    private Integer marker2Z;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public ClanEntity getClan() { return clan; }
    public void setClan(ClanEntity clan) { this.clan = clan; }

    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public String getMarkerType() { return markerType; }
    public void setMarkerType(String markerType) { this.markerType = markerType; }

    public int getMarker1X() { return marker1X; }
    public void setMarker1X(int marker1x) { this.marker1X = marker1x; }

    public int getMarker1Y() { return marker1Y; }
    public void setMarker1Y(int marker1y) { this.marker1Y = marker1y; }

    public int getMarker1Z() { return marker1Z; }
    public void setMarker1Z(int marker1z) { this.marker1Z = marker1z; }

    public Integer getMarker2X() { return marker2X; }
    public void setMarker2X(Integer marker2x) { this.marker2X = marker2x; }

    public Integer getMarker2Y() { return marker2Y; }
    public void setMarker2Y(Integer marker2y) { this.marker2Y = marker2y; }

    public Integer getMarker2Z() { return marker2Z; }
    public void setMarker2Z(Integer marker2z) { this.marker2Z = marker2z; }

    public boolean hasSecondMarker() {
        return marker2X != null && marker2Y != null && marker2Z != null;
    }

    public void setMarker1(Location loc) {
        this.marker1X = loc.getBlockX();
        this.marker1Y = loc.getBlockY();
        this.marker1Z = loc.getBlockZ();
    }

    public void setMarker2(Location loc) {
        this.marker2X = loc.getBlockX();
        this.marker2Y = loc.getBlockY();
        this.marker2Z = loc.getBlockZ();
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return false;
        if (!hasSecondMarker()) {
            return loc.getBlockX() == marker1X && loc.getBlockY() == marker1Y && loc.getBlockZ() == marker1Z;
        }
        int minX = Math.min(marker1X, marker2X);
        int maxX = Math.max(marker1X, marker2X);
        int minY = Math.min(marker1Y, marker2Y);
        int maxY = Math.max(marker1Y, marker2Y);
        int minZ = Math.min(marker1Z, marker2Z);
        int maxZ = Math.max(marker1Z, marker2Z);
        return loc.getBlockX() >= minX && loc.getBlockX() <= maxX &&
               loc.getBlockY() >= minY && loc.getBlockY() <= maxY &&
               loc.getBlockZ() >= minZ && loc.getBlockZ() <= maxZ;
    }

    public int getRegionSize() {
        if (!hasSecondMarker()) return 0;
        int dx = Math.abs(marker1X - marker2X) + 1;
        int dz = Math.abs(marker1Z - marker2Z) + 1;
        return dx * dz;
    }
}

