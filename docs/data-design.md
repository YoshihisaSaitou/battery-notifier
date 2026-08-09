# データ設計書

文書ID: DDS-001  
版: 0.1  
状態: Draft  
最終更新: 2026-08-09

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
| `fullChargeNotificationEnabled` | Boolean | false | Mobileが正本 |

### AlertState

| 項目 | 型 | 説明 |
|---|---|---|
| `armed` | Boolean | 次の下降交差を通知可能か |
| `previousLevelPercent` | Int? | 直前正常値 |
| `lastEventId` | String? | 直近イベントUUID |
| `lastTriggeredAtEpochMillis` | Long? | 直近到達時刻 |
| `fullChargeArmed` | Boolean | 現在の充電セッションで満充電通知可能か |

### ThresholdReachedEvent

| 項目 | 型 | 説明 |
|---|---|---|
| `eventId` | String | UUID v4 |
| `levelPercent` | Int | 到達時残量 |
| `thresholdPercent` | Int | 到達時設定値 |
| `occurredAtEpochMillis` | Long | Mobileでの発生時刻 |
| `expiresAtEpochMillis` | Long | Wear通知有効期限。既定は発生+5分 |
| `sequence` | Long | 状態と同じ順序系列 |

### ThresholdChangeRequest

WearからMobileへ送る変更要求であり、設定の正本ではない。

| 項目 | 型 | 制約 | 説明 |
|---|---|---|---|
| `requestId` | String | UUID v4、最大64文字 | 再試行と結果照合の冪等キー |
| `thresholdPercent` | Int | 5..100 | ユーザーが要求する値 |
| `expectedThresholdPercent` | Int | 5..100 | Wearが最後に確認したMobileの有効値 |

### ThresholdChangeResult

| 項目 | 型 | 制約 | 説明 |
|---|---|---|---|
| `requestId` | String | 要求と一致 | 結果照合キー |
| `resultCode` | Enum | `APPLIED`、`CONFLICT`、`REJECTED` | Mobileでの確定結果 |
| `effectiveThresholdPercent` | Int | 5..100 | 結果時点のMobile有効値 |
| `phoneStateSequence` | Long | 1以上 | 結果に対応してMobileが確定したstate順序 |

### FullChargeReachedEvent

| 項目 | 型 | 説明 |
|---|---|---|
| `eventId` | String | UUID v4 |
| `levelPercent` | Int | 常に100 |
| `occurredAtEpochMillis` | Long | Mobileでの発生時刻 |
| `expiresAtEpochMillis` | Long | Wear通知有効期限。既定は発生+5分 |
| `sequence` | Long | phone-stateと同じ順序系列 |

### FullChargeSettingChangeRequest

要求は`requestId`、`enabled`、`expectedEnabled`を持つ。Mobileが設定の正本を保持し、Wearは後続phone-stateだけを確定値として表示する。

## 2. Mobile Proto DataStore

推奨ファイル名: `battery_notifier_mobile.pb`

| グループ | 保存項目 |
|---|---|
| Settings | threshold、monitoringEnabled、fullChargeNotificationEnabled、onboardingCompleted、hysteresis |
| Battery | last snapshot、sequence counter |
| Alert | armed、previous level、last event、Mobile通知済みeventId |
| Sync outbox | pending state sequence、pending event、最終成功時刻、最終エラー分類 |
| Diagnostics | invalid input count、unsupported schema count |
| Wear threshold request result | 直近requestId、resultCode、有効しきい値、対応state sequence |
| Full-charge alert/outbox | arm状態、直近event、Mobile通知済みeventId、Wear同期outbox |

保持するイベントは最新の未期限切れ1件と直近処理IDだけでよい。履歴分析はv1.0対象外とする。

`battery_notifier_mobile.pb`は設定・alert state・sequence・通知/outboxを1つの原子的状態として保持するため、v1.0では部分復元しない。Android cloud backupとdevice-to-device transferの双方からファイル全体を除外する。

## 3. Wear Proto DataStore

推奨ファイル名: `battery_notifier_wear.pb`

| グループ | 保存項目 |
|---|---|
| State | last valid sync envelope、receivedAtEpochMillis |
| Notification | last processed eventId、processedAtEpochMillis |
| Diagnostics | invalid payload count、unsupported schema、last receive error |
| Threshold edit | 下書き、expected threshold、未確定requestId、送信/結果状態 |
| Full-charge notification | 直近処理eventId、投稿状態、試行回数 |

鮮度は保存せず、`now - receivedAtEpochMillis`で都度算出する。端末時刻変更を跨ぐ画面内計測には`elapsedRealtime`を併用してよいが、再起動を跨ぐ永続値にはepoch millisを用いる。

`battery_notifier_wear.pb`もAndroid cloud backupとdevice-to-device transferの双方から除外する。再インストールまたは端末移行後はNo Dataから開始し、Mobileの最新stateを新規同期する。過去のPENDING/outbox/eventIdを復元して通知してはならない。

## 4. Data Layer同期契約

### パス

