# nod-off MVP 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** クイック設定のタイルを 1 回タップすると既定の時間でタイマーが始まり、時間になったら音量をフェードアウトして再生中のメディアを一時停止し、音量を元に戻す Android アプリを作る。

**Architecture:** タイマーの状態遷移は純粋な Kotlin の状態機械（`TimerMachine`）にまとめ、JVM の単体テストで固める。Android 側は、状態機械が返す「副作用」を実行する薄い層にする。タイマー中は通常の通知（カウントダウン表示）と正確なアラーム 1 本だけで待つ。フェードの開始時刻にアラームが発火したら、短時間のフォアグラウンドサービス（`shortService`）が 15 秒のフェード、一時停止、音量の復帰を行う。音量の後始末は、システムが常駐させる `NotificationListenerService` が、画面ロックの解除と再生状態の変化を見張って行う。

**Tech Stack:** Kotlin 2.1.21、AGP 8.11.1、Gradle 8.14.3（Wrapper）、Jetpack Compose（BOM 2025.06.01、Material3）、activity-compose 1.10.1、JUnit 4.13.2。minSdk・targetSdk・compileSdk は 36。

**Spec:** `docs/superpowers/specs/2026-11-11-nod-off-mvp-design.md`、見た目と文言は `DESIGN.md`（当初の検証の結果は、設計書 §2 と git の履歴にある TODO.md を参照）

## Global Constraints

- 対象端末は Galaxy S26（Android 16・API 36）だけ。`minSdk = 36`、`targetSdk = 36`、`compileSdk = 36`。古い API 向けの分岐は書かない
- バージョン：AGP 8.11.1、Kotlin 2.1.21、Gradle 8.14.3、Compose BOM 2025.06.01、activity-compose 1.10.1、JUnit 4.13.2（`../actuator/apps/android` と同じ）
- パッケージ名・applicationId：`io.github.hiraking.nodoff`。アプリ名：`nod-off`
- 見た目・色・ロゴ・レイアウト・文言は **`DESIGN.md`** に従う（色の値、文言はそこから一字一句写す）。ダークテーマだけで、OS の設定がライトでも黒基調にする
- 画面・通知・タイルの文言は日本語。通知のボタンの文言は「+10 分」「キャンセル」で固定
- 既定の時間の選択肢：15／20／30／45／60 分とカスタム（1〜180 分）。初期値は 30 分
- フェードの長さ：15 秒（定数 `FADE_MILLIS = 15_000L`）。音量 0 まで下げる
- 「+10 分」は残り時間に 10 分足す（`EXTEND_MILLIS = 600_000L`）
- タイルのタップは ON／OFF の切り替え。止まっていれば既定の時間で始め、動いていれば（フェード中も）キャンセルする（2026-11-11、実機確認で変更。もとは「状態にかかわらず始め直す」）
- 停止は方式 A（再生中のすべてのセッションに `transportControls.pause()`）。止まらなければ方式 B（`KEYCODE_MEDIA_PAUSE`）。方式 C は作らない
- 一時停止を確かめられたときだけ、その場で音量を戻す。戻すべき音量は、フェードを始める前に永続化する
- 正確なアラームは `USE_EXACT_ALARM` で得る（Play ストアに出さないので可）
- 配布は adb での直接インストール。署名は debug の鍵でよい
- 開発 PC は Windows。Android Studio はない。Gradle は Bash から `./gradlew` で動かす。adb は `$ANDROID_HOME/platform-tools/adb.exe`（PATH にはない）。S26 はワイヤレスデバッグでつなぐ（手順は `../actuator/docs/devices/um790.md`）。ユーザー名に日本語が入る PC もあるので `gradle.properties` に `android.overridePathCheck=true` と `org.gradle.jvmargs=-Xmx2g -Dfile.encoding=COMPAT` を入れる
- `spike/` は使い捨て。コードを引き継がない（参考に読むのはよい）。コミットしない

## Review Focus

1. **フェードの途中でタイルをタップする・「+10 分」を押す・「キャンセル」を押す**：フェードを止めて音量をすぐ元に戻してから、それぞれの操作をする。音量が下がったまま次のタイマーに入ってはいけない → Task 2 の `TimerMachineTest` で固定する
2. **時間になっても何も再生されていない**：何もせずに終わり、音量は戻す → Task 2（`FadeEnded` のテスト）と Task 4（`MediaPauser` の戻り値）
3. **一時停止を確かめられない（相手が止まらない・通知へのアクセスがない）**：方式 B を試す。それでも再生中なら音量は戻さず、戻すべき音量を残す → Task 2（`RestorePolicyTest`）と Task 4
4. **フェードの途中でプロセスが落ちる・端末が再起動する**：戻すべき音量が保存に残り、次のロック解除か再生の停止で戻る → Task 2（`RestorePolicyTest`）と Task 7 の実機確認
5. **フェード前の音量が 0（消音で聴いていた）**：フェードの計算で 0 で割らず、戻す音量も 0 のまま → Task 2（`FadeCurveTest`）

