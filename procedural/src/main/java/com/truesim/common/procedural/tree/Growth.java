package com.truesim.common.procedural.tree;

/** How a species' branches are grown. */
public enum Growth {
    /**
     * Parametric (after Weber and Penn 1995): every level's branches placed by rules (whorls, spirals, angles,
     * lengths by a crown shape). For trees with strong apical dominance and regular structure: conifers.
     */
    PARAMETRIC,
    /**
     * Space colonization (Runions, Lane and Prusinkiewicz 2007): branches grow toward attraction points filling the
     * crown's envelope, competing for space. For broadleaf trees and shrubs, whose crowns fill a volume irregularly.
     */
    SPACE_COLONIZATION
}
