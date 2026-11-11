package nl.muorg.android.ui.component

import android.app.Activity
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import nl.muorg.android.R
import nl.muorg.android.ui.icon.MageIcon
import nl.muorg.android.ui.theme.OnboardingBrand
import nl.muorg.android.ui.theme.OnboardingType

/* -------------------------------------------------------------------------- */
/*  Page chrome                                                               */
/* -------------------------------------------------------------------------- */

/** Widest the onboarding column gets; phones never reach it, tablets stop here. */
private val OnboardingMaxWidth = 520.dp

private val PageHorizontalPadding = 24.dp

/** Whether the looping Orc/logo motion may run (system animator scale != 0). */
private val LocalOnboardingMotion = staticCompositionLocalOf { true }

private fun animationsEnabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f

/**
 * Root of an onboarding screen: brand background, light system-bar icons, and
 * the reduced-motion flag read once for every Orc below it.
 */
@Composable
fun OnboardingScreen(content: @Composable BoxScope.() -> Unit) {
    val context = LocalContext.current
    var motion by remember { mutableStateOf(animationsEnabled(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                motion = animationsEnabled(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    val window = (context as? Activity)?.window
    DisposableEffect(window) {
        window?.let(OnboardingSystemBars::acquire)
        onDispose { window?.let(OnboardingSystemBars::release) }
    }

    CompositionLocalProvider(
        LocalOnboardingMotion provides motion,
        LocalContentColor provides OnboardingBrand.Text,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OnboardingBrand.Ink)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            content = content,
        )
    }
}

/**
 * The onboarding background is always dark, so the status and navigation bar
 * icons must be light even when the app theme is light. Reference-counted:
 * during a route transition the outgoing and incoming screen overlap, and the
 * theme's own value is only restored when the last one leaves.
 */
private object OnboardingSystemBars {
    private var holders = 0
    private var savedStatus = false
    private var savedNavigation = false

    fun acquire(window: android.view.Window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (holders++ == 0) {
            savedStatus = controller.isAppearanceLightStatusBars
            savedNavigation = controller.isAppearanceLightNavigationBars
        }
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    fun release(window: android.view.Window) {
        if (--holders > 0) return
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = savedStatus
        controller.isAppearanceLightNavigationBars = savedNavigation
    }
}

/**
 * Scrolling page body. [top] and [bottom] are the canvas' page padding, which
 * already contains the system bars on a standard phone; taller bars (cutouts,
 * 3-button navigation) push it out instead of overlapping content.
 *
 * The content column is at least one viewport tall, so a `Spacer(weight(1f))`
 * pins the call to action to the bottom on tall screens and scrolls on short
 * ones. [decoration] draws behind the content, in scroll coordinates; it
 * receives the y of the content's top edge in px.
 */
@Composable
fun OnboardingPage(
    modifier: Modifier = Modifier,
    top: Dp = 60.dp,
    bottom: Dp = 36.dp,
    decoration: (DrawScope.(contentTop: Float) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val padding = PaddingValues(
        start = PageHorizontalPadding,
        end = PageHorizontalPadding,
        top = maxOf(top, statusBar + 12.dp),
        bottom = maxOf(bottom, navigationBar + 12.dp),
    )
    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = maxHeight
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .then(
                    if (decoration == null) Modifier
                    else Modifier.drawBehind { decoration(padding.calculateTopPadding().toPx()) },
                ),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = OnboardingMaxWidth)
                    .fillMaxWidth()
                    .heightIn(min = viewport)
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Press + focus                                                             */
/* -------------------------------------------------------------------------- */

private val LipDepth = 5.dp
private val PressTravel = 4.dp

/** 3dp [OnboardingBrand.Spotlight] outline, 3dp off the edge, for keyboard / D-pad focus. */
@Composable
fun Modifier.focusRing(interactionSource: InteractionSource, shape: CornerBasedShape): Modifier {
    val focused by interactionSource.collectIsFocusedAsState()
    return drawBehind {
        if (!focused) return@drawBehind
        val ring = 3.dp.toPx()
        val inflate = 3.dp.toPx() + ring / 2
        drawRoundRect(
            color = OnboardingBrand.Spotlight,
            topLeft = Offset(-inflate, -inflate),
            size = Size(size.width + 2 * inflate, size.height + 2 * inflate),
            cornerRadius = CornerRadius(shape.topStart.toPx(size, this) + inflate),
            style = Stroke(ring),
        )
    }
}

/**
 * The "lip" press effect: the surface sits on a solid, blur-free [lipColor]
 * shadow 5dp below it; pressed, it travels down 4dp (80ms) until only 1dp of
 * lip shows. Apply before the surface's own border/background so they travel
 * with it. [enabled] = false drops the lip and the travel.
 */
@Composable
fun Modifier.chunkyPress(
    interactionSource: InteractionSource,
    lipColor: Color,
    shape: CornerBasedShape,
    enabled: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val travel by animateDpAsState(
        targetValue = if (pressed && enabled) PressTravel else 0.dp,
        animationSpec = tween(80),
        label = "lipTravel",
    )
    return this
        .drawBehind {
            if (!enabled) return@drawBehind
            val outline = shape.createOutline(size, layoutDirection, this)
            translate(top = LipDepth.toPx()) { drawOutline(outline, lipColor) }
        }
        .offset { IntOffset(0, travel.roundToPx()) }
        .focusRing(interactionSource, shape)
}

/* -------------------------------------------------------------------------- */
/*  Buttons                                                                   */
/* -------------------------------------------------------------------------- */

/** 44dp visible square with a 48dp touch target. */
@Composable
fun OnboardingBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val source = remember { MutableInteractionSource() }
    // The 48dp target sits 2dp outside the 44dp square on every side, so the
    // visible square lands exactly on the page padding. The 4dp it adds to the
    // layout is the canvas' 4dp gap above the Orc stage.
    Box(
        modifier = modifier
            .offset((-2).dp, (-2).dp)
            .size(48.dp)
            .focusRing(source, shape)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(OnboardingBrand.Hull, shape)
                .border(1.5.dp, OnboardingBrand.Groove, shape),
            contentAlignment = Alignment.Center,
        ) {
            MageIcon("arrow-left", Modifier.size(22.dp), tint = OnboardingBrand.Text)
        }
    }
}

/**
 * Full-width pill. [busy] keeps the enabled look but ignores input (used while
 * a request is in flight); [enabled] = false is the grey, lip-less state.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    val shape = RoundedCornerShape(percent = 50)
    val source = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .chunkyPress(source, OnboardingBrand.MossLip, shape, enabled = enabled)
            .background(if (enabled) OnboardingBrand.Moss else OnboardingBrand.DisabledBg, shape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled && !busy,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = OnboardingType.Button,
            color = if (enabled) OnboardingBrand.Ink else OnboardingBrand.Mist,
            textAlign = TextAlign.Center,
        )
    }
}

/* -------------------------------------------------------------------------- */
/*  Orc                                                                       */
/* -------------------------------------------------------------------------- */

/** What an [OrcPortrait] does while it is on screen. */
sealed interface OrcMotion {
    data object Still : OrcMotion

    /** 4dp hop, 1.6s loop; [delayMillis] holds the first hop back. */
    data class Hop(val delayMillis: Int = 0) : OrcMotion

    /** ±3° wiggle, 350ms loop, pivoting at the feet. */
    data object Wiggle : OrcMotion
}

private val OrcPortraitSize = Size(140f, 175f)

/** The part of the 240×270 Orc scenes the canvas shows: x 20..220, y 15..265. */
private val OrcCrop = Rect(20f, 15f, 220f, 265f)
private val OrcViewport = Size(240f, 270f)

/**
 * Draws [drawable] so that [crop] (in the drawable's viewport units) exactly
 * fills this composable, clipped to it. Lets a full-bleed asset be reused at
 * the canvas' framing without a second copy of the artwork.
 */
@Composable
private fun CroppedVector(
    @DrawableRes drawable: Int,
    viewport: Size,
    crop: Rect,
    modifier: Modifier,
) {
    val painter = painterResource(drawable)
    Canvas(modifier) {
        val sx = size.width / crop.width
        val sy = size.height / crop.height
        clipRect {
            translate(left = -crop.left * sx, top = -crop.top * sy) {
                with(painter) { draw(Size(viewport.width * sx, viewport.height * sy)) }
            }
        }
    }
}

/**
 * An Orc scene cropped to the 140×175dp framing the onboarding canvas uses,
 * feet on the bottom edge. Put it in an [OrcStage]. [contentDescription] = null
 * marks it decorative (the card around it carries the label).
 */
@Composable
fun OrcPortrait(
    variant: OrcVariant,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    motion: OrcMotion = OrcMotion.Still,
) {
    val effective = if (LocalOnboardingMotion.current) motion else OrcMotion.Still
    val motionModifier = when (effective) {
        OrcMotion.Still -> Modifier
        is OrcMotion.Hop -> Modifier.loopingOffset(distance = 4.dp, halfCycleMillis = 800, delayMillis = effective.delayMillis)
        OrcMotion.Wiggle -> Modifier.wiggle()
    }
    CroppedVector(
        drawable = variant.drawable,
        viewport = OrcViewport,
        crop = OrcCrop,
        modifier = modifier
            .size(OrcPortraitSize.width.dp, OrcPortraitSize.height.dp)
            .then(motionModifier)
            .then(
                if (contentDescription == null) Modifier
                else Modifier.semantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                },
            ),
    )
}

/** Circle or rectangle panel that clips its content and bottom-aligns the Orc. */
@Composable
fun OrcStage(
    color: Color,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color),
        contentAlignment = Alignment.BottomCenter,
        content = content,
    )
}

