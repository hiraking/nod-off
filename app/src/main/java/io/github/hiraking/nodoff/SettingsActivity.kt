package io.github.hiraking.nodoff

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.hiraking.nodoff.core.Config
import io.github.hiraking.nodoff.core.RestorePolicy
import io.github.hiraking.nodoff.core.TimerState
import io.github.hiraking.nodoff.platform.MediaPauser
import io.github.hiraking.nodoff.platform.NodOffStore
import io.github.hiraking.nodoff.ui.NodOffColors
import io.github.hiraking.nodoff.ui.NodOffTheme

/**
 * アプリの画面（設定と初回の準備）。レイアウトと文言は DESIGN.md「画面：設定」。
 * ランチャーからも、タイルの長押し（QS_TILE_PREFERENCES）からも開く。
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var store: NodOffStore

    /** 準備の 3 項目が済んでいるか。onResume と各操作の結果で読み直す */
    private var setup by mutableStateOf(SetupStatus(listenerAccess = false, notifications = false, tileAdded = false))
    private var durationMin by mutableStateOf(Config.DEFAULT_DURATION_MIN)

    override fun onCreate(savedInstanceState: Bundle?) {
        // OS がライトでも黒基調のまま。システムバーの文字は常に明るくする
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        store = NodOffStore(this)
        durationMin = store.defaultDurationMin

        setContent {
            NodOffTheme {
                val notificationPermission = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    // 2 回断ると、ダイアログはもう出ない。そのときはアプリの通知の設定を開く
                    if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        openAppNotificationSettings()
                    }
                    reloadSetup()
                }
                SettingsScreen(
                    setup = setup,
                    durationMin = durationMin,
                    onGrantListenerAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onGrantNotifications = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onAddTile = ::requestAddTile,
                    onDurationSelected = ::saveDuration,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // フェードの途中は FadeService が音量を戻す。ここで戻すと、下げ続けた音量が戻らなくなる
        if (store.state !is TimerState.Fading) {
            TimerController.dispatchRestore(this, RestorePolicy.Trigger.APP_USED)
        }
        durationMin = store.defaultDurationMin
        reloadSetup()
    }

    private fun reloadSetup() {
        setup = SetupStatus(
            listenerAccess = MediaPauser(this).hasListenerAccess(),
            notifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            tileAdded = store.tileAdded,
        )
    }

    private fun saveDuration(minutes: Int) {
        store.defaultDurationMin = minutes
        durationMin = minutes
    }

    private fun openAppNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        )
    }

    /** システムのダイアログでタイルの追加を頼む。追加されたか、もうあれば「済み」として保存する */
    private fun requestAddTile() {
        getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(this, NodOffTileService::class.java),
            "nod-off",
            Icon.createWithResource(this, R.drawable.ic_logo_mono),
            mainExecutor,
        ) { result ->
            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                store.tileAdded = true
            }
            reloadSetup()
        }
    }
}

data class SetupStatus(val listenerAccess: Boolean, val notifications: Boolean, val tileAdded: Boolean) {
    val doneCount: Int get() = listOf(listenerAccess, notifications, tileAdded).count { it }
    val allDone: Boolean get() = doneCount == TOTAL

    companion object {
        const val TOTAL = 3
    }
}

private val ScreenPadding = 20.dp
private val ChipShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(16.dp)

private val LabelStyle = TextStyle(fontSize = 10.5.sp, color = NodOffColors.textSecondary, letterSpacing = 0.08.em)
private val BodyStyle = TextStyle(fontSize = 13.sp, color = NodOffColors.textPrimary)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    setup: SetupStatus,
    durationMin: Int,
    onGrantListenerAccess: () -> Unit,
    onGrantNotifications: () -> Unit,
    onAddTile: () -> Unit,
    onDurationSelected: (Int) -> Unit,
) {
    var showCustomSheet by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .moonlightBackground(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding, vertical = 24.dp),
        ) {
            Header()
            Spacer(Modifier.height(32.dp))

            // 3 項目がすべて済んだら、準備の欄ごと出さない
            if (!setup.allDone) {
                SetupSection(setup, onGrantListenerAccess, onGrantNotifications, onAddTile)
                Spacer(Modifier.height(32.dp))
            }

            DurationSection(
                durationMin = durationMin,
                onPresetSelected = onDurationSelected,
                onCustomClicked = { showCustomSheet = true },
            )
        }
    }

    if (showCustomSheet) {
        CustomDurationSheet(
            initialMin = durationMin.takeUnless { it in Config.DURATION_PRESETS_MIN },
            onDismiss = { showCustomSheet = false },
            onConfirm = {
                onDurationSelected(it)
                showCustomSheet = false
            },
        )
    }
}

/** 黒の右上に群青がにじむ放射グラデーション（DESIGN.md の backgroundGlow）。中心は横 85%・縦 0%、広がりは横 130%・縦 55% */
private fun Modifier.moonlightBackground(): Modifier = drawBehind {
    drawRect(NodOffColors.background)
    val center = Offset(size.width * 0.85f, 0f)
    val radiusX = size.width * 1.3f
    val radiusY = size.height * 0.55f
    if (radiusX <= 0f || radiusY <= 0f) return@drawBehind
    // 円の放射グラデーションを縦に縮めて楕円にする
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to NodOffColors.backgroundGlow,
                0.55f to NodOffColors.backgroundGlowMid,
                1f to NodOffColors.background,
                center = center,
                radius = radiusX,
            ),
            radius = radiusX,
            center = center,
        )
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(R.drawable.ic_logo_mono),
            contentDescription = null,
            colorFilter = ColorFilter.tint(NodOffColors.accent),
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "nod-off",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.W600,
                letterSpacing = (-0.01).em,
                color = NodOffColors.textPrimary,
            ),
        )
    }
}

