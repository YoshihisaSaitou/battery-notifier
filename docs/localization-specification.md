# 多言語対応仕様書

文書ID: L10N-001  
版: 0.3
状態: Draft  
最終更新: 2026-08-11

## 1. 対応言語

| 言語 | Locale | リソース |
|---|---|---|
| 英語 | `en` | `values/strings.xml`の既定値 |
| 日本語 | `ja` | `values-ja/strings.xml` |

既定リソースは英語とし、日本語以外の未対応Localeでは英語へfallbackする。MobileとWearはそれぞれの端末/アプリLocaleで表示し、同期payloadに完成済み文言を含めない。

## 2. 対象範囲

- Mobile/Wearの全画面、ダイアログ、Snackbar。
- しきい値通知、ongoing通知、同期問題通知。
- Tile、コンプリケーションのlabel、preview、content description。
- AndroidManifestのapp/service/activity label。
- 権限事前説明、プライバシー説明、エラー、診断のユーザー向け分類。
- Play Store掲載文、スクリーンショットはリリース工程で別途日英作成する。

ログ、class名、DataItem key、channel ID、path、schema fieldは翻訳しない。

## 3. リソース設計

- Kotlin/Composeへユーザー向け文字列を直書きしない。
- 数値埋め込みはformat resourceを使う。
- 分・時間など個数で変わる表現は`plurals`を使用する。
- 文字列連結で語順を組み立てず、文全体を翻訳単位にする。
- 通知channel name/descriptionもリソース化する。
- 翻訳対象外のplaceholderは`translatable="false"`を明示する。

### 推奨キー例

```text
app_name
battery_level_percent
phone_battery_content_description
monitoring_on
monitoring_off
threshold_reached_title
threshold_reached_body
data_fresh
data_delayed
data_stale
last_updated_minutes_ago
sync_now
syncing_phone_battery
allow_notifications
change_threshold
save_threshold
threshold_checking_phone
threshold_saved_confirming_sync
threshold_not_saved
threshold_conflict
retry_threshold_change
threshold_slider_description
full_charge_notification
full_charge_notification_description
full_charge_reached_title
full_charge_reached_body
full_charge_setting_checking_phone
full_charge_setting_saved_confirming_sync
full_charge_setting_not_saved
full_charge_setting_conflict
retry_full_charge_setting_change
```

## 4. 表記ルール

| 概念 | 日本語 | 英語 |
|---|---|---|
| 対象端末 | スマートフォン | Phone |
| 電池 | 電池残量 | Battery level |
| しきい値 | 通知する残量 | Alert threshold |
| Fresh | 最新 | Up to date |
| Stale | 古いデータの可能性 | Data may be outdated |
| Monitoring | 監視 | Monitoring |
| Sync | 同期 | Sync |
| 手動同期の用途 | 電池状態は通常、自動でウォッチへ同期されます。「今すぐ同期」は、自動同期されない場合や最新の状態に更新したい場合に使用します。 | Battery state normally syncs to the watch automatically. Use Sync now if automatic sync does not occur or you want to refresh the latest state. |
| 手動同期中 | スマートフォンの電池状態を同期しています。 | Syncing the phone battery state. |
| Mobileしきい値未保存 | しきい値を変更しました。「しきい値を保存」を選ぶと反映されます。 | Threshold changed. Select Save threshold to apply it. |
| Wear未確定 | まだ保存されていません | Not saved yet |
| 競合 | スマートフォンで設定が変更されました | Setting changed on phone |
| 満充電通知 | 満充電になったら通知 | Notify when fully charged |
| 満充電 | 充電が完了しました | Fully charged |

BN-002では要求payloadと結果payloadに完成済み文言を含めず、Wearが現在のLocaleで編集、送信中、適用済み、未保存、競合、拒否、再試行の文言を組み立てる。

BN-004の満充電イベントおよび設定要求payloadにも完成済み文言を含めず、Mobile/Wearが各端末の現在Localeで通知と設定状態を組み立てる。

- 日本語は簡潔な「です・ます」調を基本とする。
- 英語はsentence caseを使用する。
- `%`は数値formatで扱い、Locale依存の不要な小数を表示しない。
- Mobile通知しきい値のスライダー直下には数値目盛りを表示せず、操作ボタンの読み上げには既存の日英リソースを使用する。
- 時刻は端末の12/24時間設定、日付はLocaleへ従う。
- 「接続なし」と「同期が古い」を同じ文言にしない。

## 5. 文字長とレイアウト

- 擬似ローカライズで英語文字列が30～40%長くなっても操作不能にならないことを確認する。
- Wearの主要値は固定幅へ押し込まず、スケールとスクロールで吸収する。
- コンプリケーション表示は`68%`、`68%!`など最短表現にし、詳細はcontent descriptionへ置く。
- コンプリケーションの状態別Material Symbolは翻訳しない。content descriptionでは対象がスマートフォンであること、百分率、充電状態、鮮度、監視状態を現在Localeで組み立てる。
- コンプリケーションの可視titleには日英とも充電状態文字列を設定しない。content description内の「充電中」/`Charging`はアクセシビリティ情報として維持する。
- ボタンは必要なら2行を許容し、文字切れのために極端に縮小しない。

## 6. アプリ言語

- Androidのアプリ単位言語設定に対応する場合はAndroidX AppCompat/Locale APIの採用を別作業で決める。
- v1.0の最低受け入れはOS/アプリLocale変更へ正しく追従すること。
- Locale変更時、通知channelの既存名称はOS側に残る場合があるため、channel再作成時の挙動を確認する。

## 7. テスト

- 日英で全画面のスクリーンショットテストを行う。
- MobileとWearのLocaleが異なる組合せを試す。
- 通知、Tile、コンプリケーション、TalkBack読み上げを日英で確認する。
- フォント最大、疑似ローカライズ、24時間/12時間表記を確認する。
- 全Localeでformat引数の型と個数が一致することをLintで確認する。