/** The app logo as the canvas frames it: a 124dp box with the monster filling it. */
@Composable
fun BobbingLogo(modifier: Modifier = Modifier) {
    val motion = LocalOnboardingMotion.current
    // `ic_launcher_foreground` is an adaptive-icon layer: the monster is only
    // 144 of its 330 viewport units, inside a safe-zone margin. Crop to the
    // region that puts the monster at the canvas' size within a 124dp box.
    CroppedVector(
        drawable = R.drawable.ic_launcher_foreground,
        viewport = Size(330f, 330f),
        crop = Rect(72.7f, 71.5f, 257.3f, 256.1f),
        modifier = modifier
            .size(124.dp)
            .then(if (motion) Modifier.loopingOffset(distance = 6.dp, halfCycleMillis = 1600) else Modifier),
    )
}

/** Moves up by [distance] and back, ease-in-out each way, forever. */
@Composable
private fun Modifier.loopingOffset(distance: Dp, halfCycleMillis: Int, delayMillis: Int = 0): Modifier {
    val transition = rememberInfiniteTransition(label = "orcHop")
    val lift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(halfCycleMillis, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(delayMillis),
        ),
        label = "lift",
    )
    return graphicsLayer { translationY = -lift * distance.toPx() }
}

@Composable
private fun Modifier.wiggle(): Modifier {
    val transition = rememberInfiniteTransition(label = "orcWiggle")
    val phase by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(175, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "angle",
    )
    // The canvas pivots at 50% 90%: the feet, not the edge of the image box.
    return graphicsLayer {
        rotationZ = phase
        transformOrigin = TransformOrigin(0.5f, 0.9f)
    }
}