---

## ファイル構成

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradlew, gradlew.bat, gradle/wrapper/*
local.properties                      （git に入れない。.gitignore 済み）
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/io/github/hiraking/nodoff/
  core/                               純粋な Kotlin。Android に依存しない
    Config.kt                         定数（FADE_MILLIS, EXTEND_MILLIS, DURATION_PRESETS_MIN, DEFAULT_DURATION_MIN）
    TimerMachine.kt                   状態・イベント・副作用・遷移
    FadeCurve.kt                      フェード中の音量の計算
    RestorePolicy.kt                  音量をいつ戻すか
  platform/                           Android の API を包む
    NodOffStore.kt                    SharedPreferences（状態・既定の時間・戻すべき音量）
    MediaPauser.kt                    方式 A／B と一時停止の確認
    VolumeControl.kt                  STREAM_MUSIC の取得・設定
    EffectRunner.kt                   TimerMachine の副作用を実行する
    TimerNotification.kt              通知チャンネルとカウントダウン通知
  TimerActionReceiver.kt              通知のボタン・フェード開始のアラームを受ける
  FadeService.kt                      shortService のフォアグラウンドサービス
  NodOffTileService.kt                クイック設定のタイル
  NodOffListenerService.kt            通知へのアクセス。音量の後始末の見張り
  SettingsActivity.kt                 設定画面と初回の準備の案内
app/src/debug/AndroidManifest.xml     debug だけの adb 用の入り口
app/src/test/java/io/github/hiraking/nodoff/core/
  TimerMachineTest.kt, FadeCurveTest.kt, RestorePolicyTest.kt
```

---

### Task 1: プロジェクトのひな形と定数

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `local.properties`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/io/github/hiraking/nodoff/core/Config.kt`
- Test: `app/src/test/java/io/github/hiraking/nodoff/core/ConfigTest.kt`

**Interfaces:**
- Produces:
  - `object Config { const val FADE_MILLIS = 15_000L; const val EXTEND_MILLIS = 600_000L; val DURATION_PRESETS_MIN = listOf(15, 20, 30, 45, 60); const val DEFAULT_DURATION_MIN = 30; val CUSTOM_DURATION_RANGE_MIN = 1..180 }`
  - Gradle のコマンド：`./gradlew testDebugUnitTest`、`./gradlew assembleDebug`

- [ ] **Step 1:** Gradle Wrapper（`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar`、`gradle-wrapper.properties`）を `../actuator/apps/android/` からコピーする。`gradle-wrapper.properties` の `distributionUrl` が `gradle-8.14.3-bin.zip` であることを確かめる。`local.properties` に `sdk.dir=<$ANDROID_HOME を / 区切りにしたもの>` を書く（`cygpath -m "$ANDROID_HOME"`）
- [ ] **Step 2:** ルートの `settings.gradle.kts`（`rootProject.name = "nod-off"`、`include(":app")`、リポジトリは google と mavenCentral）、`build.gradle.kts`（3 つのプラグインを `apply false`）、`gradle.properties`（Global Constraints の 2 行に `android.useAndroidX=true`、`kotlin.code.style=official` を加える）を書く。`app/build.gradle.kts` は namespace と applicationId を `io.github.hiraking.nodoff`、Java と JVM の target を 17、`buildFeatures.compose = true` にする。依存は Compose BOM・ui・material3・activity-compose と `testImplementation("junit:junit:4.13.2")`。マニフェストは空の `<application android:label="nod-off">` から始める
- [ ] **Step 3: 失敗するテストを書く** — `ConfigTest`

```kotlin
@Test fun defaultsMatchSpec() {
    assertEquals(15_000L, Config.FADE_MILLIS)
    assertEquals(600_000L, Config.EXTEND_MILLIS)
    assertEquals(listOf(15, 20, 30, 45, 60), Config.DURATION_PRESETS_MIN)
    assertEquals(30, Config.DEFAULT_DURATION_MIN)
    assertEquals(1..180, Config.CUSTOM_DURATION_RANGE_MIN)
}
```

- [ ] **Step 4:** `./gradlew testDebugUnitTest --console=plain` を流す。期待：`Config` が未定義でコンパイルに失敗する
- [ ] **Step 5:** `Config.kt` を書く
- [ ] **Step 6:** `./gradlew testDebugUnitTest assembleDebug --console=plain` を流す。期待：`BUILD SUCCESSFUL`、`app/build/outputs/apk/debug/app-debug.apk` ができる
- [ ] **Step 7: コミット** — `git add` でひな形一式（`local.properties` は除く）を足し、`chore: Android プロジェクトのひな形を作る`

---

### Task 2: タイマーの状態機械・フェードの計算・音量を戻す判断

**Files:**
- Create: `app/src/main/java/io/github/hiraking/nodoff/core/TimerMachine.kt`, `FadeCurve.kt`, `RestorePolicy.kt`
- Test: `app/src/test/java/io/github/hiraking/nodoff/core/TimerMachineTest.kt`, `FadeCurveTest.kt`, `RestorePolicyTest.kt`

**Interfaces:**
- Consumes: `Config`
- Produces（時刻はすべて `SystemClock.elapsedRealtime()` のミリ秒）:

```kotlin
sealed interface TimerState {
    data object Idle : TimerState
    data class Running(val endAt: Long) : TimerState
    data class Fading(val endAt: Long) : TimerState
}

sealed interface TimerEvent {
    data class TileTapped(val now: Long, val durationMin: Int) : TimerEvent
    data object ExtendPressed : TimerEvent
    data object CancelPressed : TimerEvent
    data object FadeAlarmFired : TimerEvent
    data object FadeEnded : TimerEvent   // フェードと一時停止の処理が終わった
}

sealed interface Effect {
    data class ScheduleFadeAlarm(val at: Long) : Effect   // at = endAt - FADE_MILLIS
    data object CancelFadeAlarm : Effect
    data class ShowCountdown(val endAt: Long) : Effect
    data object ClearCountdown : Effect
    data object StartFade : Effect        // FadeService を起動する
    data object StopFade : Effect         // FadeService を止める
    data object RestoreVolumeNow : Effect // 保存した音量があれば戻し、保存を消す
    data object RefreshTile : Effect
}

data class Transition(val state: TimerState, val effects: List<Effect>)

object TimerMachine { fun reduce(state: TimerState, event: TimerEvent): Transition }

object FadeCurve { fun volumeAt(original: Int, elapsedMs: Long, fadeMs: Long = Config.FADE_MILLIS): Int }

object RestorePolicy {
    enum class Trigger { PAUSE_CONFIRMED, NO_MEDIA_PLAYING, SCREEN_UNLOCKED, APP_USED, STILL_PLAYING_AFTER_PAUSE }
    fun shouldRestore(pendingVolume: Int?, trigger: Trigger): Boolean
}
```

遷移の決まり（テストで固定する）：

| 状態 | イベント | 次の状態 | 副作用（この順） |
|---|---|---|---|
| どれでも | `TileTapped(now, d)` | `Running(now + d*60_000)` | Fading のときだけ先に `StopFade, RestoreVolumeNow`。続けて `CancelFadeAlarm, ScheduleFadeAlarm(endAt - FADE), ShowCountdown(endAt), RefreshTile` |
| `Running(e)` | `ExtendPressed` | `Running(e + EXTEND)` | `CancelFadeAlarm, ScheduleFadeAlarm(e + EXTEND - FADE), ShowCountdown(e + EXTEND), RefreshTile` |
| `Fading(e)` | `ExtendPressed` | `Running(e + EXTEND)` | `StopFade, RestoreVolumeNow` のあとに上と同じ |
| `Running` | `CancelPressed` | `Idle` | `CancelFadeAlarm, ClearCountdown, RefreshTile` |
| `Fading` | `CancelPressed` | `Idle` | `StopFade, RestoreVolumeNow, CancelFadeAlarm, ClearCountdown, RefreshTile` |
| `Running(e)` | `FadeAlarmFired` | `Fading(e)` | `StartFade` |
| `Fading` | `FadeEnded` | `Idle` | `ClearCountdown, RefreshTile`（音量を戻すかは FadeService が `RestorePolicy` で決める） |
| `Idle` | `ExtendPressed`／`CancelPressed`／`FadeAlarmFired`／`FadeEnded` | `Idle` | `ClearCountdown, RefreshTile`（古い通知やアラームの残りを片づけるだけ） |

- [ ] **Step 1: 失敗するテストを書く** — `TimerMachineTest`。上の表の各行を 1 テストにする。少なくとも次を含む：

```kotlin
@Test fun tapWhileIdleStartsDefaultDuration() {
    val t = TimerMachine.reduce(TimerState.Idle, TimerEvent.TileTapped(now = 1_000L, durationMin = 30))
    assertEquals(TimerState.Running(1_000L + 1_800_000L), t.state)
    assertEquals(listOf(Effect.CancelFadeAlarm, Effect.ScheduleFadeAlarm(1_801_000L - 15_000L),
        Effect.ShowCountdown(1_801_000L), Effect.RefreshTile), t.effects)
}
@Test fun tapWhileRunningRestartsFromNowNotAdds() {
    val t = TimerMachine.reduce(TimerState.Running(endAt = 5_000_000L), TimerEvent.TileTapped(now = 2_000_000L, durationMin = 30))
    assertEquals(TimerState.Running(3_800_000L), t.state)
}
@Test fun tapWhileFadingRestoresVolumeFirst() {
    val t = TimerMachine.reduce(TimerState.Fading(endAt = 100_000L), TimerEvent.TileTapped(now = 90_000L, durationMin = 30))
    assertEquals(listOf(Effect.StopFade, Effect.RestoreVolumeNow), t.effects.take(2))
    assertTrue(t.state is TimerState.Running)
}
@Test fun extendWhileFadingRestoresVolumeAndAddsTenMinutes() {
    val t = TimerMachine.reduce(TimerState.Fading(endAt = 100_000L), TimerEvent.ExtendPressed)
    assertEquals(TimerState.Running(700_000L), t.state)
    assertEquals(listOf(Effect.StopFade, Effect.RestoreVolumeNow), t.effects.take(2))
}
@Test fun cancelWhileFadingRestoresVolume() { /* effects に StopFade と RestoreVolumeNow を含み、state は Idle */ }
@Test fun fadeEndedGoesIdleAndClearsNotification() { /* Fading → Idle、effects == [ClearCountdown, RefreshTile] */ }
@Test fun staleEventsWhileIdleOnlyCleanUp() { /* 4 つのイベントそれぞれで Idle のまま、effects == [ClearCountdown, RefreshTile] */ }
```

- [ ] **Step 2: 失敗するテストを書く** — `FadeCurveTest`

```kotlin
@Test fun startsAtOriginal() = assertEquals(10, FadeCurve.volumeAt(10, 0L))
@Test fun halfwayIsHalfRoundedUp() = assertEquals(5, FadeCurve.volumeAt(10, 7_500L))
@Test fun reachesZeroAtEnd() = assertEquals(0, FadeCurve.volumeAt(10, 15_000L))
@Test fun neverNegativeAfterEnd() = assertEquals(0, FadeCurve.volumeAt(10, 20_000L))
@Test fun zeroOriginalStaysZero() = assertEquals(0, FadeCurve.volumeAt(0, 3_000L))
@Test fun nonIncreasing() { /* 0..15_000 を 100ms 刻みで見て、前の値以下であること */ }
```

- [ ] **Step 3: 失敗するテストを書く** — `RestorePolicyTest`

```kotlin
@Test fun nothingPendingNeverRestores() { /* pendingVolume = null なら、どの Trigger でも false */ }
@Test fun stillPlayingDoesNotRestore() = assertFalse(RestorePolicy.shouldRestore(8, Trigger.STILL_PLAYING_AFTER_PAUSE))
@Test fun restoresOnPauseConfirmed() = assertTrue(RestorePolicy.shouldRestore(8, Trigger.PAUSE_CONFIRMED))
@Test fun restoresWhenNoMediaPlaying() = assertTrue(RestorePolicy.shouldRestore(8, Trigger.NO_MEDIA_PLAYING))
@Test fun restoresOnUnlock() = assertTrue(RestorePolicy.shouldRestore(8, Trigger.SCREEN_UNLOCKED))
@Test fun restoresWhenAppUsed() = assertTrue(RestorePolicy.shouldRestore(8, Trigger.APP_USED))
@Test fun pendingZeroStillCountsAsPending() = assertTrue(RestorePolicy.shouldRestore(0, Trigger.SCREEN_UNLOCKED))
```

- [ ] **Step 4:** `./gradlew testDebugUnitTest --console=plain` を流す。期待：3 つのクラスが未定義でコンパイルに失敗する
- [ ] **Step 5:** `TimerMachine.kt`、`FadeCurve.kt`、`RestorePolicy.kt` を書く。`FadeCurve` は `ceil(original * (1 - elapsed/fade))` を `0..original` に収める（`original == 0` なら 0）
- [ ] **Step 6:** `./gradlew testDebugUnitTest --console=plain` を流す。期待：すべて PASS
- [ ] **Step 7: コミット** — `feat: タイマーの状態機械とフェード・音量復帰の判断を追加`

---

### Task 3: 保存・音量・一時停止（Android の API を包む）

**Files:**
- Create: `app/src/main/java/io/github/hiraking/nodoff/platform/NodOffStore.kt`, `VolumeControl.kt`, `MediaPauser.kt`
- Create: `app/src/main/java/io/github/hiraking/nodoff/NodOffListenerService.kt`（この Task では中身なし。`MediaPauser` が `ComponentName` に使う）
- Modify: `app/src/main/AndroidManifest.xml`（リスナーサービスの宣言）

**Interfaces:**
- Consumes: `TimerState`、`Config`
- Produces:

```kotlin
class NodOffStore(context: Context) {
    var state: TimerState            // Idle／Running／Fading と endAt を保存する
    var defaultDurationMin: Int      // 既定 Config.DEFAULT_DURATION_MIN。プリセット以外の値ならカスタム
    var tileAdded: Boolean           // 設定画面からタイルの追加に成功したら true（アプリからは追加済みかを調べられないため）
    var pendingRestoreVolume: Int?   // 戻すべき音量。なければ null
}
class VolumeControl(context: Context) { fun current(): Int; fun set(volume: Int) }   // STREAM_MUSIC、flags = 0
class MediaPauser(context: Context) {
    fun hasListenerAccess(): Boolean
    fun isAnythingPlaying(): Boolean
    /** 再生中（PLAYING・BUFFERING）のセッションすべてに pause()。最大 3 秒、250ms ごとに確かめ、
     *  止まらなければ KEYCODE_MEDIA_PAUSE を送ってもう一度 3 秒確かめる。
     *  通知へのアクセスがなければ最初から方式 B を送り、AudioManager.isMusicActive で確かめる。 */
    suspend fun pauseAll(): PauseResult
}
enum class PauseResult { NOTHING_PLAYING, PAUSED, STILL_PLAYING }
```

- 保存は `SharedPreferences`（名前 `nodoff`）。`commit()` で同期的に書く（フェード直前の音量はプロセスが落ちる前に確実に残すため）
- マニフェスト：`NodOffListenerService` を `BIND_NOTIFICATION_LISTENER_SERVICE` で宣言（`spike/media-pause/app/src/main/AndroidManifest.xml` と同じ形）
- `kotlinx-coroutines-android` を依存に足す（`suspend` と `delay` のため。バージョンは Kotlin 2.1.21 と組む `1.10.2`）

- [ ] **Step 1:** 3 つのクラスとリスナーサービスの空の宣言を書く
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug --console=plain` を流す。期待：`BUILD SUCCESSFUL`（このタスクの確認は Task 4 の実機確認でまとめて行う。ここは Android の API を包むだけで、JVM で意味のあるテストが書けないため）
- [ ] **Step 3: コミット** — `feat: 保存・音量・一時停止の部品を追加`

