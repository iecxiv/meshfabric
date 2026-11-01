package iecxiv.meshfabric.data.remote

// Percentage fields use 0..100. Never infer a fractional scale from a small value.
// The fixed denominator represents percentage units, not measured ZIP bytes.
internal fun zipProgressValues(percent: Float?, current: Long?, total: Long?): Pair<Long, Long> {
    if (percent != null) {
        if (!percent.isFinite() || percent < 0f || percent > 100f) return 0L to 0L
        return (percent.toDouble() * 100.0).toLong() to 10000L
    }
    if (current == null || total == null || current < 0L || total <= 0L || current > total) {
        return 0L to 0L
    }
    return current to total
}
