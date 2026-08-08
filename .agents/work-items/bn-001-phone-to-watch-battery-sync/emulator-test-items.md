# BN-001 エミュレータ試験項目書

文書ID: BN-001-ETI-001

版: 1.0

作成日: 2026-07-27

対象工程: Test

対象work item: BN-001 Phone battery state and threshold event synchronization to Wear OS

最新の実行結果の正本: `.agents/work-items/bn-001-phone-to-watch-battery-sync/state.yaml`

## 1. 目的

`docs/test-plan-and-cases.md`の`TC-E001`から`TC-E076`のうち、Android/Wear OSエミュレータで実行する試験を、Testロールがそのまま実行・記録できる粒度へ具体化する。

特に`TC-E033`は、TST-003修正後のWearアプリNo Data表示と、システムから`MainComplicationService`へ要求されたときの`NoDataComplicationData`を別々に判定する。静的なManifestまたはソース確認だけで、システムComplication試験をPassにしない。

## 2. 適用範囲

### 2.1 対象

- Mobile単体AVD上の通知権限、FGS、再起動、更新、画面適応
- Wear単体AVD上のNo Data、Freshness、Tile、Complication、通知、再起動
- ペア接続したMobile/Wear AVD間のData Layer同期、切断、再接続、通知
- debug buildへ限定した、レビュー済みテストハーネスによる順序、競合、異常payload、通知投稿失敗
- API 33～36およびWear OSの対応範囲に対する代表回帰

### 2.2 対象外

次はHumanフェーズの実機ゲートであり、本書のエミュレータ結果だけでは合格にできない。

- Pixel 10 Pro Fold実機の折りたたみ、展開、分割画面、FGS、再起動
- Pixel Watch 4 41mm/45mm実機のレイアウト、Tile、Complication、通知
- Bluetooth、Wi-Fiまたはクラウド経路を使った実機間同期
- 24時間のバッテリー消費
- 通知・同期遅延30回の測定、音、振動、実機ウォッチフェイスの視認性
- Google Play ConsoleのFGS申告と配布承認

## 3. 参照仕様

| 分類 | 参照 |
|---|---|
| 基本要件 | `docs/product-requirements.md`, `docs/functional-requirements.md` |
| データ契約 | `docs/data-design.md`, `docs/wear-os-integration-specification.md` |
| 通知・復旧 | `docs/notification-background-monitoring-specification.md`, `docs/notification-bridge-specification.md` |
| Wear表示 | `docs/wear-os-display-specification.md`, `docs/complication-specification.md` |
| 権限・互換性 | `docs/permissions-and-privacy-specification.md`, `docs/compatibility-matrix.md` |
| 元テストケース | `docs/test-plan-and-cases.md` |

## 4. 判定ルール

| 判定 | 条件 |
|---|---|
| Pass | 識別済みbuildで全手順を実行し、期待結果と必要証跡を満たした |
| Fail | 実行環境が正常な状態で、再現可能な製品またはテストコード不具合を確認した |
| Blocked | AVD、ペアリング、system image、システムUI、必要なレビュー済みハーネスなどの環境条件が不足した |
| Not Run | まだ実行を開始していない、または対象環境を準備していない |

- BlockedまたはNot RunをPassへ読み替えない。
- 製品Failはfinding IDを付け、Reviewで所有工程を判定する。
- 環境失敗は製品Failとして数えず、実際のエラーと再開条件を記録する。
- 同じケースのアプリ画面、Tile、Complication、通知は、実行していない表示面をまとめてPassにしない。
- exact boundaryはJVMテスト、代表時間のシステム描画は本書のエミュレータ試験で相互補完する。

## 5. テスト環境

### 5.1 基準環境

| ID | Mobile | Wear | 用途 |
|---|---|---|---|
| EMU-36-PAIR | Android 16 / API 36 Google APIs AVD | `Wear_OS_6_1_API_36`, Android 16 / API 36, 454x454, 320 dpi | 全機能の基準、TC-E033、ペア同期 |
| EMU-33-MIN | Android 13 / API 33 AVD | API 30以上のWear AVD | minSdk、通知runtime permission |
| EMU-34 | Android 14 / API 34 AVD | Wear OS 4/5 AVD | FGS type、権限、Data Layer |
| EMU-35 | Android 15 / API 35 AVD | Wear OS 5 AVD | boot/FGS回帰 |
| EMU-FOLD-36 | API 36 foldable AVD | 任意 | Window Size Class、fold、multi-window |
| EMU-WEAR-SIZES | Wear 41mm相当および45mm相当AVD | - | 円形画面、フォント、Tile、Complication |

