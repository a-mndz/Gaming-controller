package com.europad.app.ui

import android.content.SharedPreferences
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.europad.app.input.ButtonHi
import com.europad.app.input.ButtonLo
import com.europad.app.input.DeckEngine
import com.europad.app.input.GyroSteering
import com.europad.app.input.InputFrame
import com.europad.app.net.ConnState
import com.europad.app.net.UdpTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val AX_LT = 4
private const val AX_RT = 5
private const val AX_STEER = 6

@Composable
fun TruckDeck2(
    transport: UdpTransport,
    prefs: SharedPreferences,
    onOpenConnection: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val deck = remember(transport) {
        DeckEngine(transport, scope).also { it.startSender(120) }
    }
    DisposableEffect(transport) { onDispose { deck.stopSender() } }

    var mode by remember { mutableStateOf(prefs.getString("truckMode", null) ?: "wheel") }
    var keymap by remember { mutableStateOf(loadKeymap(prefs)) }
    var showKeys by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showEditLayout by remember { mutableStateOf(false) }
    var gear by remember { mutableStateOf("D") }

    val gyro = remember { GyroSteering(context) }
    var gyroFailed by remember { mutableStateOf(false) }
    var steer by remember { mutableFloatStateOf(0f) }
    var wheelRangeDeg by remember { mutableStateOf(prefs.getInt("wheelRange", 360)) }
    var gyroRangeDeg by remember { mutableStateOf(prefs.getInt("gyroRange", 180)) }
    var deadzone by remember { mutableFloatStateOf(0.05f) }
    gyro.rangeDeg = gyroRangeDeg.toFloat()
    gyro.deadzone = deadzone

    fun tick() {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun setSteer(s: Float) {
        steer = s.coerceIn(-1f, 1f)
        deck.setInput { it.axes[AX_STEER] = (steer * Short.MAX_VALUE).toInt().toShort() }
    }

    LaunchedEffect(mode) {
        if (mode != "gyro") {
            gyro.stop()
            return@LaunchedEffect
        }
        if (!gyro.start()) {
            gyroFailed = true
            mode = "wheel"
            prefs.edit().putString("truckMode", "wheel").apply()
            return@LaunchedEffect
        }
        gyro.recenter()
        while (mode == "gyro") {
            setSteer(gyro.currentSteer())
            delay(8)
        }
    }

    var rtt by remember { mutableLongStateOf(-1L) }
    var loss by remember { mutableIntStateOf(0) }
    var reconnecting by remember { mutableStateOf(false) }
    LaunchedEffect(transport) {
        while (true) {
            rtt = transport.rtt
            loss = transport.lossPercent
            reconnecting = transport.state == ConnState.Reconnecting
            delay(250)
        }
    }

    var gearBusy by remember { mutableStateOf(false) }
    var wiperBusy by remember { mutableStateOf(false) }
    var washerBusy by remember { mutableStateOf(false) }

    fun tapHi(bits: Int, ms: Long, busy: () -> Boolean, setBusy: (Boolean) -> Unit) {
        if (busy()) return
        setBusy(true)
        scope.launch {
            deck.setInput { setHi(it, bits, true) }
            delay(ms)
            deck.setInput { setHi(it, bits, false) }
            setBusy(false)
        }
    }

    fun selectGear(g: String) {
        if (gearBusy) return
        tick()
        gear = g
        val bits = when (g) {
            "R" -> ButtonHi.GEAR_DN
            "N" -> ButtonHi.GEAR_UP or ButtonHi.GEAR_DN
            else -> ButtonHi.GEAR_UP
        }
        val ms = if (g == "N") 250L else 200L
        tapHi(bits, ms, { gearBusy }, { gearBusy = it })
    }

    if (showKeys) {
        KeymapPanel(
            transport = transport,
            prefs = prefs,
            keymap = keymap,
            mode = mode,
            wheelRangeDeg = wheelRangeDeg,
            gyroRangeDeg = gyroRangeDeg,
            onToggleMode = {
                if (!(mode == "wheel" && gyroFailed)) {
                    tick()
                    mode = if (mode == "gyro") "wheel" else "gyro"
                    prefs.edit().putString("truckMode", mode).apply()
                }
            },
            onCycleWheelRange = {
                tick()
                wheelRangeDeg = nextWheelRange(wheelRangeDeg)
                prefs.edit().putInt("wheelRange", wheelRangeDeg).apply()
            },
            onCycleGyroRange = {
                tick()
                gyroRangeDeg = nextGyroRange(gyroRangeDeg)
                gyro.rangeDeg = gyroRangeDeg.toFloat()
                prefs.edit().putInt("gyroRange", gyroRangeDeg).apply()
            },
            onRemap = { action, key -> keymap = (keymap + (action to key)).toMutableMap() },
            onClose = { showKeys = false },
            onDisconnect = {
                transport.close()
                onOpenConnection()
            },
            onEditLayout = {
                tick()
                showKeys = false
                showEditLayout = true
            },
        )
        return
    }
    
    if (showEditLayout) {
        val aspect = remember { 16f / 9f } // Use standard aspect for edit mode
        
        // Create default positions map from DeckLayout for all elements in current mode
        val defaultPositions = remember(mode, aspect) {
            buildMap {
                put("LIGHTS", DeckLayout.utilBtn(0, aspect))
                put("WIPER", DeckLayout.utilBtn(1, aspect))
                put("VIPER", DeckLayout.utilBtn(2, aspect))
                put("HANDBRAKE", DeckLayout.topRightBtn(0, aspect))
                put("SETTINGS", DeckLayout.topRightBtn(1, aspect))
                put("MENU", DeckLayout.topRightBtn(2, aspect))
                put("CAMERA", DeckLayout.camera(aspect))
                put("GEAR", DeckLayout.gearSel())
                put("INDICATORS", DeckLayout.arrows())
                
                if (mode == "gyro") {
                    put("GYRO_ACCEL", DeckLayout.gyroAccel())
                    put("GYRO_BRAKE", DeckLayout.gyroBrake())
                } else {
                    put("WHEEL", DeckLayout.wheel(aspect))
                    put("BRAKE", DeckLayout.brake())
                    put("ACCEL", DeckLayout.accel())
                }
            }
        }
        
        // Load current custom positions (or empty map if none exist)
        val currentCustomPositions = remember(mode) {
            LayoutPreferences.load(prefs, mode) ?: emptyMap()
        }
        
        LayoutEditPanel(
            prefs = prefs,
            mode = mode,
            defaultPositions = defaultPositions,
            customPositions = currentCustomPositions,
            onSave = { newPositions ->
                tick()
                LayoutPreferences.save(prefs, mode, newPositions)
                showEditLayout = false
            },
            onReset = {
                tick()
                LayoutPreferences.clear(prefs, mode)
                showEditLayout = false
            },
            onCancel = {
                tick()
                showEditLayout = false
            },
        )
        return
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(PitWall.Ground),
    ) {
        val pad = minOf(maxWidth, maxHeight) * 0.03f
        val innerW = maxWidth - pad * 2
        val innerH = maxHeight - pad * 2
        val aspect = if (innerH > 0.dp) innerW / innerH else DeckLayout.ASPECT
        
        // Load custom positions for the current mode, falling back to null if corrupted/missing
        val customPositions = remember(mode) {
            LayoutPreferences.load(prefs, mode)
        }
        
        /**
         * Get the position rectangle for an element, checking custom positions first,
         * then falling back to default DeckLayout positions.
         */
        fun getElementRect(id: String, defaultRect: DeckRect): DeckRect {
            return customPositions?.get(id)?.toDeckRect() ?: defaultRect
        }
        
        fun place(r: DeckRect) = Modifier
            .offset(x = pad + innerW * r.left, y = pad + innerH * r.top)
            .size(width = innerW * r.w, height = innerH * r.h)

        UtilButton(
            label = "LIGHTS", description = "pad:LIGHTS", tint = PitWall.ButtonLabel,
            modifier = place(getElementRect("LIGHTS", DeckLayout.utilBtn(0, aspect))),
            onPress = { on -> deck.setInput { setHi(it, ButtonHi.LIGHTS, on) } },
        ) { tint, m -> DeckIcons.lights(tint, m) }
        UtilButton(
            label = "WIPER", description = "pad:WIPER", tint = PitWall.ButtonLabel,
            modifier = place(getElementRect("WIPER", DeckLayout.utilBtn(1, aspect))),
            onPress = { down -> if (down) tapHi(ButtonHi.WIPERS, 120, { wiperBusy }, { wiperBusy = it }) },
        ) { tint, m -> DeckIcons.wiper(tint, m) }
        UtilButton(
            label = "VIPER", description = "pad:VIPER", tint = PitWall.ButtonLabel,
            modifier = place(getElementRect("VIPER", DeckLayout.utilBtn(2, aspect))),
            onPress = { down -> if (down) tapHi(ButtonHi.WIPERS, 700, { washerBusy }, { washerBusy = it }) },
        ) { tint, m -> DeckIcons.viper(tint, m) }

        GearSelector(gear, ::selectGear, place(getElementRect("GEAR", DeckLayout.gearSel())))

        UtilButton(
            label = "HANDBRAKE", description = "pad:HANDBRAKE", tint = PitWall.SignalRed,
            modifier = place(getElementRect("HANDBRAKE", DeckLayout.topRightBtn(0, aspect))),
            isTransparent = true,
            labelColor = PitWall.SignalRed,
            onPress = { on -> deck.setInput { setHi(it, ButtonHi.HANDBRAKE, on) } },
        ) { tint, m -> DeckIcons.circledP(tint, m) }

        UtilButton(
            label = "SETTINGS", description = "pad:SETTINGS", tint = PitWall.ButtonLabel,
            modifier = place(getElementRect("SETTINGS", DeckLayout.topRightBtn(1, aspect))),
            onPress = { showKeys = true },
        ) { tint, m -> DeckIcons.gearWheel(tint, m) }

        UtilButton(
            label = "MENU", description = "pad:MENU", tint = PitWall.ButtonLabel,
            modifier = place(getElementRect("MENU", DeckLayout.topRightBtn(2, aspect))),
            onPress = { showMenu = true },
        ) { tint, m -> DeckIcons.bars(tint, m) }

        SignalPair(deck, ::tick, place(getElementRect("INDICATORS", DeckLayout.arrows())))

        UtilButton(
            label = "CAMERA", description = "pad:CAMERA", tint = PitWall.ButtonLabel,
            modifier = place(getElementRect("CAMERA", DeckLayout.camera(aspect))),
            onPress = { down ->
                if (down) tick()
                deck.setInput { setLo(it, ButtonLo.BACK, down) }
            },
        ) { tint, m -> DeckIcons.camera(tint, m) }

        if (mode == "gyro") {
            MetallicPedal(
                label = "ACCELERATOR",
                deck = deck,
                tick = ::tick,
                axis = AX_RT,
                ridgeCount = 8,
                modifier = place(getElementRect("GYRO_ACCEL", DeckLayout.gyroAccel())),
            )
            MetallicPedal(
                label = "BRAKE",
                deck = deck,
                tick = ::tick,
                axis = AX_LT,
                ridgeCount = 6,
                modifier = place(getElementRect("GYRO_BRAKE", DeckLayout.gyroBrake())),
            )
        } else {
            WheelControl(
                steer = steer,
                wheelRangeDeg = wheelRangeDeg,
                gyroOn = false,
                onSteer = ::setSteer,
                onRecenter = { },
                tick = ::tick,
                modifier = place(getElementRect("WHEEL", DeckLayout.wheel(aspect))),
            )

            MetallicPedal("BRAKE", deck, ::tick, AX_LT, ridgeCount = 5, modifier = place(getElementRect("BRAKE", DeckLayout.brake())))
            MetallicPedal("ACCELERATOR", deck, ::tick, AX_RT, ridgeCount = 8, modifier = place(getElementRect("ACCEL", DeckLayout.accel())))
        }

        if (showMenu) {
            OptionsSheet(
                deck = deck,
                tick = ::tick,
                keymap = keymap,
                mode = mode,
                gyroFailed = gyroFailed,
                wheelRangeDeg = wheelRangeDeg,
                gyroRangeDeg = gyroRangeDeg,
                rtt = rtt,
                loss = loss,
                transportName = transport.transport,
                onToggleMode = {
                    if (!(mode == "wheel" && gyroFailed)) {
                        tick()
                        mode = if (mode == "gyro") "wheel" else "gyro"
                        prefs.edit().putString("truckMode", mode).apply()
                    }
                    showMenu = false
                },
                onCenter = { tick(); gyro.recenter(); steer = 0f; showMenu = false },
                onCycleWheelRange = {
                    tick()
                    wheelRangeDeg = nextWheelRange(wheelRangeDeg)
                    prefs.edit().putInt("wheelRange", wheelRangeDeg).apply()
                },
                onCycleGyroRange = {
                    tick()
                    gyroRangeDeg = nextGyroRange(gyroRangeDeg)
                    gyro.rangeDeg = gyroRangeDeg.toFloat()
                    prefs.edit().putInt("gyroRange", gyroRangeDeg).apply()
                },
                onMapKeys = { tick(); showMenu = false; showKeys = true },
                onOpenConnection = { tick(); showMenu = false; onOpenConnection() },
                onClose = { showMenu = false },
            )
        }
    }
}

private val WHEEL_RANGES = intArrayOf(180, 270, 360, 540, 900)
private fun nextWheelRange(cur: Int): Int = WHEEL_RANGES[(WHEEL_RANGES.indexOf(cur) + 1 + WHEEL_RANGES.size) % WHEEL_RANGES.size]

private val GYRO_RANGES = intArrayOf(90, 180, 270, 360)
private fun nextGyroRange(cur: Int): Int = GYRO_RANGES[(GYRO_RANGES.indexOf(cur) + 1 + GYRO_RANGES.size) % GYRO_RANGES.size]

@Composable
private fun UtilButton(
    label: String,
    description: String,
    tint: Color,
    modifier: Modifier,
    isTransparent: Boolean = false,
    labelColor: Color? = null,
    onPress: (down: Boolean) -> Unit = {},
    icon: (@Composable (Color, Modifier) -> Unit)? = null,
) {
    var down by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val bg = when {
        isTransparent -> if (down) Color(0x30FFFFFF) else Color.Transparent
        down -> PitWall.Indigo
        else -> PitWall.Panel
    }
    val borderModifier = if (isTransparent) {
        Modifier
    } else {
        Modifier.border(1.dp, PitWall.PanelBorder, shape)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(borderModifier)
            .semantics { contentDescription = description }
            .pointerInput(label) {
                detectTapGestures(onPress = {
                    down = true
                    onPress(true)
                    tryAwaitRelease()
                    onPress(false)
                    down = false
                })
            },
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                icon?.invoke(if (down) PitWall.Ink else tint, Modifier.fillMaxSize(0.55f))
            }
            Text(
                label,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp, start = 2.dp, end = 2.dp),
                color = if (down) PitWall.Ink else (labelColor ?: PitWall.ButtonLabel),
                fontSize = if (label == "HANDBRAKE" || label == "SETTINGS" || label == "ACCELERATOR") 8.5.sp else 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GearSelector(gear: String, onSelect: (String) -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(PitWall.Panel)
            .border(1.dp, PitWall.PanelBorder, shape)
            .semantics { contentDescription = "pad:GEAR" },
    ) {
        val gears = listOf("R", "N", "D")
        gears.forEachIndexed { index, g ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight(0.65f)
                        .align(Alignment.CenterVertically)
                        .background(PitWall.PanelBorder),
                )
            }
            val active = gear == g
            val color = when (g) {
                "R" -> PitWall.SignalRed
                "N" -> PitWall.NeutralWhite
                else -> PitWall.SignalGreen
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(g) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    g,
                    color = if (active) color else color.copy(alpha = 0.65f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                if (active) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 3.dp)
                            .width(18.dp)
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(color),
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalPair(deck: DeckEngine, tick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(PitWall.Panel)
            .border(1.dp, PitWall.PanelBorder, shape),
    ) {
        SignalSeg(ButtonHi.IND_L, pointRight = false, deck = deck, tick = tick, modifier = Modifier.weight(1f).fillMaxHeight())
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight(0.65f)
                .align(Alignment.CenterVertically)
                .background(PitWall.PanelBorder),
        )
        SignalSeg(ButtonHi.IND_R, pointRight = true, deck = deck, tick = tick, modifier = Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun SignalSeg(bit: Int, pointRight: Boolean, deck: DeckEngine, tick: () -> Unit, modifier: Modifier) {
    var down by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(if (down) PitWall.Indigo else Color.Transparent)
            .semantics { contentDescription = if (pointRight) "pad:IND_R" else "pad:IND_L" }
            .pointerInput(bit) {
                detectTapGestures(onPress = {
                    down = true
                    tick()
                    deck.setInput { setHi(it, bit, true) }
                    tryAwaitRelease()
                    deck.setInput { setHi(it, bit, false) }
                    down = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        DeckIcons.solidArrow(pointRight, if (down) PitWall.Ink else PitWall.ButtonLabel, Modifier.fillMaxSize(0.55f))
    }
}

@Composable
private fun WheelControl(
    steer: Float,
    wheelRangeDeg: Int = 360,
    gyroOn: Boolean,
    onSteer: (Float) -> Unit,
    onRecenter: () -> Unit,
    tick: () -> Unit,
    modifier: Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var currentAngleDeg by remember { mutableFloatStateOf(0f) }
    val animatedAngle = remember { androidx.compose.animation.core.Animatable(0f) }
    val maxTurnDeg = (wheelRangeDeg / 2f).coerceIn(90f, 450f)

    LaunchedEffect(steer, gyroOn) {
        if (gyroOn) {
            currentAngleDeg = steer * 180f
        }
    }

    Box(
        modifier = modifier
            .semantics { contentDescription = "pad:WHEEL" }
            .pointerInput(gyroOn, wheelRangeDeg) {
                if (gyroOn) {
                    detectTapGestures(onTap = {
                        tick()
                        onRecenter()
                    })
                } else {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    var lastAngleRad = 0.0

                    detectDragGestures(
                        onDragStart = { off ->
                            tick()
                            coroutineScope.launch { animatedAngle.stop() }
                            lastAngleRad = kotlin.math.atan2((off.y - cy).toDouble(), (off.x - cx).toDouble())
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val curAngleRad = kotlin.math.atan2((change.position.y - cy).toDouble(), (change.position.x - cx).toDouble())
                            var diffRad = curAngleRad - lastAngleRad
                            while (diffRad > PI) diffRad -= 2 * PI
                            while (diffRad < -PI) diffRad += 2 * PI

                            val diffDeg = (diffRad * 180.0 / PI).toFloat()
                            currentAngleDeg = (currentAngleDeg + diffDeg).coerceIn(-maxTurnDeg, maxTurnDeg)
                            lastAngleRad = curAngleRad
                            onSteer((currentAngleDeg / maxTurnDeg).coerceIn(-1f, 1f))
                        },
                        onDragEnd = {
                            coroutineScope.launch {
                                animatedAngle.snapTo(currentAngleDeg)
                                animatedAngle.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                                    ),
                                ) {
                                    currentAngleDeg = value
                                    onSteer((currentAngleDeg / maxTurnDeg).coerceIn(-1f, 1f))
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                animatedAngle.snapTo(currentAngleDeg)
                                animatedAngle.animateTo(
                                    targetValue = 0f,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
                                    ),
                                ) {
                                    currentAngleDeg = value
                                    onSteer((currentAngleDeg / maxTurnDeg).coerceIn(-1f, 1f))
                                }
                            }
                        },
                    )
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val glowWidth = 3.5.dp.toPx()
            val rimThickness = r * 0.22f
            val hubRadius = r * 0.35f
            val innerRimRadius = r - glowWidth - rimThickness

            val displayAngle = if (gyroOn) steer * 180f else currentAngleDeg
            rotate(displayAngle) {
                drawCircle(
                    color = PitWall.WheelGlow,
                    radius = r - glowWidth / 2f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(glowWidth),
                )

                drawCircle(
                    color = PitWall.WheelRim,
                    radius = r - glowWidth - rimThickness / 2f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(rimThickness),
                )

                drawCircle(
                    color = PitWall.WheelRimBorder,
                    radius = innerRimRadius,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                )

                val spokeColor = PitWall.WheelSpoke
                val leftSpoke = Path().apply {
                    moveTo(center.x - hubRadius * 0.85f, center.y - hubRadius * 0.45f)
                    lineTo(center.x - innerRimRadius, center.y - rimThickness * 0.75f)
                    lineTo(center.x - innerRimRadius, center.y + rimThickness * 0.75f)
                    lineTo(center.x - hubRadius * 0.85f, center.y + hubRadius * 0.45f)
                    close()
                }
                drawPath(leftSpoke, spokeColor)

                val rightSpoke = Path().apply {
                    moveTo(center.x + hubRadius * 0.85f, center.y - hubRadius * 0.45f)
                    lineTo(center.x + innerRimRadius, center.y - rimThickness * 0.75f)
                    lineTo(center.x + innerRimRadius, center.y + rimThickness * 0.75f)
                    lineTo(center.x + hubRadius * 0.85f, center.y + hubRadius * 0.45f)
                    close()
                }
                drawPath(rightSpoke, spokeColor)

                val bottomSpoke = Path().apply {
                    moveTo(center.x - hubRadius * 0.45f, center.y + hubRadius * 0.80f)
                    lineTo(center.x - rimThickness * 0.70f, center.y + innerRimRadius)
                    lineTo(center.x + rimThickness * 0.70f, center.y + innerRimRadius)
                    lineTo(center.x + hubRadius * 0.45f, center.y + hubRadius * 0.80f)
                    close()
                }
                drawPath(bottomSpoke, spokeColor)

                drawCircle(
                    color = PitWall.WheelRimBorder,
                    radius = hubRadius + 1.5.dp.toPx(),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                )

                drawCircle(
                    Brush.radialGradient(
                        0.0f to PitWall.WheelCenter,
                        1.0f to PitWall.WheelCenterDark,
                        center = center,
                        radius = hubRadius,
                    ),
                    radius = hubRadius,
                    center = center,
                )

                drawCircle(
                    color = Color(0x30FFFFFF),
                    radius = hubRadius - 1.5.dp.toPx(),
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                )

                val textPaint = Paint().apply {
                    color = android.graphics.Color.argb(240, 240, 239, 245)
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }
                val textSz = hubRadius * 0.36f
                textPaint.textSize = textSz
                drawContext.canvas.nativeCanvas.drawText(
                    "EURO",
                    center.x,
                    center.y - textSz * 0.18f,
                    textPaint,
                )
                drawContext.canvas.nativeCanvas.drawText(
                    "TRUCK",
                    center.x,
                    center.y + textSz * 0.95f,
                    textPaint,
                )
            }
        }
    }
}

@Composable
private fun MetallicPedal(
    label: String,
    deck: DeckEngine,
    tick: () -> Unit,
    axis: Int,
    ridgeCount: Int = 6,
    modifier: Modifier,
) {
    var down by remember { mutableStateOf(false) }
    val animatedValue = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .semantics { contentDescription = "pad:$label" }
                .pointerInput(axis) {
                    detectTapGestures(
                        onPress = {
                            down = true
                            tick()
                            deck.setInput { it.axes[axis] = Short.MAX_VALUE }
                            coroutineScope.launch { animatedValue.animateTo(1f, androidx.compose.animation.core.tween(60)) }
                            tryAwaitRelease()
                            deck.setInput { it.axes[axis] = 0 }
                            down = false
                            coroutineScope.launch { animatedValue.animateTo(0f, androidx.compose.animation.core.tween(100)) }
                        },
                    )
                },
        ) {
            val value = animatedValue.value
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cr = 10.dp.toPx()
                val bevel = 2.dp.toPx()

                drawRoundRect(
                    PitWall.PedalBorder,
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(cr),
                )

                val bodyInset = bevel
                drawRoundRect(
                    Brush.verticalGradient(
                        0f to (if (down) PitWall.PedalMid else PitWall.PedalLight),
                        0.12f to PitWall.PedalMid,
                        0.88f to PitWall.PedalMid,
                        1f to (if (down) PitWall.PedalLight else PitWall.PedalDark),
                    ),
                    topLeft = Offset(bodyInset, bodyInset),
                    size = Size(w - bodyInset * 2, h - bodyInset * 2),
                    cornerRadius = CornerRadius(cr - bevel),
                )

                val slotInsetX = w * 0.16f
                val slotAreaTop = h * 0.08f
                val slotAreaBot = h * 0.92f
                val slotStep = (slotAreaBot - slotAreaTop) / (ridgeCount + 1)
                val slotH = (h * 0.038f).coerceIn(4.dp.toPx(), 8.dp.toPx())
                val slotRadius = CornerRadius(slotH / 2f)

                for (i in 1..ridgeCount) {
                    val y = slotAreaTop + slotStep * i

                    drawRoundRect(
                        if (down) PitWall.Indigo.copy(alpha = 0.8f) else PitWall.PedalSlotDark,
                        topLeft = Offset(slotInsetX, y - slotH / 2f),
                        size = Size(w - slotInsetX * 2, slotH),
                        cornerRadius = slotRadius,
                    )
                    drawRoundRect(
                        PitWall.PedalSlotLight,
                        topLeft = Offset(slotInsetX + 1.dp.toPx(), y + slotH / 2f - 1.dp.toPx()),
                        size = Size(w - slotInsetX * 2 - 2.dp.toPx(), 1.dp.toPx()),
                        cornerRadius = CornerRadius(0.5.dp.toPx()),
                    )
                }

                if (value > 0f) {
                    drawRoundRect(
                        PitWall.Indigo.copy(alpha = 0.35f * value),
                        topLeft = Offset(bodyInset, bodyInset),
                        size = Size(w - bodyInset * 2, h - bodyInset * 2),
                        cornerRadius = CornerRadius(cr - bevel),
                    )
                }
            }
        }
        Text(
            label,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 16.dp),
            color = if (down) PitWall.Ink else PitWall.ButtonLabel,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun OptionsSheet(
    deck: DeckEngine,
    tick: () -> Unit,
    keymap: Map<String, String>,
    mode: String,
    gyroFailed: Boolean,
    wheelRangeDeg: Int,
    gyroRangeDeg: Int,
    rtt: Long = -1L,
    loss: Int = 0,
    transportName: String = "UDP",
    onToggleMode: () -> Unit,
    onCenter: () -> Unit,
    onCycleWheelRange: () -> Unit,
    onCycleGyroRange: () -> Unit,
    onMapKeys: () -> Unit,
    onOpenConnection: () -> Unit,
    onClose: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClose() }) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.65f)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PitWall.Panel)
                .border(1.dp, PitWall.PanelBorder, RoundedCornerShape(12.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("CONTROLS & SENSITIVITY", color = PitWall.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(
                    if (rtt >= 0) "$transportName · ${PitWall.rttDisplay(rtt)} · LOSS ${PitWall.lossDisplay(loss)}" else "DISCONNECTED",
                    color = if (rtt >= 0 && loss < 5) PitWall.SignalGreen else PitWall.Amber,
                    fontSize = 10.sp,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("STEERING MODE", color = PitWall.TowerGray, fontSize = 10.sp, letterSpacing = 1.sp)
                ChipLabel(
                    if (mode == "gyro") "MODE: GYROSCOPE" else "MODE: TOUCH WHEEL",
                    if (mode == "gyro") PitWall.SignalGreen else PitWall.Indigo,
                    onToggleMode,
                )
            }

            if (mode == "gyro") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("GYRO RANGE", color = PitWall.TowerGray, fontSize = 10.sp, letterSpacing = 1.sp)
                    ChipLabel("$gyroRangeDeg° (SENSITIVITY)", PitWall.WheelGlow, onCycleGyroRange)
                    ChipLabel("RECENTER GYRO", PitWall.Amber, onCenter)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("WHEEL RANGE", color = PitWall.TowerGray, fontSize = 10.sp, letterSpacing = 1.sp)
                    ChipLabel("$wheelRangeDeg° (SENSITIVITY)", PitWall.WheelGlow, onCycleWheelRange)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipLabel("MAP KEYS", PitWall.TowerGray, onMapKeys)
                ChipLabel("SERVERS / CONNECTION", PitWall.Indigo, onOpenConnection)
            }

            Row(
                Modifier.fillMaxWidth().height(36.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (name in listOf("HORN", "HAZARD", "BEAM", "ENGINE")) {
                    KeyButton(keymap[name] ?: name, TruckKeys.bits[name]!!, deck, tick, Modifier.weight(1f), compact = true)
                }
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    bit: Int,
    deck: DeckEngine,
    tick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var down by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (down) PitWall.Indigo else PitWall.Panel)
            .border(1.dp, PitWall.PanelBorder, RoundedCornerShape(6.dp))
            .semantics { contentDescription = "pad:$label" }
            .pointerInput(bit) {
                detectTapGestures(onPress = {
                    down = true
                    tick()
                    deck.setInput { setHi(it, bit, true) }
                    tryAwaitRelease()
                    deck.setInput { setHi(it, bit, false) }
                    down = false
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (down) PitWall.Ink else PitWall.ButtonLabel,
            fontSize = if (compact) 10.sp else 12.sp,
            letterSpacing = 1.sp,
        )
    }
}

private fun setHi(f: InputFrame, mask: Int, on: Boolean) {
    f.buttonsHi = if (on) f.buttonsHi or mask else f.buttonsHi and mask.inv()
}

private fun setLo(f: InputFrame, mask: Int, on: Boolean) {
    f.buttonsLo = if (on) f.buttonsLo or mask else f.buttonsLo and mask.inv()
}

/** All deck glyphs drawn with accurate vector canvas matching the reference design. */
private object DeckIcons {

    @Composable
    fun lights(tint: Color, modifier: Modifier) {
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val sw = (h * 0.08f).coerceAtLeast(1.8f)

            // Headlight dome (facing left)
            val domePath = Path().apply {
                moveTo(w * 0.50f, h * 0.22f)
                cubicTo(w * 0.22f, h * 0.22f, w * 0.22f, h * 0.78f, w * 0.50f, h * 0.78f)
                close()
            }
            drawPath(domePath, tint)

            // 3 Light beam rays pointing left
            for (i in 0..2) {
                val y = h * (0.34f + i * 0.16f)
                drawLine(tint, Offset(w * 0.08f, y), Offset(w * 0.20f, y), sw, cap = StrokeCap.Round)
            }
        }
    }

    @Composable
    fun wiper(tint: Color, modifier: Modifier) {
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val sw = (h * 0.08f).coerceAtLeast(1.8f)

            // Windshield arc
            drawArc(
                tint,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.12f, h * 0.20f),
                size = Size(w * 0.76f, h * 0.65f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round),
            )
            // Wiper arm and blade
            drawLine(
                tint,
                Offset(w * 0.50f, h * 0.82f),
                Offset(w * 0.68f, h * 0.32f),
                sw * 1.2f,
                cap = StrokeCap.Round,
            )
        }
    }

    @Composable
    fun viper(tint: Color, modifier: Modifier) {
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val sw = (h * 0.075f).coerceAtLeast(1.6f)

            // Windshield arc
            drawArc(
                tint,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(w * 0.12f, h * 0.16f),
                size = Size(w * 0.76f, h * 0.68f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = StrokeCap.Round),
            )

            // Spray rays + droplets radiating from bottom
            val pivotX = w * 0.50f
            val pivotY = h * 0.85f
            for (i in -2..2) {
                val angle = (270 + i * 22).toDouble() * PI / 180.0
                val len = h * 0.30f
                val dx = cos(angle).toFloat()
                val dy = sin(angle).toFloat()
                drawLine(
                    tint,
                    Offset(pivotX, pivotY),
                    Offset(pivotX + dx * (len * 0.65f), pivotY + dy * (len * 0.65f)),
                    sw * 0.9f,
                    cap = StrokeCap.Round,
                )
                // Droplet dot at top of ray
                drawCircle(
                    tint,
                    radius = sw * 0.7f,
                    center = Offset(pivotX + dx * len, pivotY + dy * len),
                )
            }
        }
    }

    @Composable
    fun circledP(tint: Color, modifier: Modifier) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val sw = (size.height * 0.09f).coerceAtLeast(2.2f)
                val r = size.minDimension * 0.40f
                drawCircle(tint, radius = r, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
                // Parking brake side parenthesis arcs
                drawArc(
                    tint,
                    startAngle = 125f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - r * 1.35f, center.y - r * 1.35f),
                    size = Size(r * 2.7f, r * 2.7f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(sw * 0.85f, cap = StrokeCap.Round),
                )
                drawArc(
                    tint,
                    startAngle = 305f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(center.x - r * 1.35f, center.y - r * 1.35f),
                    size = Size(r * 2.7f, r * 2.7f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(sw * 0.85f, cap = StrokeCap.Round),
                )
            }
            Text("P", color = tint, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }

    @Composable
    fun gearWheel(tint: Color, modifier: Modifier) {
        Canvas(modifier) {
            val r = size.minDimension * 0.28f
            val sw = (size.height * 0.075f).coerceAtLeast(1.8f)
            drawCircle(tint, radius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
            drawCircle(tint, radius = r * 0.35f, style = androidx.compose.ui.graphics.drawscope.Stroke(sw * 0.8f))
            for (i in 0..7) {
                val a = i * 45.0 * PI / 180.0
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                drawLine(
                    tint,
                    Offset(center.x + ca * (r + sw * 0.5f), center.y + sa * (r + sw * 0.5f)),
                    Offset(center.x + ca * (r + sw * 2.2f), center.y + sa * (r + sw * 2.2f)),
                    sw * 1.3f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }

    @Composable
    fun bars(tint: Color, modifier: Modifier) {
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val sw = (h * 0.09f).coerceAtLeast(2f)
            for (i in 0..2) {
                val y = h * (0.28f + i * 0.22f)
                drawLine(tint, Offset(w * 0.18f, y), Offset(w * 0.82f, y), sw, cap = StrokeCap.Round)
            }
        }
    }

    @Composable
    fun solidArrow(pointRight: Boolean, tint: Color, modifier: Modifier) {
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val arrowPath = Path().apply {
                if (pointRight) {
                    moveTo(w * 0.72f, h * 0.50f)
                    lineTo(w * 0.40f, h * 0.22f)
                    lineTo(w * 0.40f, h * 0.38f)
                    lineTo(w * 0.20f, h * 0.38f)
                    lineTo(w * 0.20f, h * 0.62f)
                    lineTo(w * 0.40f, h * 0.62f)
                    lineTo(w * 0.40f, h * 0.78f)
                    close()
                } else {
                    moveTo(w * 0.28f, h * 0.50f)
                    lineTo(w * 0.60f, h * 0.22f)
                    lineTo(w * 0.60f, h * 0.38f)
                    lineTo(w * 0.80f, h * 0.38f)
                    lineTo(w * 0.80f, h * 0.62f)
                    lineTo(w * 0.60f, h * 0.62f)
                    lineTo(w * 0.60f, h * 0.78f)
                    close()
                }
            }
            drawPath(arrowPath, tint)
        }
    }

    @Composable
    fun camera(tint: Color, modifier: Modifier) {
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            val sw = (h * 0.075f).coerceAtLeast(1.8f)
            // Top viewfinder notch
            drawRoundRect(
                tint,
                topLeft = Offset(w * 0.36f, h * 0.20f),
                size = Size(w * 0.28f, h * 0.14f),
                cornerRadius = CornerRadius(h * 0.04f),
            )
            // Camera body
            drawRoundRect(
                tint,
                topLeft = Offset(w * 0.14f, h * 0.30f),
                size = Size(w * 0.72f, h * 0.50f),
                cornerRadius = CornerRadius(h * 0.09f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw),
            )
            // Lens circle
            drawCircle(
                tint,
                radius = h * 0.14f,
                center = Offset(w * 0.50f, h * 0.55f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(sw),
            )
        }
    }
}
