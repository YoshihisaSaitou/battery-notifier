# 権限・プライバシー仕様書

文書ID: PPS-001  
版: 0.1  
状態: Draft  
最終更新: 2026-07-22

## 1. 基本方針

- 機能に必要な最小限の権限だけを要求する。
- 権限要求前に目的、拒否時の影響、停止方法を説明する。
- 電池情報と設定は端末内およびペア端末間だけで扱い、独自サーバーへ送信しない。
- v1.0では広告、分析、クラッシュ収集SDKを導入しない。
- ユーザーが監視を停止でき、アプリデータ削除/アンインストールで保存データを削除できる。

## 2. Mobile権限

| 権限 | 種別 | 用途 | 要求時点 |
|---|---|---|---|
| `POST_NOTIFICATIONS` | Runtime、API 33+ | 低残量、監視中、復旧案内 | オンボーディング説明後 |
| `FOREGROUND_SERVICE` | Manifest | 継続監視Service | インストール時 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Manifest、API 34+ | 他typeに該当しない電池監視 | インストール時 |
| `RECEIVE_BOOT_COMPLETED` | Manifest | 監視有効時の再起動復旧 | インストール時 |
| `VIBRATE` | Normal | alert channelの振動をOS設定に従い利用 | インストール時。必要性を実装時確認 |

電池状態取得自体に危険権限は不要である。位置情報、Bluetooth scan/connect、連絡先、電話、ストレージ、正確なアラームは要求しない。

## 3. Wear権限

| 権限 | 種別 | 用途 |
|---|---|---|
| `POST_NOTIFICATIONS` | Runtime、対応OS | Wearローカル低残量通知 |
| `VIBRATE` | Normal | Wear通知channelの振動をOS設定に従い利用 |
| `BIND_COMPLICATION_PROVIDER` | Service保護 | システムだけがproviderへbindできるようにする |
| `BIND_TILE_PROVIDER` | Service保護 | システムだけがTile providerへbindできるようにする |

Data Layer通信はGoogle Play services APIを使用し、独自のBluetooth権限や低レベルsocketを追加しない。

## 4. 保存・送信データ

| データ | Mobile保存 | Wear送信 | Wear保存 | 保持目的 |
|---|---:|---:|---:|---|
| 電池残量・充電状態 | Yes | Yes | Yes | 表示・判定 |
| しきい値・監視状態 | Yes | Yes | Yes | 表示・判定 |
| 取得/送受信時刻・sequence | Yes | Yes | Yes | 鮮度・順序 |
| eventId・有効期限 | Yes | Yes | Yes | 通知重複防止 |
| 通知権限状態 | 派生/必要最小限 | No | 派生/必要最小限 | UI案内 |
| 位置、アカウント、連絡先 | No | No | No | 収集しない |
| 広告ID、端末ハードウェアID | No | No | No | 収集しない |

Data LayerはBluetooth、Wi-Fi、またはGoogle管理のクラウド中継を利用する場合があり、通信はGoogle Play servicesにより保護される。独自サーバーへのアップロードではないが、この可能性をプライバシー説明へ明記する。

## 5. コンポーネント公開

- Activityは外部起動が必要なものだけexportする。
- BroadcastReceiverは明示PendingIntent用を`exported=false`にする。
- Wear Listener Serviceは公式要件に従うintent filterを最小pathへ限定する。
- Complication/Tile serviceは必要なBIND permissionで保護する。
- PendingIntentは明示Intent、`FLAG_IMMUTABLE`を既定とする。
- debug用Activity/diagnosticsはreleaseでexportしない。

## 6. バックアップ

- v1.0ではMobileの`battery_notifier_mobile.pb`とWearの`battery_notifier_wear.pb`をAndroid cloud backupおよびdevice-to-device transferの双方からファイル単位で除外する。
- 設定だけを切り離して復元するとalert state、sequence、eventId、outboxとの原子性が失われるため、Proto DataStoreの部分復元は行わない。
- 再インストールまたは端末移行後は既定設定/No Dataから開始する。過去の`monitoringEnabled`でFGSを自動開始せず、過去のPENDING通知・outbox・処理済みIDを復元しない。
- 新規セットアップ後、WearはMobileから送られた新しいsequenceの最新stateへ収束する。

## 7. 権限拒否・取消

- 拒否してもアプリを終了させず、残量表示と設定を利用可能にする。
- 「通知できない」と「監視していない」を別状態で表示する。
- OS設定から権限が取り消された場合、次回resumeで状態を再評価する。
- 通知権限の再要求を繰り返さず、システム設定への明示導線を提供する。
- FGS/通知ポリシーを満たせない場合、ユーザーに保証できない監視を「有効」と表示しない。

## 8. Google Play対応

- target API 34以上で使用するFGS typeをPlay Consoleへ申告する。
- 機能説明、遅延/中断時の影響、ユーザーが開始・停止する動画、`specialUse`の根拠を用意する。
- 公開前に最新のDevice and Network Abuse policyを再確認する。
- Data safety欄は実装したSDKと送信経路を監査して回答し、文書だけから自動決定しない。

## 9. プライバシー表示に含める内容

- 何を監視するか: スマートフォンの電池残量と充電状態。
- どこへ保存するか: Mobileとペアリング済みWear端末のアプリ領域。
- どこへ送るか: Wear Data Layer経由のペア端末。経路にGoogle管理中継が使われる場合がある。
- 何を収集しないか: 位置、アカウント、広告ID、連絡先、閲覧履歴。
- 停止/削除方法: 監視停止、アプリデータ削除、アンインストール。

## 10. 参考

- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Wear Data Layer security](https://developer.android.com/training/wearables/data/overview)