現在確認済みのWear基準AVDは`Wear_OS_6_1_API_36`である。ほかのsystem imageまたはAVDが存在しない場合、その環境のケースはBlockedまたはNot Runとして記録する。

### 5.2 build識別

各実行前に次を記録する。

- Git commit SHAおよび未コミット差分の有無
- Mobile/Wearの`applicationId`
- `versionCode`、`versionName`、`minSdk`、`targetSdk`
- debugまたはrelease、および署名種別
- AVD名、OS/API、system image、画面サイズ、密度
- Mobile/Wearの接続経路、Locale、通知権限、channel状態

Mobile/Wearは同じ`com.magicitengineer.batterynotifier`、同じdebug署名のbuildを組み合わせる。debug/releaseを混在させない。

## 6. 共通準備

### 6.1 build

Mobile:

```powershell
cd BatteryNotifierAndroidMobileApp
.\gradlew.bat assembleDebug assembleDebugAndroidTest --no-daemon --offline --max-workers=1 --console=plain '-Pkotlin.compiler.execution.strategy=in-process'
```

Wear:

```powershell
cd BatteryNotifierAndroidWearApp
.\gradlew.bat assembleDebug assembleDebugAndroidTest --no-daemon --offline --max-workers=1 --console=plain '-Pkotlin.compiler.execution.strategy=in-process'
```

### 6.2 共通確認

1. 対象以外のADB端末を停止するか、すべてのコマンドで対象serialを明示する。
2. Mobile/Wearのdebug APKを対応AVDへインストールする。
3. ペア試験ではAndroid StudioのWear OS emulator pairingを完了し、Data Layerで相互に到達可能であることを確認する。
4. ケースが初期状態を要求する場合だけ、対象アプリのデータを消去する。
5. ケース開始前に`logcat`をクリアし、終了後にFATAL、ANR、ForegroundServiceStartNotAllowed、DataStore例外を確認する。
6. システム時計を変更するケースはAVD snapshotを取得し、終了後に自動時刻へ戻す。
7. 証跡へADB serial、Android ID、node表示名などの個人・端末識別子を残さない。

## 7. 推奨実行順

1. PRE-001 build・identity・署名確認
2. `TC-E033` No DataとシステムComplication
3. `TC-E030`～`TC-E034` Freshnessと時刻
4. `TC-E001`～`TC-E004` 通常同期
5. `TC-E010`～`TC-E014` 切断・再接続
6. `TC-E060`～`TC-E069` 権限と通知
7. `TC-E020`～`TC-E025` 再起動・プロセス・更新
8. `TC-E040`～`TC-E043` 連続更新・競合
9. `TC-E050`～`TC-E056` 異常payload
10. `TC-E070`～`TC-E076` UI・Locale・互換性
11. API 33～35およびWear最小系の代表回帰

## 8. 詳細試験項目

### 8.1 PRE-001 build・install・identity

| 項目 | 内容 |
|---|---|
| 前提 | Mobile/Wear基準AVDがboot完了し、ADBでonline |
| 手順 | 両projectをbuildし、APKをinstall。package、version、targetSdk、署名証明書系列を取得する |
| 期待結果 | build/install成功。application IDは共通、debug署名は同一、Mobileは偶数・Wearは奇数versionCode |
| 証跡 | build結果、package dumpの識別情報、署名比較結果 |
| 中止条件 | application IDまたは署名不一致。以後のペアE2Eを実行しない |

### 8.2 TC-E033 No Data表示とComplication

対応: `FR-033`, `FR-052`, `TST-003`

#### A. Wearアプリ表示

1. `Wear_OS_6_1_API_36`上のWearアプリデータを消去し、Mobileからstateを送らない。
2. Localeを英語にしてWearアプリをcold launchする。
3. 画面とaccessibility hierarchyを取得する。
4. Localeを日本語へ切り替え、同じ確認を繰り返す。
5. Activity、プロセス、FATAL/ANRを確認する。

期待結果:

- 画面値は`--%`であり、`--%%`ではない。
- 英語content descriptionは`Phone battery --%`、日本語は同等の日本語表現である。
- No Data案内とMobile接続・確認の導線がLocaleに応じて表示される。
- 画面が操作可能で、クラッシュまたはANRがない。

#### B. システムComplication要求

