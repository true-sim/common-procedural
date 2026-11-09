package com.truesim.common.procedural.assets;

import com.truesim.annotations.AllowAllocation;
import com.truesim.common.procedural.ecology.EcologyParameters;
import com.truesim.engine.settings.EngineSettings;
import com.truesim.engine.settings.Setting;
import java.util.List;

/**
 * The ecology's tunables as engine settings: where trees and shrubs can root ({@link EcologyParameters.Woody}).
 * {@link #apply} sets them on an ecology's parameters; their defaults are {@link EcologyParameters.Woody#DEFAULTS}.
 */
@AllowAllocation("settings: declared once, read when an ecology is made")
public final class EcologySettings {

    /** The steepest ground trees and shrubs stand on, degrees. */
    public static final Setting<Float> TREE_MAX_SLOPE = Setting.decimal("procedural.ecology.treeMaxSlope",
        (float) EcologyParameters.Woody.DEFAULTS.maxSlope(), 20, 90,
        "the steepest ground trees and shrubs stand on, degrees (no trees on rock faces)");
    /** The least soil depth a tree needs, metres. */
    public static final Setting<Float> TREE_MIN_SOIL = Setting.decimal("procedural.ecology.treeMinSoil",
        (float) EcologyParameters.Woody.DEFAULTS.minSoil(), 0, 1,
        "the least soil depth a tree roots in, metres (shrubs half of it)");

    /** Every setting, to register. */
    public static final List<Setting<?>> ALL = List.of(TREE_MAX_SLOPE, TREE_MIN_SOIL);

    private EcologySettings() {
    }

    /** {@code parameters} with the woody limits {@code settings} ask for. */
    public static EcologyParameters apply(EcologyParameters parameters, EngineSettings settings) {
        return parameters.withWoody(new EcologyParameters.Woody(get(settings, TREE_MAX_SLOPE),
            get(settings, TREE_MIN_SOIL)));
    }

    private static double get(EngineSettings settings, Setting<Float> setting) {
        return Double.parseDouble(Float.toString(settings.get(setting)));
    }
}
