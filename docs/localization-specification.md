# 多言語対応仕様書

文書ID: L10N-001  
版: 0.1  
状態: Draft  
最終更新: 2026-07-20

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
allow_notifications
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

- 日本語は簡潔な「です・ます」調を基本とする。
- 英語はsentence caseを使用する。
- `%`は数値formatで扱い、Locale依存の不要な小数を表示しない。
- 時刻は端末の12/24時間設定、日付はLocaleへ従う。
- 「接続なし」と「同期が古い」を同じ文言にしない。

## 5. 文字長とレイアウト

- 擬似ローカライズで英語文字列が30～40%長くなっても操作不能にならないことを確認する。
- Wearの主要値は固定幅へ押し込まず、スケールとスクロールで吸収する。
- コンプリケーション表示は`68%`、`68%!`など最短表現にし、詳細はcontent descriptionへ置く。
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

