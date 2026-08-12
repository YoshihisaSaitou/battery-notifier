# システムアーキテクチャ設計書

文書ID: SAD-001  
版: 0.2
状態: Draft  
最終更新: 2026-08-12

## 1. アーキテクチャ方針

小規模アプリに過剰な抽象化を持ち込まず、簡易DDDとAndroid推奨の単方向データフローを組み合わせる。

- ドメインルールをAndroid APIから分離し、JVM単体テスト可能にする。
- UI、アプリケーション、ドメイン、データ/プラットフォームの依存方向を内側へ向ける。
- 1ユースケース1クラスを機械的に増やさず、複雑な業務ルールだけを明示的なUseCaseへする。
- Repositoryは保存先やData Layerなど外部境界がある箇所に限定する。
- 初期版は手動DIを採用し、依存数やスコープが増えた時点でHilt導入を再評価する。

## 2. システム構成

```mermaid
flowchart LR
    Battery["Android BatteryManager / ACTION_BATTERY_CHANGED"] --> MobileMonitor["Mobile Battery Monitoring"]
    MobileUI["Mobile Compose UI"] --> MobileApp["Mobile Application Layer"]
    MobileUI --> AdConsent["UMP consent / privacy options"]
    AdConsent -->|"canRequestAds"| MobileAds["Google Mobile Ads SDK"]
    MobileAds --> AdMob["Google AdMob"]
    MobileMonitor --> MobileDomain["Threshold Domain"]
    MobileDomain --> MobileStore["Mobile Proto DataStore"]
    MobileDomain --> MobileNotify["Mobile Notification"]
    MobileDomain --> DataClient["Wear Data Layer DataClient"]
    DataClient <--> PlayServices["Google Play services"]
    PlayServices <--> WearListener["Wear Data Layer Listener"]
    WearListener --> WearStore["Wear Proto DataStore"]
    WearStore --> WearUI["Wear Compose UI"]
    WearStore --> Tile["Wear Tile"]
    WearStore --> Complication["Complication Data Source"]
    WearListener --> WearNotify["Wear Notification"]
    WearUI --> WearCommand["Wear Threshold Request"]
    WearCommand --> PlayServices
    PlayServices --> MobileCommand["Mobile Threshold Request Handler"]
    MobileCommand --> MobileApp
```

Data Layerは配送路であり永続ストレージではない。各端末は受信した正常値を自身のDataStoreへ保存する。

## 3. 境界づけられたコンテキスト

| コンテキスト | 責務 | 主なモデル |
|---|---|---|
| Battery Monitoring | OS電池値の取得・正規化 | `BatterySnapshot` |
| Alerting | しきい値、再アーム、通知イベント | `AlertRule`, `AlertState`, `ThresholdReachedEvent` |
| Full-charge Alerting | 充電セッション、満充電arm、通知イベント | `FullChargeAlertState`, `FullChargeReachedEvent` |
| Wear Synchronization | DTO検証、順序、送受信、再接続 | `BatterySyncEnvelope`, `SyncCursor` |
| Presentation | 画面状態、鮮度、ローカライズ | `BatteryUiState`, `Freshness` |
| Mobile Advertising | UMP同意状態、SDK初期化gate、banner lifecycle | `ConsentInformation`, `AdView` |

この規模では各コンテキストを独立Gradleモジュールにせず、パッケージ境界と依存テストで分離する。

## 4. レイヤ

### Presentation

- Compose UI、ViewModel、UI state。
- domain/dataの`Flow`を`StateFlow`へ変換する。
- Android API呼び出しやDataStore直接アクセスを行わない。
- 広告表示はMobileのScaffold bottom barへ限定し、`canRequestAds`とSDK初期化完了をUI stateとして受け取る。

### Application

- `StartMonitoring`、`StopMonitoring`、`ChangeThreshold`、`ProcessBatterySnapshot`、`SyncCurrentState`などの処理調整。
- トランザクション境界、Repository呼び出し順、通知・同期副作用を管理する。

### Domain

- Kotlinのみで記述する値オブジェクト、エンティティ、ポリシー。
- しきい値交差、再アーム、鮮度、順序比較、データ検証を実装する。
- `Context`、`Intent`、Google Play services型へ依存しない。

### Data / Platform

- Proto DataStore、BatteryManager、BroadcastReceiver、Foreground Service。
- DataClient、WearableListenerService、NotificationManager。
- Mobile限定のUMP `ConsentInformation`、Google Mobile Ads初期化、anchored adaptive `AdView`。
- DTOとdomain modelのMapper。

## 5. Mobile推奨パッケージ

```text
BatteryNotifierAndroidMobileApp/app/src/main/java/<base-package>/
  presentation/
  application/
  domain/
    battery/
    alert/
    sync/
  data/
    datastore/
    wearable/
  platform/
    ads/
    battery/
    notification/
    service/
    boot/
```

## 6. Wear推奨パッケージ

```text
BatteryNotifierAndroidWearApp/app/src/main/java/<base-package>/
  presentation/
  application/
  domain/
  data/
    datastore/
    wearable/
  platform/
    notification/
  tile/
  complication/
```

Mobile/Wearのapplication IDと署名は一致させる。現在の雛形は異なるapplication IDであるため、Data Layer実装前に同じ値へ変更する。Kotlin namespaceは必要に応じて端末別サブパッケージを使ってよい。

## 7. 状態フロー