/* -------------------------------------------------------------------------- */
/*  Entrance                                                                  */
/* -------------------------------------------------------------------------- */

/**
 * Fade + rise-from-[slideFraction]-of-own-height when [visible] flips on.
 * Done in the graphics layer rather than `AnimatedVisibility` so unrevealed
 * items keep their layout space and nothing below them jumps as they arrive.
 */
@Composable
fun Modifier.entrance(visible: Boolean, durationMillis: Int, slideFraction: Float): Modifier {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis, easing = FastOutSlowInEasing),
        label = "entrance",
    )
    return graphicsLayer {
        alpha = progress
        translationY = (1f - progress) * size.height * slideFraction
    }
}

/* -------------------------------------------------------------------------- */
/*  Choice card                                                               */
/* -------------------------------------------------------------------------- */

private val ChoiceCardShape = RoundedCornerShape(26.dp)

/**
 * One tap target: Orc panel on the left, title, subtitle and a (decorative)
 * arrow on the right. Announced as a single button, "title. subtitle".
 */
@Composable
fun ChoiceCard(
    title: String,
    subtitle: String,
    accent: Color,
    stageColor: Color,
    orc: OrcVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hopDelayMillis: Int = 0,
) {
    val source = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 184.dp)
            .height(IntrinsicSize.Min)
            .chunkyPress(source, OnboardingBrand.Lip, ChoiceCardShape)
            .border(1.5.dp, OnboardingBrand.Groove, ChoiceCardShape)
            .background(OnboardingBrand.Hull, ChoiceCardShape)
            .clip(ChoiceCardShape)
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = "$title. $subtitle" },
    ) {
        OrcStage(
            color = stageColor,
            shape = RectangleShape,
            modifier = Modifier.width(140.dp).fillMaxHeight(),
        ) {
            OrcPortrait(orc, contentDescription = null, motion = OrcMotion.Hop(hopDelayMillis))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 18.dp, top = 20.dp, end = 18.dp, bottom = 18.dp),
        ) {
            Text(title, style = OnboardingType.CardTitle, color = OnboardingBrand.Text)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = OnboardingType.Body14, color = OnboardingBrand.Mist)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .size(44.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                MageIcon("arrow-right", Modifier.size(22.dp), tint = OnboardingBrand.Ink)
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/*  Text                                                                      */
/* -------------------------------------------------------------------------- */

@Composable
fun OnboardingHeadline(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = style,
        color = OnboardingBrand.Text,
        textAlign = TextAlign.Center,
        modifier = modifier.semantics { heading() },
    )
}