---

### Task 4: タイマーの本体（通知・アラーム・フェード）

**Files:**
- Create: `app/src/main/java/io/github/hiraking/nodoff/platform/EffectRunner.kt`, `TimerNotification.kt`
- Create: `app/src/main/java/io/github/hiraking/nodoff/TimerActionReceiver.kt`, `FadeService.kt`
- Create: `app/src/debug/AndroidManifest.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: Task 2 と Task 3 のすべて
- Produces:

```kotlin
/** イベントを状態機械に通し、新しい状態を保存してから、副作用を実行する。唯一の入口。 */
object TimerController {
    fun dispatch(context: Context, event: TimerEvent)
    /** 保存された音量を RestorePolicy.shouldRestore に従って戻し、戻したら保存を消す */
    fun dispatchRestore(context: Context, trigger: RestorePolicy.Trigger)
    fun debugStart(context: Context, seconds: Int)
}
class EffectRunner(context: Context) { fun run(effects: List<Effect>) }
object TimerNotification { const val CHANNEL_ID = "timer"; const val NOTIFICATION_ID = 1
    fun show(context: Context, endAt: Long); fun clear(context: Context) }
class TimerActionReceiver : BroadcastReceiver() { companion object {
    const val ACTION_EXTEND = "io.github.hiraking.nodoff.EXTEND"
    const val ACTION_CANCEL = "io.github.hiraking.nodoff.CANCEL"
    const val ACTION_FADE_ALARM = "io.github.hiraking.nodoff.FADE_ALARM"
    const val ACTION_DEBUG_START = "io.github.hiraking.nodoff.DEBUG_START" } }  // extra "seconds": Int
