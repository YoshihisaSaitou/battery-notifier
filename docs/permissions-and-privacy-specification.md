# 権限・プライバシー仕様書

文書ID: PPS-001  
版: 0.2
状態: Draft  
最終更新: 2026-08-12

## 1. 基本方針

- 機能に必要な最小限の権限だけを要求する。
- 権限要求前に目的、拒否時の影響、停止方法を説明する。
- 電池情報と設定は端末内およびペア端末間だけで扱い、独自サーバーへ送信しない。
- v1.0の承認済み例外としてMobileへGoogle Mobile Ads SDKとUMP SDKを導入する。分析SDK、クラッシュ収集SDK、Wear広告は導入しない。
- ユーザーが監視を停止でき、アプリデータ削除/アンインストールで保存データを削除できる。

## 2. Mobile権限

| 権限 | 種別 | 用途 | 要求時点 |
|---|---|---|---|
| `POST_NOTIFICATIONS` | Runtime、API 33+ | 低残量、監視中、復旧案内 | オンボーディング説明後 |
| `FOREGROUND_SERVICE` | Manifest | 継続監視Service | インストール時 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Manifest、API 34+ | 他typeに該当しない電池監視 | インストール時 |
| `RECEIVE_BOOT_COMPLETED` | Manifest | 監視有効時の再起動復旧 | インストール時 |
| `VIBRATE` | Normal | alert channelの振動をOS設定に従い利用 | インストール時。必要性を実装時確認 |
| `INTERNET` | Manifest/Normal、SDK manifest merge | AdMobの同意情報更新と広告配信 | インストール時 |
| `ACCESS_NETWORK_STATE` | Manifest/Normal、SDK manifest merge | 広告配信時のネットワーク状態判定 | インストール時 |
| `com.google.android.gms.permission.AD_ID` | Manifest/Normal、SDK manifest merge | OSと同意状態が許す場合の広告配信。Google Mobile Ads SDK 20.4.0以降が自動宣言 | インストール時 |

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
| 満充電通知ON/OFF・充電session arm | Yes | ON/OFFのみ | ON/OFFと未確定要求 | 表示・判定・要求復旧 |
| 取得/送受信時刻・sequence | Yes | Yes | Yes | 鮮度・順序 |
| eventId・有効期限 | Yes | Yes | Yes | 通知重複防止 |
| 通知権限状態 | 派生/必要最小限 | No | 派生/必要最小限 | UI案内 |
| 位置、アカウント、連絡先 | No | No | No | 収集しない |
| 広告ID | アプリ独自保存No。Mobile Ads SDKの処理はあり得る | No | No | 同意、OS設定、Google SDK/AdMob設定に従う広告配信。DataStore、ログ、Data Layerへ保存しない |
| 端末ハードウェアID | No | No | No | アプリ独自に収集しない |

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
- AdMobを含むreleaseの公開前に、Play Consoleの「広告を含む」、Data safety、対象年齢とコンテンツ、プライバシーポリシーURLを人間が確認する。
- AdMob Privacy & messagingで必要な地域向けメッセージを公開し、production application IDとの対応を人間が確認する。

## 9. プライバシー表示に含める内容

- 何を監視するか: スマートフォンの電池残量と充電状態。
- どこへ保存するか: Mobileとペアリング済みWear端末のアプリ領域。
- どこへ送るか: Wear Data Layer経由のペア端末。経路にGoogle管理中継が使われる場合がある。
- 何を収集しないか: Battery Notifier自身は位置、アカウント、広告ID、連絡先、閲覧履歴を保存・Wear送信しない。
- 外部処理: MobileではGoogle Mobile Ads SDK/UMPが同意情報、OS設定、AdMob設定に従って広告配信・測定用データをGoogleへ送る場合がある。広告なしでも電池監視、設定、同期を利用できる。
- 選択変更: UMPが必要と判定した場合はアプリ内の`プライバシー設定 / Privacy options`から再表示する。
- 停止/削除方法: 監視停止、アプリデータ削除、アンインストール。

## 10. 参考

- [Notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Wear Data Layer security](https://developer.android.com/training/wearables/data/overview)

## 11. Wearしきい値変更（BN-002提案）

- 既存のWear Data Layer MessageClientを使い、新しい権限、Bluetooth scan/connect、外部サービス、独自socketを追加しない。
- 要求payloadは`requestId`、要求しきい値、期待しきい値、schema versionだけとし、端末名、node ID、アカウント、位置、時刻、言語文字列を永続payloadへ含めない。
- node IDは結果を要求元へ返す実行時routingだけに使用し、Proto、ログ、分析へ保存しない。
- `requestId`はランダムUUIDであり、端末識別子として再利用しない。
- Mobileは直近要求結果、Wearは下書きと未確定要求を各Proto DataStoreへ保存する。両Protoは引き続きcloud backupとdevice transferから除外する。
- 不正payloadをログへ出力せず、エラー分類と診断件数だけを記録する。

## 12. 満充電通知とWear設定（BN-004）

- 既存の電池状態、通知権限、Data Layerだけを使用し、新しいAndroid権限、Bluetooth権限、外部サービス、分析SDKを追加しない。
- 同期する追加データは満充電通知ON/OFF、満充電eventId/sequence/発生時刻/期限だけとする。充電履歴や端末識別子を保存・送信しない。
- Wear要求のnode IDは実行時routingだけに使い、Protoやログへ保存しない。`requestId`はランダムUUIDで、端末識別に再利用しない。
- Mobile/Wear Protoは引き続きbackup/transfer対象外とし、不正payloadは値を記録せず分類と件数だけを残す。

## 13. Mobile AdMobバナー（BN-010）

- 対象はMobileの画面下部に固定するanchored adaptive banner 1枠だけとし、Wear、Tile、コンプリケーション、通知には広告SDKも広告表示も追加しない。
- UMPの`requestConsentInfoUpdate()`を起動ごとに呼び、必要なフォームを処理する。`canRequestAds`がtrueになる前はMobile Ads SDKを初期化せず広告要求を行わない。
- privacy options entry pointが必要な場合は、Mobile画面からいつでもUMPの選択画面を再表示できるようにする。
- 同意更新の失敗時は保存済み状態を再評価し、`canRequestAds=false`なら広告なしで動作する。監視・設定・同期・通知を広告障害へ連動させない。
- debugはGoogle公式demo application/banner ID、releaseだけが人間提示のproduction application/banner IDを使う。production広告を開発者が読み込んだりクリックしたりしない。
- アプリ独自の広告イベント、クリック追跡、広告ID/端末IDのDataStore保存、ログ出力、Data Layer送信は行わない。
- 対象年齢、child-directed/under-age tag、AdMobメッセージ公開、Data safety、広告申告、privacy policyの最終内容はproduction配布前のHuman gateとする。

## 14. AdMob参考（2026-08-12確認）

- [Google Mobile Ads SDK Android quick start](https://developers.google.com/admob/android/quick-start)
- [Anchored adaptive banner](https://developers.google.com/admob/android/banner)
- [User Messaging Platform SDK](https://developers.google.com/admob/android/privacy)
- [EEA/UK/Switzerland consent guidance](https://developers.google.com/admob/android/privacy/gdpr)
