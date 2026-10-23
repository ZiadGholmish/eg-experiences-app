package eg.bahr.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onVisibilityChanged
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import eg.bahr.core.designsystem.generated.resources.Res
import eg.bahr.core.designsystem.theme.BahrTheme
import eg.bahr.core.designsystem.theme.reducedMotion
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieClipSpec
import io.github.alexzhirkevich.compottie.LottieComposition
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import kotlin.math.ceil

/**
 * A Lottie animation from the design system's own files (M3-M8, Compottie on Android and iOS), in the
 * current theme's colours.
 *
 * - Its box keeps the animation's aspect ratio from the first frame, before the file has loaded, so
 *   nothing moves when it appears. Give it one dimension (a width or a height) in [modifier].
 * - Plays once and rests on its last frame, or loops ([Props.playback]); prefer once. It plays only
 *   while on screen with the app in the foreground, and asks for no frames once it rests.
 * - Under reduced motion (BahrTheme.reducedMotion) it does not play: it shows that resting frame.
 * - Decorative unless [Props.contentDescription] is given: most sit next to a title that already says it.
 *
 * Use sparingly, where motion adds meaning (PLAN M3-M8): an empty state, a hero.
 */
interface BahrLottie {
    /**
     * The animations the design system ships, named for what they show, not where they are used
     * (sources and licences: `composeResources/files/licenses/lottie_simple_license.txt`).
     */
    enum class Animation(
        internal val path: String,
        /** Width / height of the file's canvas, reserved before it loads. */
        internal val aspectRatio: Float,
    ) {
        /** A bell in a disc, rung once with two rings going out (624 x 663). */
        Bell("files/lottie/bell.json", BELL_WIDTH / BELL_HEIGHT),

        /** A line-drawn sailing boat rocking on waves, on a `surface` ground (348 x 240). */
        Sailboat("files/lottie/sailboat.json", SAILBOAT_WIDTH / SAILBOAT_HEIGHT),
    }

    enum class Playback { Once, Loop }

    @Immutable
    data class Props(
        val animation: Animation,
        val playback: Playback = Playback.Once,
        /** For screen readers; null when the animation is decoration (the usual case). */
        val contentDescription: String? = null,
    )
}

@Composable
fun BahrLottie(
    modifier: Modifier = Modifier,
    props: BahrLottie.Props,
) = BahrLottie(modifier, props, fixedProgress = null)

/**
 * [BahrLottie], or, with [fixedProgress], one still frame of it (0..1): the design system's screenshot
 * tests use it to show a moving part's colours mid-animation.
 */
@Composable
internal fun BahrLottie(
    modifier: Modifier,
    props: BahrLottie.Props,
    fixedProgress: Float?,
) {
    val scheme = MaterialTheme.colorScheme
    val spec = remember(props.animation, scheme) { TintedLottieSpec(props.animation, scheme.lottieRoles()) }
    val composition by rememberLottieComposition(spec)
    val reduced = BahrTheme.reducedMotion
    var onScreen by remember { mutableStateOf(false) }
    val progress: () -> Float =
        if (fixedProgress != null) {
            { fixedProgress }
        } else if (reduced) {
            { composition?.restingProgress() ?: 0f }
        } else {
            // Frames only while someone can see it (the iPhone ran hot, M3-M8): paused while the app is in
            // the background or the box is scrolled out of view, and picked up where it stopped (no restart).
            // Leaving the composition cancels it outright, and a played-once animation that has ended asks for
            // no more frames. Under reduced motion (above) it never runs at all.
            val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
            val playing = onScreen && lifecycle.isAtLeast(Lifecycle.State.STARTED)
            val state =
                animateLottieCompositionAsState(
                    composition = composition,
                    isPlaying = playing,
                    restartOnPlay = false,
                    // Ends on the last whole frame: on the out-point itself, layers that end there are gone.
                    clipSpec = composition?.let { LottieClipSpec.Progress(min = 0f, max = it.restingProgress()) },
                    iterations = if (props.playback == BahrLottie.Playback.Loop) Compottie.IterateForever else 1,
                )
            state::value
        }
    val painter =
        rememberLottiePainter(
            composition = composition,
            progress = progress,
            // The shipped files use none; skipping them skips the script engine's set-up.
            enableExpressions = false,
        )
    // The painter's size is a 1x1 placeholder until the file is parsed and the painter built.
    val ready = painter.intrinsicSize.width > 1f
    Image(
        painter = painter,
        contentDescription = props.contentDescription,
        contentScale = ContentScale.Fit,
        modifier =
            modifier
                .aspectRatio(props.animation.aspectRatio)
                .onVisibilityChanged(minFractionVisible = VISIBLE_FRACTION) { onScreen = it }
                .then(if (ready) Modifier.testTag(BAHR_LOTTIE_READY_TAG) else Modifier),
    )
}

/** For tests: a [BahrLottie] whose animation has loaded and draws (screenshots wait for it). */
const val BAHR_LOTTIE_READY_TAG = "bahr_lottie_ready"

/** The roles the shipped files name (`bahr.<role>`), from the current theme. */
private fun ColorScheme.lottieRoles(): Map<String, Color> =
    mapOf(
        "primary" to primary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "surface" to surface,
    )

/**
 * One file in one theme's colours. Its [key] names both, so the player's composition cache keeps one
 * parsed copy per theme, and a screen entered again draws from its first frame.
 */
private data class TintedLottieSpec(
    val animation: BahrLottie.Animation,
    val roles: Map<String, Color>,
) : LottieCompositionSpec {
    override val key: String =
        "bahr_${animation.name}_" + roles.entries.sortedBy { it.key }.joinToString("_") { it.value.toArgb().toString(HEX) }

    override suspend fun load(): LottieComposition =
        LottieComposition.parse(tintLottieJson(Res.readBytes(animation.path).decodeToString(), roles))
}

/** The last frame drawn before the out-point (see the clip in [BahrLottie]). */
private fun LottieComposition.lastWholeFrame(): Int = ceil(endFrame).toInt() - 1

/** Where a played-once animation rests, as progress: its last whole frame. */
private fun LottieComposition.restingProgress(): Float = ((lastWholeFrame() - startFrame) / durationFrames).coerceIn(0f, 1f)

private const val HEX = 16

/** Share of the box on screen for the animation to count as seen (and play). */
private const val VISIBLE_FRACTION = .25f

private const val BELL_WIDTH = 624f
private const val BELL_HEIGHT = 663f
private const val SAILBOAT_WIDTH = 348f
private const val SAILBOAT_HEIGHT = 240f