class FadeService : Service()
```

決まり：

- `TimerController` は `TimerController.kt`（パッケージ直下）に置く
- **アラーム**：`AlarmManager.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, at, pi)`。`pi` は `ACTION_FADE_ALARM` を `TimerActionReceiver` に送る `PendingIntent`（`FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`、requestCode 0）。`CancelFadeAlarm` は同じ `pi` を `cancel` する
- **通知**：チャンネル `timer`、重要度 LOW（音・振動なし）、名前「タイマー」。`setOngoing(true)`、`setUsesChronometer(true)`、`setChronometerCountDown(true)`、`setWhen(System.currentTimeMillis() + (endAt - elapsedRealtime()))`。タイトル「nod-off」、本文「H:mm に停止」（24 時間表記。例「0:42 に停止」）。ボタンは「+10 分」（`ACTION_EXTEND`）と「キャンセル」（`ACTION_CANCEL`）。小さいアイコンは `R.drawable.ic_logo_mono`、`setColor(0xFFB9C3FF)`（DESIGN.md「通知」）
- **ロゴの単色ベクター** `app/src/main/res/drawable/ic_logo_mono.xml`：`docs/design/logo.svg` を VectorDrawable にしたもの（白で塗る。三日月はマスクを使わず、2 つの円弧でできた 1 本のパスにする）。通知（この Task）とタイル（Task 5）で使う
- **FadeService**：`foregroundServiceType="shortService"`。権限は `FOREGROUND_SERVICE`。起動したら通知を出してフォアグラウンドにし、コルーチンで次を順に行う
  1. `pendingRestoreVolume` が null なら、`VolumeControl.current()` を保存する
  2. 15 秒間、200ms ごとに `FadeCurve.volumeAt(original, elapsed)` を `VolumeControl.set` に渡す
  3. `MediaPauser.pauseAll()` の結果を `RestorePolicy.Trigger` に読み替える（`PAUSED`→`PAUSE_CONFIRMED`、`NOTHING_PLAYING`→`NO_MEDIA_PLAYING`、`STILL_PLAYING`→`STILL_PLAYING_AFTER_PAUSE`）。`shouldRestore` が true なら音量を戻し、保存を消す
  4. `TimerController.dispatch(FadeEnded)` を呼んで `stopSelf()`
  - `StopFade`（`stopService`）で止められたら、コルーチンを取り消す。音量は `RestoreVolumeNow` が戻す
  - `stopService` は非同期なので、`RestoreVolumeNow` のあとにフェードのループが音量を下げてしまう競合がありうる。防ぐため、ループは音量を設定する前に毎回 `NodOffStore.state` が `Fading` かを確かめ、違えば抜ける（`TimerController` は副作用より先に新しい状態を保存するので、これで止まる）
  - `onTimeout(startId, fgsType)`（shortService の時間切れ）でも同じく取り消して `stopSelf()`
- **RestoreVolumeNow**：`pendingRestoreVolume` があれば `VolumeControl.set` してから null にする
- **debug だけの入口**：`app/src/debug/AndroidManifest.xml` で `TimerActionReceiver` を `exported="true"` にし、`ACTION_DEBUG_START` の intent-filter を足す。受けたら `TimerController.debugStart(context, seconds)` を呼ぶ。`debugStart` は、`endAt = now + seconds*1000` になるように `now` をずらした `TileTapped(now + seconds*1000L - 30*60_000L, 30)` を `dispatch` する（状態機械に debug 用の分岐を足さない）。`seconds` は `FADE_MILLIS` より長くする（例：30、45）
- 通知の表示には `POST_NOTIFICATIONS` が要る。この Task では adb で `pm grant io.github.hiraking.nodoff android.permission.POST_NOTIFICATIONS` して確かめる（画面での案内は Task 6）

- [ ] **Step 1:** 上の決まりのとおりに書く
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug --console=plain`。期待：`BUILD SUCCESSFUL`
- [ ] **Step 3: 実機に入れる** — S26 をつなぎ（`adb devices` に出ること）、`adb install -r app/build/outputs/apk/debug/app-debug.apk`、`adb shell pm grant io.github.hiraking.nodoff android.permission.POST_NOTIFICATIONS`、`adb shell cmd notification allow_listener io.github.hiraking.nodoff/io.github.hiraking.nodoff.NodOffListenerService`
- [ ] **Step 4: 実機で確かめる（利用者に操作を頼む）** — Udemy Business（`com.udemy.android.ufb`）で再生し、画面オフにしてから `adb shell am broadcast -a io.github.hiraking.nodoff.DEBUG_START -n io.github.hiraking.nodoff/.TimerActionReceiver --ei seconds 45`。期待：
  - 通知にカウントダウンが出る
  - 30 秒後から 15 秒かけて音が小さくなる
  - 45 秒で `adb shell dumpsys media_session` の Udemy が `PAUSED`
  - `adb shell dumpsys audio | grep -A3 STREAM_MUSIC` の音量が元に戻っている
  - 通知が消える
  - `adb logcat -d | grep -i "FGS\|Disallowed\|nodoff"` に `ForegroundServiceStartNotAllowedException` がない
