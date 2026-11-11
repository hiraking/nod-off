# nod-off

寝る前の「あと 30 分だけ聴く」のための、Android のスリープタイマー。

クイック設定のタイルを 1 回タップすると、既定の時間（初期値 30 分）でタイマーが始まります。時間になると、15 秒かけて音量を下げてから、再生中のメディアを**一時停止**し、音量を元に戻します。強制終了ではなく一時停止なので、再生アプリの視聴位置は残ります。

- 主な対象は Udemy Business アプリ（スリープタイマーがないため）。ほかのアプリの再生も一時停止します
- 対象端末は Galaxy S26（Android 16・API 36）だけ。自分専用で、Play ストアには出しません
- 見た目と文言の決まりは [DESIGN.md](DESIGN.md)、動きの決まりは [MVP 設計書](docs/superpowers/specs/2026-11-11-nod-off-mvp-design.md)

## 使い方

| 操作 | 動き |
|---|---|
| タイルをタップ | 既定の時間でタイマーを始めます。動いているとき（音量を下げている途中も）にタップすると、キャンセルします |
| タイルを長押し | 設定画面を開きます |
| 通知の「+10 分」 | 残り時間に 10 分足します |
| 通知の「キャンセル」 | タイマーを止めます。音量を下げている途中なら、すぐに元に戻します |

## ビルドと入れ方

Android Studio は使いません。リポジトリ直下で Gradle Wrapper を動かします（Windows では Git Bash から）。

```bash
# local.properties に sdk.dir を書いておく（git には入れない）
./gradlew testDebugUnitTest assembleDebug --console=plain
```

端末へは adb で直接入れます。adb は `$ANDROID_HOME/platform-tools/adb.exe` にあります（PATH にはありません）。

```bash
ADB="$ANDROID_HOME/platform-tools/adb.exe"
"$ADB" devices                                    # 端末が出ること（ワイヤレスデバッグでつなぐ）
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk
```

署名は debug の鍵です。

## 初回の準備

アプリ（ランチャーの nod-off）を開くと、「準備」の欄に 3 項目が出ます。右の「許可 ›」「追加 ›」を順に押してください。すべて済むと、準備の欄は消えます（あとで許可が外れたら、また出ます）。

1. **通知へのアクセス**：再生中のアプリに一時停止を送るのに使います。開いた一覧で nod-off をオンにします
2. **通知の表示**：タイマー中の残り時間と「+10 分」「キャンセル」の通知に使います
3. **タイルを追加**：クイックパネルに nod-off のタイルを足します

既定の時間は、同じ画面の「既定の時間（分）」で 15／20／30／45／60 分かカスタム（1〜180 分）から選びます。

## Udemy Business のバッテリーを「制限なし」にする

**手順**：設定 → アプリ → Udemy Business → バッテリー → **制限なし**

**理由**：バッテリーの設定が「制限なし」でないと、省電力モード中の Udemy Business は、裏で講義の終わりに達したときに次の講義へ進めず止まります。再生用のフォアグラウンドサービスの起動がシステムに拒否され、通信もブロックされるためです（2026-11-11 に Galaxy S26 で確認）。「制限なし」にすれば、省電力モードのまま画面オフでも次の講義へ進みます。

nod-off の動きとは関係ない Udemy Business 側の事情なので、アプリの「準備」には入れていません。

## 開発用（debug ビルドだけ）

何秒後に終わるタイマーを adb から始められます（秒数はフェードの 15 秒より長くします）。

```bash
"$ADB" shell am broadcast -a io.github.hiraking.nodoff.DEBUG_START \
  -n io.github.hiraking.nodoff/.TimerActionReceiver --ei seconds 45
```