1. SHORT_TEXTまたはRANGED_VALUEスロットを持つ、Google提供のComplication対応watch faceを有効にする。
2. watch face editorのシステムprovider pickerを開く。
3. 対象スロットへ`Battery Notifier`を選択する。
4. SHORT_TEXTとRANGED_VALUEをそれぞれ対応スロットで確認する。
5. providerへのシステム要求と、返却された`NoDataComplicationData`を確認できる証跡を取得する。
6. watch faceへ戻り、スロットがクラッシュ、無限更新、誤った`0%`またはWatch自身の電池値を表示しないことを確認する。

期待結果:

- provider pickerからBattery Notifierを選択できる。
- システム要求に対して`NoDataComplicationData`と`--%` placeholderが返る。
- 最終描画はwatch faceの仕様に従い、空表示またはplaceholder表示でもよいが、誤った正常値を表示しない。
- providerまたはSystem UIにクラッシュ、ANR、更新stormがない。

Pass条件:

- AとBの両方がPass。
- provider登録の`dumpsys`、Manifest確認、ソース確認だけではBをPassにしない。
- editorがprovider chooserを公開しない場合はBをBlockedとし、対応slotへ手動割当するか、独立Review済みのinstrumentation harnessで`onComplicationRequest`を実行する。

必要証跡:

- 英語・日本語の画面、accessibility hierarchy
- provider pickerでのBattery Notifier選択
- SHORT_TEXT/RANGED_VALUEごとのシステム要求またはReview済みharness出力
- watch faceの最終状態、FATAL/ANRなし

### 8.3 通常同期

| ID | 環境・前提 | 操作 | 期待結果 | 主な証跡 |
|---|---|---|---|---|
| TC-E001 | EMU-36-PAIR、通知許可 | Mobileを67%、非充電、監視ONにする | Wearアプリ、Tile、SHORT_TEXT/RANGED_VALUEが67%、Fresh。threshold/監視状態も一致 | 両画面、Tile/face、Wear保存sequence |
| TC-E002 | threshold=20、両通知許可 | 21%を確定後20%へ下降 | Mobile/Wear各1通知、Wear値20%。同じeventId再送で増えない | 両通知、eventId分類、通知件数 |
| TC-E003 | 正常同期済み | 充電開始へ変更 | Mobile/Wear、Tile、Complicationが充電状態。新規低残量eventなし | 画面、icon/文言、event件数 |
| TC-E004 | 正常同期済み | Mobileでthresholdを20から15へ保存 | Mobile保存、Wearに15表示、state sequence増加、通知なし | 設定画面、Wear画面、sequence |

### 8.4 切断中更新・再接続

完全切断はData Layerの全利用可能経路を失わせる。Bluetooth相当だけを切ってクラウド経路が残る状態を完全切断と扱わない。

| ID | 操作 | 期待結果 | 主な証跡 |
|---|---|---|---|
| TC-E010 | 正常同期後に全経路を切断し、70→65→60 | Mobileは60を保持して監視継続。Wearは直前値を維持し、5分超でStale | 切断状態、両端値、6分時点 |
| TC-E011 | TC-E010から経路を復旧 | Mobileが現在値を再取得し新sequenceをurgent送信。Wearは中間値で止まらず60へ収束してFresh | 復旧時刻、sequence、最終値 |
| TC-E012 | 切断中に21→20、2分以内に再接続 | Mobile即時1通知、Wearは期限内に1通知 | 発生/再接続/通知時刻、件数 |
| TC-E013 | 切断中に21→20、6分後に再接続 | Mobileのみ通知。Wearは最新stateを表示するが、期限切れeventを通知しない | event expiry、Wear通知0件 |
| TC-E014 | Review済みharnessで送信Task A/Bを逆順完了 | WearとMobileのlastSyncedは最大sequence。古い完了で後退しない | harness出力、両端sequence |

### 8.5 Fresh・Delayed・Stale・時刻

exact boundaryの`2:00`、`2:00.001`、`5:00`、`5:00.001`は単体テストを参照し、システム描画では3、6、10、60分を代表値として確認する。

| ID | 操作 | 期待結果 | 主な証跡 |
|---|---|---|---|
| TC-E030 | state受信後0～2分以内に各表示面を開く | app、Tile、ComplicationがFresh。値と充電状態が一致 | 0～2分の各表示面 |
| TC-E031 | 受信後3分、追加DataItemなし | appがDelayedと相対時刻。Tile/Complicationは値を維持し遅延説明を持つ | 3分時点、content description |
| TC-E032 | 受信後6、10、60分、追加DataItemなし | 値維持、Stale警告、最終更新、再試行。SHORT_TEXT/RANGED_VALUEの説明も古い可能性を含む | 各時点のapp/Tile/face、TalkBack |
| TC-E033 | 一度も正常stateを受信しない | 8.2の全条件 | 8.2参照 |
| TC-E034 | watch時刻を前後へ変更後、各表示面を更新 | クラッシュなし。疑わしい未来時刻をFreshへ固定せず警告し、自動時刻復帰後に回復 | 変更前後の時刻、画面、log |