- [ ] **Step 5: 実機で確かめる** — 同じ準備で、フェードの途中に通知の「キャンセル」を押す → 音量がすぐ戻り、再生は続く。「+10 分」を押す → 音量がすぐ戻り、通知の残りが約 10 分になる
- [ ] **Step 6:** 期待どおりでなければ、原因を調べて直す（`superpowers:systematic-debugging`）。とくに `shortService` がアラームから起動できない場合は、`setExactAndAllowWhileIdle` を `setAlarmClock` に替えて試す（アラームのアイコンが出るのは許容する）。替えたらこの計画の Task 4 に結果を書き足す
- [ ] **Step 7: コミット** — `feat: タイマーの通知・アラーム・フェードと一時停止を実装`

---

### Task 5: クイック設定のタイル

**Files:**
- Create: `app/src/main/java/io/github/hiraking/nodoff/NodOffTileService.kt`
- Create: アプリのアイコン（アダプティブアイコン）`app/src/main/res/mipmap-anydpi/ic_launcher.xml`、`drawable/ic_launcher_foreground.xml`、`drawable/ic_launcher_background.xml`（DESIGN.md「ロゴ」の放射グラデーション）、単色は `ic_logo_mono` を使う
- Modify: `app/src/main/AndroidManifest.xml`（`android:icon`）、`platform/EffectRunner.kt`（`RefreshTile` の実装）