1. Foreground Serviceが`ACTION_BATTERY_CHANGED`を受信する。
2. Adapterが`BatterySnapshot`へ正規化する。
3. Application層が現在の`AlertState`とともにdomainの判定器へ渡す。
4. 結果を1回のDataStore更新で保存する。
5. 状態変化があればMobile UIへFlowで反映する。
6. 到達イベントがあればMobile通知を冪等に投稿する。
7. 最新状態とイベントをData Layerへ送る。
8. Wearが検証・順序判定・保存し、UI/Tile/Complication/通知を更新する。

### 7.1 Wear起点設定変更（BN-002提案）

1. Wear Presentationは下書きをApplication層へ渡し、Proto DataStoreへ未確定要求を保存する。
2. Wear Data/PlatformがMessageClientで型付き要求を対応Mobile nodeへ送る。
3. Mobile Data/Platformがmessageを型付き値へ変換し、Application層へ渡す。
4. Mobile ApplicationはMobile UIのしきい値変更と同じ直列化境界で期待値、冪等キー、domain ruleを評価する。
5. Mobile DataStoreが設定、alert state、state sequence、outbox、直近要求結果を原子的に保存する。
6. Mobileはphone-stateと要求結果を送る。Wearは結果だけで新しい正本を作らず、phone-stateへ収束する。

WearからMobile DataStoreを直接更新する境界は作らない。Wearの未確定要求はUI復旧用であり、設定の正本ではない。

### 7.2 満充電通知とWear起点設定（BN-004）

1. Mobileの同じBatterySnapshot処理内で、低残量判定と独立した純粋domain ruleが充電セッションごとの満充電到達を評価する。
2. Mobile DataStoreは満充電arm、event、sequence、通知/sync outboxを低残量状態と同じtransactionで保存する。
3. MobileとWearのnotification adapterはイベント種別ごとの処理済み状態を使い、互いのeventを上書きしない。
4. WearのON/OFF操作は専用MessageClient要求としてMobileへ送り、ADR-015と同じMobile単一writer、期待値競合、結果永続化、明示再試行境界を使う。
5. phone-stateの任意booleanで確定値を同期し、旧Mobile/旧Wearはfalseまたは未知path無視で安全に共存する。

### 7.3 Mobile広告起動と表示（BN-010）

1. `MainActivity`は起動ごとにUMPへ同意情報更新を要求し、必要な同意フォームを表示する。
2. platform/ads境界は`canRequestAds`とprivacy options requirementだけをPresentationへ公開する。広告IDや同意文字列をdomain、Proto DataStore、Data Layerへ渡さない。
3. `canRequestAds=true`になった後だけGoogle Mobile Ads SDKを1回初期化する。
4. SDK初期化完了後だけ、現在幅からanchored adaptive sizeを計算した`AdView`をMobileのbottom barへ構成して1回読み込む。
5. `canRequestAds=false`への変更またはComposition離脱時は`AdView.destroy()`で資源を解放する。広告エラーは本来機能の状態へ伝播させない。

debug/releaseのapplication IDとbanner IDはbuild variantで分離し、実行時の任意切替や外部設定からproduction IDをdebugへ注入する経路を作らない。

## 8. 並行処理

- 電池イベント処理は単一`CoroutineScope`と`Mutex`または単一actorで直列化する。
- DataStoreの`updateData`内で`sequence`増加と状態保存を原子的に行う。
- 通知と同期は保存済みのoutbox状態から実行し、成功後に配信済みフラグを更新する。
- 同期失敗は状態を失わず、次の電池更新、node変化、手動同期で再試行する。
- ViewModelは保存層のFlowを読むだけとし、複数の独立した真実のソースを作らない。
- Mobile UI変更とWear要求を同じMutex/actorへ直列化し、期待値比較から保存までを割り込ませない。
- `requestId`の処理済み結果を設定変更と同じtransactionで保存し、結果message喪失後の再試行で再適用しない。

## 9. エラー処理

| エラー | 方針 |
|---|---|
| Battery intent項目欠落 | 入力を破棄し診断カウンタを加算 |
| DataStore破損 | serializerで破損を検知し、安全な既定値への回復とエラー記録 |
| Data Layer利用不可 | Mobile監視・通知は継続し、UIへ同期不可を表示 |
| 同期Task失敗 | 指数バックオフではなく次の自然な契機＋上限付き明示再試行 |
| 未対応schema | 直前正常値を保持し、アプリ更新案内 |
| 通知権限なし | 例外扱いせず、通知不可状態としてUIへ公開 |
| FGS開始制限 | ユーザー操作からの再開を案内し、バックグラウンドから無理に再試行しない |
| UMP更新/フォーム失敗 | 保存済み`canRequestAds`を再評価し、falseなら広告なしで継続する |
| Mobile Ads初期化/読込失敗 | 広告枠を表示せず、本来機能と同期・通知の状態を変更しない |

## 10. ビルドと品質ゲート

- Android Studioで両プロジェクトを個別に開いてビルド可能にする。
- domain単体テスト、DataStore/Mapperテスト、Compose UIテスト、Data Layer統合テストを分ける。
- 両アプリの同期契約に同じfixtureを適用するcontract testを用意する。
- Pull Request相当の作業完了条件は、仕様更新、テスト、レビュー、`state.yaml`更新である。
- 広告gateではbuild variant ID分離、同意前0 request、1枠制約、View破棄、Mobile-only依存を静的/自動テストし、production広告を自動試験で要求しない。

詳細決定は[architecture-decision-records.md](architecture-decision-records.md)を参照する。
