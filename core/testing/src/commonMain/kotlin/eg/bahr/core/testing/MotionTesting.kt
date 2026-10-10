package eg.bahr.core.testing

/**
 * A few frames (four at 60 Hz) past an animation's nominal end, for tests that drive the Compose
 * frame clock by hand: the last frame of an animation lands on the first frame at or after its
 * duration, so advancing by exactly the duration can stop one frame short of the settled state.
 * Add it to a `BahrMotion` duration (this module cannot see `BahrMotion`, so only the margin is shared).
 */
const val ANIMATION_SETTLE_MARGIN_MILLIS = 64L