**Interfaces:**
- Consumes: `TimerController.dispatch`、`NodOffStore`
- Produces: `class NodOffTileService : TileService()`

決まり：

- `onClick()`：`TimerController.dispatch(TileTapped(elapsedRealtime(), store.defaultDurationMin))`
- `onStartListening()`（クイックパネルが開かれたとき）：`TimerController.dispatchRestore(APP_USED)` を呼んでから、タイルを描く
- 表示（DESIGN.md「タイル」）：アイコンは `ic_logo_mono`、ラベル「nod-off」。Idle は `STATE_INACTIVE`、サブタイトル「30 分」（既定の時間）。Running・Fading は `STATE_ACTIVE`、サブタイトル「残り N 分」（N は残りを分に切り上げ）
- `RefreshTile`：`TileService.requestListeningState(context, ComponentName(context, NodOffTileService::class.java))`
- 長押しで設定画面：`SettingsActivity` に `android.service.quicksettings.action.QS_TILE_PREFERENCES` の intent-filter を付ける（Task 6 で `SettingsActivity` を作るので、この Task ではマニフェストに書かない）
- マニフェスト：`BIND_QUICK_SETTINGS_TILE` 権限、`android.service.quicksettings.action.QS_TILE` の intent-filter、`icon`、`label`

- [ ] **Step 1:** 書く
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug --console=plain`。期待：`BUILD SUCCESSFUL`
- [ ] **Step 3: 実機で確かめる（利用者に操作を頼む）** — 入れ直し、ホーム画面のアイコンが DESIGN.md のロゴになっていることを見てもらう。クイックパネルの編集から nod-off を追加してもらう。タップ → 通知に約 30 分のカウントダウン、タイルが ON で「残り 30 分」。もう一度タップ → 残りが 30 分に戻る（足されない）。通知の「キャンセル」→ タイルが OFF に戻る
- [ ] **Step 4: コミット** — `feat: クイック設定のタイルを追加`

---

### Task 6: 設定画面・初回の準備・音量の後始末の見張り

**Files:**
- Create: `app/src/main/java/io/github/hiraking/nodoff/SettingsActivity.kt`、`app/src/main/java/io/github/hiraking/nodoff/ui/Theme.kt`（DESIGN.md の色のトークン）、`README.md`
- Modify: `app/src/main/java/io/github/hiraking/nodoff/NodOffListenerService.kt`、`app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `NodOffStore`、`MediaPauser`、`TimerController.dispatchRestore`（Task 4）
- Produces: なし（画面と見張りだけ）

