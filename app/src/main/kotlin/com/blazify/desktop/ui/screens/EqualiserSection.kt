package com.blazify.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.blazify.desktop.Typing
import com.blazify.desktop.audio.AutoEq
import com.blazify.desktop.audio.CustomCurve
import com.blazify.desktop.audio.Equaliser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.blazify.desktop.ui.Blaze
import com.blazify.desktop.ui.Blz
import com.blazify.desktop.ui.hoverBackground
import com.blazify.desktop.ui.hoverGlow
import com.blazify.desktop.ui.rememberHovered

/**
 * Blazify Project (C) 2026
 * Licensed under GPL-3.0
 */

/**
 * The equaliser, as the row of faders everyone already knows how to use.
 *
 * Vertical because the shape of the curve is the information: a glance across
 * the tops of the faders says what it does, which a column of numbers never
 * would. The frequencies are labelled underneath in the units they're written
 * in — hertz below a thousand, kilohertz above.
 */
@Composable
fun EqualiserSection(
    section: @Composable (String, (() -> Unit)?, @Composable () -> Unit) -> Unit,
) {
    LaunchedEffect(Unit) { Equaliser.load() }

    section("Equaliser", Equaliser::flatten) {
        if (Equaliser.bands.isEmpty()) {
            Text(
                "The audio library didn't offer an equaliser on this machine",
                color = Blz.dim, fontSize = 12.5.sp,
            )
            return@section
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Shape the sound", color = Blz.ink, fontSize = 13.5.sp)
                    Text(
                        Equaliser.preset ?: if (Equaliser.on) "Set by hand" else "Off",
                        color = Blz.dim, fontSize = 11.5.sp,
                    )
                }
                Toggle(Equaliser.on) { Equaliser.setEnabled(it) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Equaliser.presets.take(7).forEach { name ->
                    Chip(name, name == Equaliser.preset) { Equaliser.choosePreset(name) }
                }
            }

            Row(
                Modifier.fillMaxWidth().height(160.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Equaliser.bands.forEachIndexed { at, hertz ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Fader(
                            value = Equaliser.gains.getOrElse(at) { 0f },
                            enabled = Equaliser.on,
                            modifier = Modifier.weight(1f),
                        ) { Equaliser.setBand(at, it) }
                        Text(label(hertz), color = Blz.dim, fontSize = 9.5.sp)
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Level", color = Blz.muted, fontSize = 12.sp, modifier = Modifier.width(44.dp))
                Box(Modifier.weight(1f)) {
                    Slider(Equaliser.preamp, Equaliser.on) { Equaliser.changePreamp(it) }
                }
                Text(
                    "%+.0f dB".format(Equaliser.preamp),
                    color = Blz.dim, fontSize = 11.sp,
                    modifier = Modifier.width(56.dp).padding(start = 10.dp),
                )
            }

            Chip("Flatten", false) { Equaliser.flatten() }
        }
    }

    if (Equaliser.bands.isNotEmpty()) {
        section("Headphone correction", null) { HeadphoneCorrection() }
    }
}

/**
 * Picking a headphone model and putting its AutoEq correction on.
 *
 * Searched as you type, against a list fetched the first time it's needed.
 * A picked curve is kept under the model's name beside the presets, so it is
 * one press next time and needs no connection.
 */
@Composable
private fun HeadphoneCorrection() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<AutoEq.Model>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            searched = false
            return@LaunchedEffect
        }
        delay(300)
        results = AutoEq.search(query)
        searched = true
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Corrects your headphones towards a neutral sound, using AutoEq's public " +
                "measurements of thousands of models.",
            color = Blz.dim, fontSize = 12.sp, lineHeight = 18.sp,
        )
        Text(
            "An approximation: AutoEq's detailed curve is read at the centre of each of " +
                "these ten bands, so the shape is right but the fine detail is not.",
            color = Blz.dim, fontSize = 11.5.sp, lineHeight = 17.sp,
        )

        if (Equaliser.custom.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Equaliser.custom.take(5).forEach { curve ->
                    Chip(curve.name.removePrefix(AUTOEQ_PREFIX), curve.name == Equaliser.preset) {
                        Equaliser.choosePreset(curve.name)
                    }
                }
                Equaliser.preset?.takeIf { name -> Equaliser.custom.any { it.name == name } }?.let { name ->
                    Chip("Remove", false) { Equaliser.forgetCustom(name) }
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Blz.surfaceHigh)
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            if (query.isEmpty()) Text("Search for your headphones, e.g. HD 600", color = Blz.dim, fontSize = 12.5.sp)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Blz.ink, fontSize = 12.5.sp),
                cursorBrush = SolidColor(Blaze.Amber),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { Typing.active = it.isFocused },
            )
        }

        when {
            AutoEq.busy -> Text("Fetching from AutoEq…", color = Blz.muted, fontSize = 12.sp)
            AutoEq.trouble != null -> Text(AutoEq.trouble!!, color = Blaze.Amber, fontSize = 12.sp)
            searched && results.isEmpty() -> Text("No model by that name", color = Blz.muted, fontSize = 12.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            results.take(8).forEach { model ->
                ModelRow(model) {
                    scope.launch {
                        AutoEq.correction(model, Equaliser.bands).onSuccess { (gains, preamp) ->
                            Equaliser.keepCustom(CustomCurve(AUTOEQ_PREFIX + model.name, gains, preamp))
                            query = ""
                        }
                    }
                }
            }
        }
    }
}