### 8.6 再起動・プロセス・更新

| ID | 操作 | 期待結果 | 主な証跡 |
|---|---|---|---|
| TC-E020 | Mobile監視ONで通常reboot | boot完了後、OS許可範囲でFGS復旧。不可なら`resumeRequired`であり監視中と偽らない | boot完了、service、通知、保存状態 |
| TC-E021 | Mobile監視OFFでreboot | FGSを開始せず、監視OFFを保持 | serviceなし、設定値 |
| TC-E022 | Fresh保存時とStale保存時にWearをreboot | Activityを開く前からTile/Complicationが保存値を回復し、鮮度を再計算。再接続後Fresh | boot後各表示面、sequence |
| TC-E023 | force-stopではないprocess kill | OS/Serviceの契約に従って状態復元し、通知重複なし | process、service、通知件数 |
| TC-E024 | ユーザー操作相当でアプリをforce-stop | broadcastを受けないOS挙動を監視中と偽らず、再open後に案内 | force-stop前後のUI/Service |
| TC-E025 | `adb install -r`でMobile/Wearを更新 | 設定とschema v1を保持し、過去eventを重複通知しない | update前後の状態、通知件数 |

`TC-E020`は`sys.boot_completed=1`かつActivity/Notification/Package servicesが利用可能になってから判定する。AVDがbootを完了しない場合はBlockedであり製品Failではない。

### 8.7 連続更新・競合

| ID | 実行方法 | 期待結果 | 主な証跡 |
|---|---|---|---|
| TC-E040 | Review済みsender/harnessから100ms間隔でsequence 1..30 | Wear最終値はsequence 30、ANR/crashなし、更新要求はbounded | 送受信30件、最終DataStore、log |
| TC-E041 | 同一event callbackを並行10回 | Wear通知1件、予約試行番号の重複なし | callback数、通知件数、attempt |
| TC-E042 | Mobile threshold sliderを連続操作し最後だけ保存 | 保存値だけ同期し、送信stormなし | 操作動画、送信sequence数 |
| TC-E043 | DataItem A/Bを順不同で受信 | 最大sequenceだけ保存し、表示が古い値へ戻らない | 入力順、保存sequence、画面 |

必要なdebug sender/harnessが未実装または未Reviewの場合、対象ケースはNot RunまたはBlockedとし、production codeへTestロールが注入口を追加しない。

### 8.8 異常payload

正常stateを先に保存し、各異常入力後もその値が維持されることを確認する。入力はReview済みdebug senderまたはinstrumentation harnessから送る。

| ID | 入力 | 期待結果 |
|---|---|---|
| TC-E050 | 未知path | 無視。保存値、表示、通知を変更しない |
| TC-E051 | 必須key欠落、DataMap型違い | payload全体を拒否し、直前正常値を保持 |
| TC-E052 | `levelPercent=-1`および`101` | 全体拒否、invalid count増加、クラッシュなし |
| TC-E053 | `schemaVersion=2` | unsupportedを記録し、直前値維持、更新案内、クラッシュなし |
| TC-E054 | eventId 1,000文字および非UUID | event拒否、Wear通知なし |
| TC-E055 | `expiresAt <= occurredAt`または最大+15分超 | event拒否、Wear通知なし |
| TC-E056 | test専用DataStoreへ破損fixture | safe defaultへ回復し、過去PENDINGを通知しない |

各ケースで入力分類、invalid/unsupported count、保存前後値、通知件数、FATAL/ANRなしを記録する。

### 8.9 権限・通知

権限はMobileとWearで独立に初期化する。権限変更後はアプリをforegroundへ戻し、状態再評価を待つ。