| 用途 | パス | 更新方法 |
|---|---|---|
| 最新状態 | `/battery-notifier/v1/phone-state` | 固定パスを上書き |
| 到達イベント | `/battery-notifier/v1/threshold-event` | 固定パスをeventId/sequence付きで上書き |
| 状態要求 | `/battery-notifier/v1/request-state` | MessageClient。ベストエフォート |
| しきい値変更要求 | `/battery-notifier/v1/change-threshold` | MessageClient。WearからMobileへの接続中RPC |
| しきい値変更結果 | `/battery-notifier/v1/change-threshold-result` | MessageClient。Mobileから要求元Wearへの応答 |
| 満充電イベント | `/battery-notifier/v1/full-charge-event` | 固定パスをeventId/sequence付きで上書き |
| 満充電通知設定要求 | `/battery-notifier/v1/change-full-charge-setting` | MessageClient。WearからMobileへの接続中RPC |

DataItemパスをイベントごとに増やさない。固定パスにして不要DataItemを蓄積させず、値が同じ場合でも`sequence`の変更で同期を発生させる。

満充電通知設定要求に対応するMobile capabilityは`battery_notifier_full_charge_setting_writer_v1`とする。既存の`battery_notifier_threshold_writer`とは分離し、旧Mobileを誤って対応済みと判定しない。

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
| `fullChargeNotificationEnabled` | Boolean | No | 欠落時はfalse。BN-004以前の送信元との互換用 |

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

### full-charge-event DataMap

| Key | DataMap型 | 必須 | 制約 |
|---|---|---|---|
| `schemaVersion` | Int | Yes | v1は1 |
| `eventId` | String | Yes | UUID、最大64文字 |
| `sequence` | Long | Yes | 1以上 |
| `levelPercent` | Int | Yes | 100 |
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

## 9. Wearしきい値変更メッセージ契約（BN-002提案）

メッセージpayloadは`DataMap.toByteArray()`相当の型付きbinaryとし、Kotlin文字列やLocale依存文言を含めない。両pathは完全一致で判定する。

### change-threshold request

| Key | DataMap型 | 必須 | 制約 |
|---|---|---|---|
| `schemaVersion` | Int | Yes | 1 |
| `requestId` | String | Yes | UUID、最大64文字 |
| `thresholdPercent` | Int | Yes | 5..100 |
| `expectedThresholdPercent` | Int | Yes | 5..100 |

### change-threshold-result

| Key | DataMap型 | 必須 | 制約 |
|---|---|---|---|
| `schemaVersion` | Int | Yes | 1 |
| `requestId` | String | Yes | 要求と一致するUUID |
| `resultCode` | String | Yes | `APPLIED`、`CONFLICT`、`REJECTED` |
| `effectiveThresholdPercent` | Int | Yes | 5..100 |
| `phoneStateSequence` | Long | Yes | 1以上 |

### 検証・原子性・冪等性

1. Mobileはpath、schema、必須key、型、UUID、範囲を全体検証し、部分採用しない。
2. 同じ`requestId`がMobile Proto DataStoreの直近処理済み要求と一致する場合、設定を再適用せず保存済み結果を再送する。
3. `thresholdPercent == Mobileの現在値`なら、期待値にかかわらず冪等な`APPLIED`として現在値を返してよい。
4. それ以外で`expectedThresholdPercent != Mobileの現在値`なら`CONFLICT`とし、設定を変更しない。
5. 適用する場合は、既存のしきい値変更domain処理、alert state再評価、Mobile state `sequence`増加、同期outbox、要求結果を同じ直列化境界で確定する。設定変更だけで到達イベントを作らない。
6. 結果送信が失敗しても保存済み結果を保持し、同じ`requestId`の再試行へ再送する。
7. Wearは`requestId`が現在の未確定要求と一致する正常な結果だけを採用する。未知・古い・異常な結果で表示値や下書きを変更しない。
8. Wearは`APPLIED`結果を受けても、後続または既受信のphone-stateが`phoneStateSequence`以上へ収束するまで「同期確認中」と表示できる。最終表示の正本はphone-stateのしきい値である。

MessageClientは接続が必要で永続再送を行わない。未送信または結果不明の要求はWear Proto DataStoreへ保持するが、再接続や再起動だけでは自動送信しない。

## 10. Wear満充電通知設定メッセージ契約（BN-004）

### change-full-charge-setting request

| Key | DataMap型 | 必須 | 制約 |
|---|---|---|---|
| `schemaVersion` | Int | Yes | 1 |
| `requestId` | String | Yes | UUID、最大64文字 |
| `enabled` | Boolean | Yes | 要求値 |
| `expectedEnabled` | Boolean | Yes | Wearが最後に確認したMobile値 |

Mobileは要求を完全検証する。要求値が現在値と同じ場合は設定・sequenceを変更せず現在のphone-stateを再送する。期待値が現在値と異なる場合も設定を変更せず現在のphone-stateを再送する。それ以外は設定・満充電arm状態・sequence・outboxを原子的に確定してphone-stateを送る。MessageClient送信失敗、再接続、再起動だけでは自動再送せず、ユーザーの再操作を待つ。
