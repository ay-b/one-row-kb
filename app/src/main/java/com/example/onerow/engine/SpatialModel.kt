package com.example.onerow.engine

/** Per-touch Gaussian over keys. Output is ln-likelihood up to a constant, one entry per letter of the alphabet. */
object SpatialModel {
    /**
     * @param cx,cy key centres indexed by letter (a=0); NaN when a letter is not on the layout
     * @param sx,sy standard deviations; use a huge sy to ignore the vertical axis (one-row mode)
     */
    fun logProbs(x: Float, y: Float, cx: FloatArray, cy: FloatArray, sx: Float, sy: Float): FloatArray {
        val out = FloatArray(cx.size)
        for (i in cx.indices) {
            if (cx[i].isNaN()) { out[i] = NEG; continue }
            val dx = (x - cx[i]) / sx; val dy = (y - cy[i]) / sy
            out[i] = -0.5f * (dx * dx + dy * dy)
        }
        return out
    }
    const val NEG = -1e9f
}