/** Kept curves are named after the model, marked so they never collide with a library preset. */
private const val AUTOEQ_PREFIX = "AutoEq: "

@Composable
private fun ModelRow(model: AutoEq.Model, onPick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .hoverBackground(Blz.hover, hovered, source)
            .clickable(onClick = onPick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            model.name, color = Blz.ink, fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "measured by ${model.source}", color = Blz.dim, fontSize = 11.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * One vertical fader.
 *
 * Zero sits in the middle and the fill runs from there rather than from the
 * bottom, so a cut reads as a cut instead of as a smaller boost.
 */
@Composable
private fun Fader(
    value: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit,
) {
    var height by remember { mutableStateOf(1) }
    val fraction = ((value + Equaliser.RANGE) / (Equaliser.RANGE * 2)).coerceIn(0f, 1f)

    fun fromY(y: Float) {
        val portion = 1f - (y / height).coerceIn(0f, 1f)
        onChange(portion * Equaliser.RANGE * 2 - Equaliser.RANGE)
    }

    BoxWithConstraints(
        modifier
            .width(26.dp)
            .onSizeChanged { height = it.height.coerceAtLeast(1) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { fromY(it.y) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ -> fromY(change.position.y) }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        val travel = maxHeight - 14.dp

        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(50))
                .background(Blz.surfaceHigh)
                .align(Alignment.TopCenter),
        )
        // The line through the middle is zero, so a cut reads as a cut rather
        // than as a smaller boost.
        Box(Modifier.width(12.dp).height(1.dp).background(Blz.line).align(Alignment.Center))

        Box(
            Modifier
                .padding(top = travel * (1f - fraction))
                .size(14.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                    else Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh)),
                ),
        )
    }
}

@Composable
private fun Slider(value: Float, enabled: Boolean, onChange: (Float) -> Unit) {
    var width by remember { mutableStateOf(1) }
    val fraction = ((value + Equaliser.RANGE) / (Equaliser.RANGE * 2)).coerceIn(0f, 1f)

    fun fromX(x: Float) {
        val portion = (x / width).coerceIn(0f, 1f)
        onChange(portion * Equaliser.RANGE * 2 - Equaliser.RANGE)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { fromX(it.x) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures { change, _ -> fromX(change.position.x) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(Blz.surfaceHigh))
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    if (enabled) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                    else Brush.linearGradient(listOf(Blz.line, Blz.line)),
                ),
        )
    }
}

@Composable
private fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier
            .width(38.dp)
            .height(21.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (on) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                else Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh)),
            )
            .clickable { onChange(!on) },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(15.dp)
                .clip(CircleShape)
                .background(if (on) Blaze.OnAmber else Blz.muted),
        )
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    val (source, hovered) = rememberHovered()
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (on) Brush.linearGradient(listOf(Blaze.Amber, Blaze.Ember))
                else Brush.linearGradient(listOf(Blz.surfaceHigh, Blz.surfaceHigh)),
            )
            .then(
                if (on) Modifier.hoverGlow(hovered, source)
                else Modifier.hoverBackground(Blz.hover, hovered, source),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (on) Blaze.OnAmber else Blz.muted,
            fontSize = 12.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Hertz below a thousand, kilohertz above — as anyone would say them aloud. */
private fun label(hertz: Float): String = when {
    hertz >= 1000f -> "%.0fk".format(hertz / 1000f)
    else -> "%.0f".format(hertz)
}
