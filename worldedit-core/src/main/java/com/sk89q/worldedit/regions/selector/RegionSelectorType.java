/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.regions.selector;

import com.fastasyncworldedit.core.regions.selector.FuzzyRegionSelector;
import com.fastasyncworldedit.core.regions.selector.PolyhedralRegionSelector;
import com.sk89q.worldedit.regions.RegionSelector;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * An enum of default region selector types.
 */
public enum RegionSelectorType {

    CUBOID(CuboidRegionSelector.class),
    EXTENDING_CUBOID(ExtendingCuboidRegionSelector.class),
    CYLINDER(CylinderRegionSelector.class),
    SPHERE(SphereRegionSelector.class),
    ELLIPSOID(EllipsoidRegionSelector.class),
    POLYGON(Polygonal2DRegionSelector.class),
    CONVEX_POLYHEDRON(ConvexPolyhedralRegionSelector.class),
    //FAWE start
    POLYHEDRAL(PolyhedralRegionSelector.class),
    FUZZY(FuzzyRegionSelector.class);
    //FAWE end

    //FAWE start
    private static final Map<Class<? extends RegionSelector>, RegionSelectorType> VALUE_MAP = new HashMap<>();

    static {
        for (RegionSelectorType type : values()) {
            VALUE_MAP.put(type.getSelectorClass(), type);
        }
    }
    //FAWE end

    private final Class<? extends RegionSelector> selectorClass;

    RegionSelectorType(Class<? extends RegionSelector> selectorClass) {
        this.selectorClass = selectorClass;
    }

    //FAWE start
    /**
     * Get a {@link RegionSelectorType} for the given {@link RegionSelector}
     *
     * @param selector Region selector to get type enum for
     * @since 2.9.2
     */
    @Nullable
    public static RegionSelectorType getForSelector(RegionSelector selector) {
        return VALUE_MAP.get(selector.getClass());
    }
    //FAWE end

    /**
     * Get the selector class.
     *
     * @return a selector class
     */
    public Class<? extends RegionSelector> getSelectorClass() {
        return selectorClass;
    }

    /**
     * Create a new selector instance.
     *
     * @return a selector
     */
    public RegionSelector createSelector() {
        try {
            return getSelectorClass().newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException("Could not create selector", e);
        }
    }

}
