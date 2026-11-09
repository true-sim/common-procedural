package com.truesim.common.procedural.water;

/**
 * Everything a water network is generated from, with physical defaults for Alpine rivers and streams. Every value is
 * also an engine setting ({@link WaterSettings}); {@link #DEFAULTS} equals the settings' defaults.
 *
 * @param seed the seed: stones and the waterfalls' jets
 * @param flow the flow routing: grid, channel initiation, discharge
 * @param channels the channels' hydraulic geometry: width, depth and speed from discharge and slope
 * @param banks the carved beds and banks, and where grass stops
 * @param falls the waterfalls: detection, break-up, spray and mist
 * @param stones the stones in the beds
 * @param surface the surface meshes
 */
public record WaterParameters(long seed, Flow flow, Channels channels, Banks banks, Falls falls, Stones stones,
    Surface surface) {

    /** Alpine rivers and streams. */
    public static final WaterParameters DEFAULTS = new WaterParameters(1, Flow.DEFAULTS, Channels.DEFAULTS,
        Banks.DEFAULTS, Falls.DEFAULTS, Stones.DEFAULTS, Surface.DEFAULTS);

    /**
     * Flow routing over the terrain sampled on a grid: depressions filled (Priority-Flood), single flow directions,
     * catchments accumulated.
     *
     * @param cell metres between grid samples
     * @param subsamples heights per cell side taken for a cell's height, its lowest kept (1: the centre only), so a
     *     channel narrower than a cell in finer data is found
     * @param channelArea the catchment a channel starts from, square metres
     * @param runoff the mean specific discharge, cubic metres a second per square kilometre of catchment (Alpine
     *     catchments 0.03 to 0.06)
     * @param burnDepth metres mapped watercourses are lowered by before routing ("stream burning"), so the flow follows
     *     them
     * @param minLength the shortest channel kept from its head, metres
     */
    public record Flow(double cell, int subsamples, double channelArea, double runoff, double burnDepth,
        double minLength) {

        /** 10 m cells, channels from a quarter square kilometre, 45 litres a second per square kilometre. */
        public static final Flow DEFAULTS = new Flow(10, 3, 250_000, 0.045, 20, 60);

        public Flow {
            if (!(cell > 0) || subsamples < 1 || !(channelArea > 0) || !(runoff > 0) || !(burnDepth >= 0)
                || !(minLength >= 0)) {
                throw new IllegalArgumentException("Invalid flow parameters: " + cell + ", " + subsamples + ", "
                    + channelArea + ", " + runoff + ", " + burnDepth + ", " + minLength);
            }
        }
    }

    /**
     * Hydraulic geometry (Leopold and Maddock 1953): the width {@code a Q^b} from the discharge {@code Q}; the depth
     * from Manning's equation for that width and the channel's slope, {@code d = (Q n / (w sqrt S))^(3/5)}; the speed
     * from continuity, {@code v = Q / (w d)}.
     *
     * @param widthCoefficient {@code a}, metres (for Q in cubic metres a second)
     * @param widthExponent {@code b} (about 0.5 at a station downstream)
     * @param minWidth the narrowest channel, metres
     * @param manning Manning's roughness {@code n} (gravel and cobble beds 0.03 to 0.05, boulders 0.07)
     * @param minSlope the gentlest slope a channel's depth is worked out for
     * @param maxSpeed the fastest flow, metres a second (steep cascades are aerated and rough)
     * @param smoothing metres the channel's line is smoothed over (Chaikin corner cutting, then a running mean)
     * @param snap metres across the flow a channel's line moves to the lowest ground (the thalweg in finer data)
     */
    public record Channels(double widthCoefficient, double widthExponent, double minWidth, double manning,
        double minSlope, double maxSpeed, double smoothing, double snap) {

        /** Mountain gravel-bed rivers. */
        public static final Channels DEFAULTS = new Channels(4.0, 0.5, 0.6, 0.045, 0.002, 8, 15, 6);

        public Channels {
            if (!(widthCoefficient > 0) || !(widthExponent >= 0) || !(minWidth > 0) || !(manning > 0)
                || !(minSlope > 0) || !(maxSpeed > 0) || !(smoothing >= 0) || !(snap >= 0)) {
                throw new IllegalArgumentException("Invalid channel parameters");
            }
        }
    }

    /**
     * The bed and banks carved into the terrain, and the grass kept off them.
     *
     * @param bankWidth metres of bank beside the water on each side, as a multiple of the channel's width, at least
     *     {@code minBank}
     * @param minBank the narrowest bank, metres
     * @param bankSlope the banks' rise per metre outward
     * @param gravel the bare (grassless) bank beside the water, as a fraction of the bank's width
     * @param surfaceOffset metres the water's surface lies below the ground along the channel's line (the data's
     *     surface is the water's, or the bed's for narrow streams)
     */
    public record Banks(double bankWidth, double minBank, double bankSlope, double gravel, double surfaceOffset) {

        /** Gentle banks, a third of them bare gravel. */
        public static final Banks DEFAULTS = new Banks(0.5, 1.5, 0.6, 0.35, 0.15);

        public Banks {
            if (!(bankWidth >= 0) || !(minBank > 0) || !(bankSlope > 0) || !(gravel >= 0 && gravel <= 1)
                || !(surfaceOffset >= 0)) {
                throw new IllegalArgumentException("Invalid bank parameters");
            }
        }
    }

    /**
     * Waterfalls, where a channel drops over steep ground. A free fall leaves the lip at the brink's speed (critical
     * flow, Rouse's brink depth) and falls against quadratic drag; its sheet breaks up after Horeni's length
     * {@code L = coefficient q^exponent} for the discharge {@code q} per metre of width (as cited by Castillo et
     * al. 2015), then falls as jets and spray, atomising: the fraction {@code 1 - exp(-H / atomisation)} of a fall of
     * height {@code H} reaches the bottom as spray and mist.
     *
     * @param minGradient the steepest ground a channel flows down, drop per run: steeper is a fall (1.2, 50 degrees)
     * @param freeGradient a fall's mean drop per run from which it falls free (else it cascades over the rock)
     * @param minHeight the lowest fall, metres
     * @param breakupCoefficient Horeni's coefficient, metres
     * @param breakupExponent Horeni's exponent
     * @param terminalSpeed the falling water's terminal speed once broken up, metres a second
     * @param atomisation metres of fall over which the water atomises
     * @param spread the sheet's spreading, degrees each side (jets three times as much)
     * @param jetSpacing metres between the jets across the sheet
     * @param mistRadius the mist's horizontal radius as a fraction of the fall's height
     * @param mistHeight the mist's height as a fraction of the fall's height
     * @param mistDensity the mist's extinction per metre for 1 cubic metre a second of spray
     */
    public record Falls(double minGradient, double freeGradient, double minHeight, double breakupCoefficient,
        double breakupExponent, double terminalSpeed, double atomisation, double spread, double jetSpacing,
        double mistRadius, double mistHeight, double mistDensity) {

        /** Physical defaults: Staubbach-like falls mostly reach the ground as spray. */
        public static final Falls DEFAULTS = new Falls(1.2, 1.6, 8, 6, 0.32, 9, 120, 3, 0.8, 0.2, 0.3, 0.06);

        public Falls {
            if (!(minGradient > 0) || !(freeGradient >= minGradient) || !(minHeight > 0)
                || !(breakupCoefficient > 0) || !(terminalSpeed > 0) || !(atomisation > 0) || !(spread >= 0)
                || !(jetSpacing > 0) || !(mistRadius > 0) || !(mistHeight > 0) || !(mistDensity >= 0)) {
                throw new IllegalArgumentException("Invalid waterfall parameters");
            }
        }
    }

    /**
     * Stones in the beds: their median size from the shear stress of the flood that shapes the bed at the threshold of
     * motion (Shields: the bed is armoured with what the flow cannot move, {@code D50 = d S / (1.65 theta)} for depth
     * {@code d}, slope {@code S} and Shields' {@code theta} 0.047, the depth the mean depth times {@code flood}), spread
     * log-normally.
     *
     * @param density stones a square metre of bed
     * @param spread the log-normal spread of sizes
     * @param minSize the smallest stone placed, metres
     * @param maxSize the largest, metres
     * @param banks stones beyond the water's edge, on the bank, as a fraction of the width
     * @param flood the depth of the bed-shaping flood as a multiple of the mean depth (Alpine rivers 2 to 4)
     */
    public record Stones(double density, double spread, double minSize, double maxSize, double banks, double flood) {

        /** A stony mountain bed. */
        public static final Stones DEFAULTS = new Stones(0.06, 0.7, 0.15, 2.5, 0.15, 3);

        public Stones {
            if (!(density >= 0) || !(spread >= 0) || !(minSize > 0) || !(maxSize >= minSize) || !(banks >= 0)
                || !(flood > 0)) {
                throw new IllegalArgumentException("Invalid stone parameters");
            }
        }
    }

    /**
     * The surface meshes.
     *
     * @param chunk metres a side of the square chunks the surfaces are cut into (one mesh each, streamed)
     * @param spacing metres between cross-sections along a channel
     * @param across quads across a channel
     */
    public record Surface(double chunk, double spacing, int across) {

        /** 512 m chunks, cross-sections every 4 m, 4 quads across. */
        public static final Surface DEFAULTS = new Surface(512, 4, 4);

        public Surface {
            if (!(chunk > 0) || !(spacing > 0) || across < 1) {
                throw new IllegalArgumentException("Invalid surface parameters");
            }
        }
    }

    public WaterParameters withSeed(long newSeed) {
        return new WaterParameters(newSeed, flow, channels, banks, falls, stones, surface);
    }

    public WaterParameters withFlow(Flow newFlow) {
        return new WaterParameters(seed, newFlow, channels, banks, falls, stones, surface);
    }

    public WaterParameters withChannels(Channels newChannels) {
        return new WaterParameters(seed, flow, newChannels, banks, falls, stones, surface);
    }

    public WaterParameters withBanks(Banks newBanks) {
        return new WaterParameters(seed, flow, channels, newBanks, falls, stones, surface);
    }

    public WaterParameters withFalls(Falls newFalls) {
        return new WaterParameters(seed, flow, channels, banks, newFalls, stones, surface);
    }

    public WaterParameters withStones(Stones newStones) {
        return new WaterParameters(seed, flow, channels, banks, falls, newStones, surface);
    }

    public WaterParameters withSurface(Surface newSurface) {
        return new WaterParameters(seed, flow, channels, banks, falls, stones, newSurface);
    }
}