決まり：

- **SettingsActivity**（Compose、Material3 の `darkColorScheme` を DESIGN.md の色で上書きし、OS の設定にかかわらず常にこれを使う）。レイアウト・文言は DESIGN.md「画面：設定」のとおり。ランチャーから開け、タイルの長押し（`QS_TILE_PREFERENCES`）でも開く。`onResume` で `dispatchRestore(APP_USED)` し、準備の状態を読み直す
  - 「準備」：3 項目。**すべて済んだら欄ごと出さない**
    1. 通知へのアクセス：状態は `MediaPauser.hasListenerAccess()`。「許可 ›」で `Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`
    2. 通知の表示：状態は `POST_NOTIFICATIONS` の許可。「許可 ›」で実行時の許可を求める
    3. タイルを追加：状態は `NodOffStore.tileAdded`。「追加 ›」で `StatusBarManager.requestAddTileService(...)`。結果が `TILE_ADD_REQUEST_RESULT_TILE_ADDED` か `TILE_ALREADY_ADDED` なら `tileAdded = true`。タイルの `onTileAdded()` でも `tileAdded = true` にする（パネルの編集から足した場合）
  - 「既定の時間（分）」：3 列のグリッド。選ぶと `NodOffStore.defaultDurationMin` に保存。`カスタム` はボトムシートで 1〜180 を入力（`Config.CUSTOM_DURATION_RANGE_MIN` の外と空欄では「決定」を押せない）。補足の一文「タップしてから N 分後に、再生を一時停止します。」