@Composable
private fun SetupSection(
    setup: SetupStatus,
    onGrantListenerAccess: () -> Unit,
    onGrantNotifications: () -> Unit,
    onAddTile: () -> Unit,
) {
    Text("準備　${setup.doneCount} / ${SetupStatus.TOTAL}", style = LabelStyle)
    Spacer(Modifier.height(10.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(NodOffColors.surface)
            .border(1.dp, NodOffColors.surfaceBorder, CardShape),
    ) {
        SetupRow("通知へのアクセス", done = setup.listenerAccess, actionLabel = "許可 ›", onClick = onGrantListenerAccess)
        HorizontalDivider(thickness = 1.dp, color = NodOffColors.divider)
        SetupRow("通知の表示", done = setup.notifications, actionLabel = "許可 ›", onClick = onGrantNotifications)
        HorizontalDivider(thickness = 1.dp, color = NodOffColors.divider)
        SetupRow("タイルを追加", done = setup.tileAdded, actionLabel = "追加 ›", onClick = onAddTile)
    }
}

@Composable
private fun SetupRow(label: String, done: Boolean, actionLabel: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (done) Modifier else Modifier.clickable(onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = BodyStyle, modifier = Modifier.weight(1f))
        if (done) {
            Text("✓", style = BodyStyle.copy(color = NodOffColors.textSecondary))
        } else {
            Text(actionLabel, style = BodyStyle.copy(color = NodOffColors.accent))
        }
    }
}

@Composable
private fun DurationSection(
    durationMin: Int,
    onPresetSelected: (Int) -> Unit,
    onCustomClicked: () -> Unit,
) {
    val isCustom = durationMin !in Config.DURATION_PRESETS_MIN
    // プリセット 5 つとカスタムを 3 列に並べる
    val options: List<Pair<String, Boolean>> =
        Config.DURATION_PRESETS_MIN.map { "$it" to (it == durationMin) } +
            ((if (isCustom) "$durationMin 分" else "カスタム") to isCustom)

    Text("既定の時間（分）", style = LabelStyle)
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { columnIndex, (label, selected) ->
                    val index = rowIndex * 3 + columnIndex
                    DurationChip(
                        label = label,
                        selected = selected,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (index < Config.DURATION_PRESETS_MIN.size) {
                                onPresetSelected(Config.DURATION_PRESETS_MIN[index])
                            } else {
                                onCustomClicked()
                            }
                        },
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Text(
        "タップしてから $durationMin 分後に、再生を一時停止します。",
        style = TextStyle(fontSize = 11.sp, color = NodOffColors.textTertiary),
    )
}

@Composable
private fun DurationChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(48.dp)
            .clip(ChipShape)
            .background(if (selected) NodOffColors.accent else NodOffColors.chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.W600 else FontWeight.Normal,
                color = if (selected) NodOffColors.onAccent else NodOffColors.textSecondary,
            ),
        )
    }
}

/** カスタムの入力。1〜180 分の外と空欄では「決定」を押せない */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomDurationSheet(initialMin: Int?, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember { mutableStateOf(initialMin?.toString() ?: "") }
    val value = text.toIntOrNull()
    val valid = value != null && value in Config.CUSTOM_DURATION_RANGE_MIN
    val focusRequester = remember { FocusRequester() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NodOffColors.sheet,
        contentColor = NodOffColors.textPrimary,
        scrimColor = NodOffColors.background.copy(alpha = 0.6f),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenPadding)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("既定の時間", style = LabelStyle, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = text,
                    // 数字だけ、3 桁まで
                    onValueChange = { new -> if (new.length <= 3 && new.all(Char::isDigit)) text = new },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = TextStyle(
                        fontSize = 40.sp,
                        fontWeight = FontWeight.W200,
                        fontFeatureSettings = "tnum",
                        textAlign = TextAlign.End,
                        color = NodOffColors.textPrimary,
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = NodOffColors.sheet,
                        unfocusedContainerColor = NodOffColors.sheet,
                        focusedIndicatorColor = NodOffColors.accent,
                        unfocusedIndicatorColor = NodOffColors.textTertiary,
                        cursorColor = NodOffColors.accent,
                    ),
                    modifier = Modifier
                        .width(140.dp)
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.width(8.dp))
                Text("分", style = BodyStyle)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${Config.CUSTOM_DURATION_RANGE_MIN.first}〜${Config.CUSTOM_DURATION_RANGE_MIN.last} 分",
                style = TextStyle(fontSize = 11.sp, color = NodOffColors.textTertiary),
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) {
                    Text("キャンセル", style = BodyStyle.copy(color = NodOffColors.textSecondary))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { value?.let(onConfirm) },
                    enabled = valid,
                    shape = ChipShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NodOffColors.accent,
                        contentColor = NodOffColors.onAccent,
                        disabledContainerColor = NodOffColors.chip,
                        disabledContentColor = NodOffColors.textTertiary,
                    ),
                ) {
                    Text("決定", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.W600))
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