/** Caps label above a [Hull] box. The label is never replaced by the placeholder. */
@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = OnboardingType.FieldLabel, color = OnboardingBrand.Mist)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (!focused) return@drawBehind
                    val halo = 3.dp.toPx()
                    drawRoundRect(
                        color = OnboardingBrand.Spotlight.copy(alpha = 0.25f),
                        topLeft = Offset(-halo, -halo),
                        size = Size(size.width + 2 * halo, size.height + 2 * halo),
                        cornerRadius = CornerRadius(18.dp.toPx() + halo),
                    )
                }
                .background(OnboardingBrand.Hull, shape)
                .border(1.5.dp, if (focused) OnboardingBrand.Spotlight else OnboardingBrand.Groove, shape),
            contentAlignment = Alignment.CenterEnd,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = fieldModifier.fillMaxWidth().heightIn(min = 56.dp),
                singleLine = true,
                textStyle = OnboardingType.Body16.copy(color = OnboardingBrand.Text),
                cursorBrush = SolidColor(OnboardingBrand.Spotlight),
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                interactionSource = source,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 18.dp, end = if (trailing != null) 60.dp else 18.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = OnboardingType.Body16,
                                color = OnboardingBrand.Mist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (trailing != null) {
                Box(Modifier.padding(end = 4.dp)) { trailing() }
            }
        }
    }
}

/** 48dp icon button used inside a [LabeledField] (the API-key eye). */
@Composable
fun FieldIconButton(
    iconName: String,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        MageIcon(iconName, Modifier.size(22.dp), tint = tint, contentDescription = contentDescription)
    }
}

/** The dashed outline of the "Add folder" button. */
fun DrawScope.drawDashedOutline(color: Color, cornerRadius: Dp) {
    val stroke = 2.dp.toPx()
    val inset = stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(cornerRadius.toPx() - inset),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
        ),
    )
}

/** Four concentric rings around [center], as in the canvas: radii 0.23/0.32/0.41/0.5 of 460dp. */
fun DrawScope.drawOnboardingRings(center: Offset) {
    val span = 460.dp.toPx()
    val rings = listOf(
        0.23f to OnboardingBrand.GrooveRing,
        0.32f to OnboardingBrand.GrooveRing,
        0.41f to OnboardingBrand.GrooveRingMid,
        0.50f to OnboardingBrand.GrooveRingOuter,
    )
    rings.forEach { (fraction, color) ->
        drawCircle(color = color, radius = span * fraction, center = center, style = Stroke(2.dp.toPx()))
    }
}