- **README.md**（リポジトリ直下）を作る：何のアプリか、ビルドと adb での入れ方、初回の準備、そして**「Udemy Business のバッテリーを『制限なし』にする」手順と理由**（設定 → アプリ → Udemy Business → バッテリー → 制限なし。しないと、省電力モード中は講義の終わりで止まる）
- **NodOffListenerService**：`onListenerConnected` で
  - `ACTION_USER_PRESENT` の受信を動的に登録し、受けたら `dispatchRestore(SCREEN_UNLOCKED)`
  - `MediaSessionManager.addOnActiveSessionsChangedListener` を登録し、各セッションに `MediaController.Callback` を付ける。再生状態が変わったら、`MediaPauser.isAnythingPlaying()` が false のときだけ `dispatchRestore(NO_MEDIA_PLAYING)`
  - `onListenerConnected` のときにも、何も再生されていなければ `dispatchRestore(NO_MEDIA_PLAYING)`（再起動のあとの後始末）
  - `onListenerDisconnected` で登録を外す

- [ ] **Step 1:** 書く
- [ ] **Step 2:** `./gradlew testDebugUnitTest assembleDebug --console=plain`。期待：`BUILD SUCCESSFUL`
- [ ] **Step 3: 実機で確かめる（利用者に操作を頼む）** — いったん `adb uninstall io.github.hiraking.nodoff` してから入れ直し、設定画面の案内だけで 3 項目を済ませられること。済んだら準備の欄が消えること。「制限付き設定」で通知へのアクセスが阻まれないこと。見た目が DESIGN.md のモックの雰囲気と合っているかを見てもらう（OS をライトモードにしても黒基調のままか）。カスタムで 40 を入れるとタイルのサブタイトルが「40 分」になること
- [ ] **Step 4: 実機で確かめる（音量の後始末）** — 準備として、`adb shell am broadcast ... DEBUG_START --ei seconds 30` のあと、フェードの途中で `adb shell am force-stop io.github.hiraking.nodoff` を流してプロセスを落とす（音量が下がったまま残る）。そのあと Udemy を一時停止する、または画面をオフにしてからロックを解除する。期待：音量が元に戻る
- [ ] **Step 5: コミット** — `feat: 設定画面と初回の準備、音量の後始末を追加`

---

### Task 7: 実機での受け入れ確認（設計書 §7）

**Files:**
- Modify: 設計書 §7（確認の結果を書く。TODO.md は 2026-11-12 に削除した）
- Modify: `docs/superpowers/specs/2026-11-11-nod-off-mvp-design.md`（フェードの長さを変えたら §4.3 を直す）

- [ ] **Step 1: 完了の定義** — 利用者が実際に、Udemy Business で再生 → タイルを 1 回タップ → 画面を消して寝る。翌朝、次を確かめる：再生が一時停止で止まっていること、音量が戻っていること、続きから再生できること。ログは `adb logcat -d | grep -i nodoff` と `adb shell dumpsys media_session` で補う
- [ ] **Step 2: 再起動後の視聴位置** — 一時停止のあと端末を再起動し、Udemy Business で続きから再生できるか
- [ ] **Step 3: 方式 B の画面オフ** — 通知へのアクセスを一時的に外し、`DEBUG_START --ei seconds 45` を画面オフで流す。期待：Udemy Business が `PAUSED`。確かめたら通知へのアクセスを戻す
- [ ] **Step 4: フェードの体感** — 利用者に 15 秒の長さと滑らかさを聞く。変えるなら `Config.FADE_MILLIS` と `ConfigTest`・`FadeCurveTest` の値、設計書 §4.3 を一緒に直す
- [ ] **Step 5:** 結果を設計書 §7 に書く
- [ ] **Step 6: コミット** — `docs: MVP の実機確認の結果を記録`