| ID | Mobile / Wear | 操作・期待結果 |
|---|---|---|
| TC-E060 | 許可 / 許可 | 1 eventで各1通知。Mobileはlocal-only |
| TC-E061 | 拒否 / 許可 | Wearのみ1通知。Mobileは案内し、保存・同期を継続 |
| TC-E062 | 許可 / 拒否 | Mobileのみ1通知。Wearは案内し、eventを終端処理 |
| TC-E063 | 拒否 / 拒否 | 通知0件。両端の状態表示とevent記録は更新 |
| TC-E064 | 許可後に`battery_alerts` channelを無効化 | クラッシュせず、通知不可を画面に表示 |
| TC-E065 | ongoing通知のStopをタップ | FGS停止、監視OFF、ongoing通知除去、WearへOFF反映 |
| TC-E066 | Mobile通知とWearローカル通知を観測 | Mobile通知が自動ミラーされず、Wear側合計1件 |
| TC-E067 | Review済みharnessでWear初回投稿失敗後、期限内にforeground化 | attempt=2で1回再試行し、成功通知は合計1件 |
| TC-E068 | Wear投稿を3回失敗させ、さらにforeground化・明示再試行 | `FAILED_EXHAUSTED`、4回目なし |
| TC-E069 | Wear投稿が`PERMISSION_DENIED`後に許可しforeground化 | 過去eventを再投稿せず、次の新eventだけ通知可能 |

権限ダイアログはcold launch直後に自動表示されず、用途説明後のユーザー操作でのみ表示されることも記録する。

### 8.10 UI・Locale・互換性

| ID | 環境・操作 | 期待結果 |
|---|---|---|
| TC-E070 | EMU-FOLD-36で外側→内側→外側を再現 | 入力、監視状態、navigation保持、clipなし |
| TC-E071 | multi-windowまたはresizeで幅を連続変更 | Window Size Classへ追従し、全操作へ到達可能 |
| TC-E072 | 41mm/45mm相当Wear AVD | 主要情報、scroll、Tile、通知、SHORT_TEXT/RANGED_VALUEが欠けない |
| TC-E073 | Mobile/Wearを英語・日本語へ切替 | 画面、通知、Tile、provider labelがLocaleに追従 |
| TC-E074 | Mobile=日本語、Wear=英語、その逆 | payloadに文言を持たず、各端末のLocaleで表示・通知 |
| TC-E075 | TalkBack ON、最大font/display scaling | 残量、充電、鮮度、操作の意味順。色だけへ依存せず操作可能 |
| TC-E076 | 対応watch faceと非対応watch face | 対応slotで選択可能。非対応faceは対象外として正常 |

エミュレータでの41mm/45mmまたはFold結果は実機リリースゲートの代替ではなく、事前回帰として記録する。

## 9. OS代表回帰

全ケースの直積は行わず、EMU-36-PAIRで全機能を実行した後、次の代表ケースを下位APIへ展開する。

| 環境 | 代表ケース |
|---|---|
| EMU-33-MIN | PRE-001, TC-E001, TC-E033, TC-E060～TC-E063, TC-E073 |
| EMU-34 | PRE-001, TC-E001, TC-E020, TC-E021, TC-E064, TC-E065 |
| EMU-35 | PRE-001, TC-E001, TC-E020～TC-E025, TC-E034 |
| EMU-36-PAIR | 本書の全対象ケース |

## 10. 実行記録テンプレート

各実行結果は`state.yaml`へ要約し、詳細ログや画像はrepository-relativeまたは承認済み一時artifact pathへ保存する。

```yaml
- id: "TC-EXXX-RUN-001"
  test_ref: "TC-EXXX"
  executed_at: "YYYY-MM-DDTHH:MM:SS+09:00"
  tester: "actor"
  status: "pass|fail|blocked|not_run"
  build:
    commit: "<sha>"
    worktree: "clean|recorded-diff"
    application_id: "com.magicitengineer.batterynotifier"
    mobile_version: "<versionCode/versionName or n/a>"
    wear_version: "<versionCode/versionName or n/a>"
    signing: "debug|release"
  environment:
    mobile: "<AVD/API or n/a>"
    wear: "<AVD/API or n/a>"
    connection: "<paired/disconnected/reconnected/single>"
    locale: "<mobile/wear>"
    permissions: "<mobile/wear/channel>"
  steps: "<実施した手順またはコマンド>"
  expected: "<期待結果>"
  actual: "<実結果>"
  evidence:
    - "<screenshot/log/xml path>"
  finding_id: null
  blocker: null
  resume_condition: null
```

## 11. Testゲート

Humanフェーズへ進める条件:

1. PRE-001と、EMU-36-PAIRで必要な自動・エミュレータケースがPass。
2. `TC-E033`のアプリ表示とシステムComplication要求が両方Pass。
3. Failにfinding IDと処置があり、未解決Critical/Highがない。
4. Blocked/Not Runが実機専用ケースと明確に分離され、必要なエミュレータケースを実機確認へ先送りしていない。
5. Pixel 10 Pro FoldとPixel Watch 4の実機項目、バッテリー消費、通知遅延がHumanフェーズの未実施項目として残っている。
