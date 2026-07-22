# データ設計書

文書ID: DDS-001  
版: 0.1  
状態: Draft  
最終更新: 2026-07-20

## 1. ドメインモデル

### BatterySnapshot

| 項目 | 型 | 制約 | 説明 |
|---|---|---|---|
| `levelPercent` | Int | 0..100 | 正規化したMobile残量 |
| `isCharging` | Boolean | 必須 | 充電状態 |
| `capturedAtEpochMillis` | Long | 0より大 | Mobileで取得したUTC epoch millis |
| `sequence` | Long | 1以上 | Mobile DataStoreで単調増加する順序 |

### AlertRule

| 項目 | 型 | 既定値 | 制約 |
|---|---|---|---|
| `thresholdPercent` | Int | 20 | 5..100 |
| `monitoringEnabled` | Boolean | false | ユーザー操作で変更 |
| `notifyIfAlreadyBelowOnStart` | Boolean | false | v1.0 UIでは固定でもよい |
| `rearmHysteresisPercent` | Int | 2 | 1..10 |

### AlertState

| 項目 | 型 | 説明 |
|---|---|---|
| `armed` | Boolean | 次の下降交差を通知可能か |
| `previousLevelPercent` | Int? | 直前正常値 |
| `lastEventId` | String? | 直近イベントUUID |
| `lastTriggeredAtEpochMillis` | Long? | 直近到達時刻 |

### ThresholdReachedEvent

| 項目 | 型 | 説明 |
|---|---|---|
| `eventId` | String | UUID v4 |
| `levelPercent` | Int | 到達時残量 |
| `thresholdPercent` | Int | 到達時設定値 |
| `occurredAtEpochMillis` | Long | Mobileでの発生時刻 |
| `expiresAtEpochMillis` | Long | Wear通知有効期限。既定は発生+5分 |
| `sequence` | Long | 状態と同じ順序系列 |

## 2. Mobile Proto DataStore

推奨ファイル名: `battery_notifier_mobile.pb`

| グループ | 保存項目 |
|---|---|
| Settings | threshold、monitoringEnabled、onboardingCompleted、hysteresis |
| Battery | last snapshot、sequence counter |
| Alert | armed、previous level、last event、Mobile通知済みeventId |
| Sync outbox | pending state sequence、pending event、最終成功時刻、最終エラー分類 |
| Diagnostics | invalid input count、unsupported schema count |

保持するイベントは最新の未期限切れ1件と直近処理IDだけでよい。履歴分析はv1.0対象外とする。

`battery_notifier_mobile.pb`は設定・alert state・sequence・通知/outboxを1つの原子的状態として保持するため、v1.0では部分復元しない。Android cloud backupとdevice-to-device transferの双方からファイル全体を除外する。

## 3. Wear Proto DataStore

推奨ファイル名: `battery_notifier_wear.pb`

| グループ | 保存項目 |
|---|---|
| State | last valid sync envelope、receivedAtEpochMillis |
| Notification | last processed eventId、processedAtEpochMillis |
| Diagnostics | invalid payload count、unsupported schema、last receive error |

鮮度は保存せず、`now - receivedAtEpochMillis`で都度算出する。端末時刻変更を跨ぐ画面内計測には`elapsedRealtime`を併用してよいが、再起動を跨ぐ永続値にはepoch millisを用いる。

`battery_notifier_wear.pb`もAndroid cloud backupとdevice-to-device transferの双方から除外する。再インストールまたは端末移行後はNo Dataから開始し、Mobileの最新stateを新規同期する。過去のPENDING/outbox/eventIdを復元して通知してはならない。

## 4. Data Layer同期契約

### パス

| 用途 | パス | 更新方法 |
|---|---|---|
| 最新状態 | `/battery-notifier/v1/phone-state` | 固定パスを上書き |
| 到達イベント | `/battery-notifier/v1/threshold-event` | 固定パスをeventId/sequence付きで上書き |
| 状態要求 | `/battery-notifier/v1/request-state` | MessageClient。ベストエフォート |

DataItemパスをイベントごとに増やさない。固定パスにして不要DataItemを蓄積させず、値が同じ場合でも`sequence`の変更で同期を発生させる。

### phone-state DataMap

| Key | DataMap型 | 必須 | 制約 |
|---|---|---|---|
| `schemaVersion` | Int | Yes | v1は1 |
| `sequence` | Long | Yes | 1以上 |
| `levelPercent` | Int | Yes | 0..100 |
| `isCharging` | Boolean | Yes | - |
| `capturedAtEpochMillis` | Long | Yes | 0より大、受信時の未来許容は5分 |
| `thresholdPercent` | Int | Yes | 5..100 |
| `monitoringEnabled` | Boolean | Yes | - |
| `sentAtEpochMillis` | Long | Yes | 0より大 |

### threshold-event DataMap

| Key | DataMap型 | 必須 | 制約 |
|---|---|---|---|
| `schemaVersion` | Int | Yes | v1は1 |
| `eventId` | String | Yes | UUID、最大64文字 |
| `sequence` | Long | Yes | 1以上 |
| `levelPercent` | Int | Yes | 0..100 |
| `thresholdPercent` | Int | Yes | 5..100 |
| `occurredAtEpochMillis` | Long | Yes | 0より大 |
| `expiresAtEpochMillis` | Long | Yes | occurredより後、最大+15分 |

## 5. 検証順序

1. URI pathが既知の完全一致か。
2. schemaVersionが対応範囲か。
3. 必須keyとDataMap型が正しいか。
4. 数値範囲、文字列長、時刻関係が正しいか。
5. `sequence > storedSequence`か。
6. domain modelへ変換し、DataStoreへ保存する。

異常データは部分採用しない。直前の正常状態を維持し、ログは値全体ではなくエラー分類を記録する。

## 6. 順序と競合

- Mobileの単一DataStore更新でsequenceを増加させる。
- Wearは`sequence`を第一比較キーとする。
- 同じsequenceが重複した場合は冪等に無視する。
- sequenceが小さいデータは、capturedAtが新しく見えても無視する。
- DataStore初期化や再インストールでsequenceがリセットされるため、将来複数source対応時は`sourceInstanceId`が必要になる。v1は単一Mobile・両アプリ再セットアップを前提とする。
- Mobileのデータ消去後はWear側データもユーザーの再ペア設定時にクリアする導線を設ける。

## 7. データ保持と削除

- アプリ設定、最終状態、直近イベントのみ端末内へ保持する。
- アプリのデータ削除またはアンインストールで削除される。
- 外部サーバー、分析基盤、ファイル共有領域へ保存しない。
- Data Layerは接続経路によりGoogle管理の中継を利用し得るため、送信項目を電池状態と設定値に限定する。

## 8. 例示

```json
{
  "schemaVersion": 1,
  "sequence": 42,
  "levelPercent": 20,
  "isCharging": false,
  "capturedAtEpochMillis": 1784516400000,
  "thresholdPercent": 20,
  "monitoringEnabled": true,
  "sentAtEpochMillis": 1784516400500
}
```

JSONは説明用であり、実装はDataMapを使用する。
