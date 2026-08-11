# コンプリケーション仕様書

文書ID: CPS-001  
版: 0.3
状態: Approved
最終更新: 2026-08-11

## 1. 目的と制約

対応するウォッチフェイスのコンプリケーションスロットへ、Mobile電池残量を提供する。アプリはデータソースであり、表示位置・フォント・最終描画はウォッチフェイスが制御する。

次は保証できない。

- コンプリケーション非対応ウォッチフェイスへの表示。
- ユーザー操作なしの自動配置。
- すべてのフェイスで同一レイアウト・色・アイコンになること。

互換対象はWatch Face Format v1～5で、後述の対応型を受け付けるユーザー設定可能なスロットとする。同じデータフィールドを提供することを互換性の保証とし、第三者ウォッチフェイスが任意フィールドを省略せず同じ配置で描画することは保証しない。

## 2. Provider

- `SuspendingComplicationDataSourceService`を使用する。
- Manifestでserviceをexportし、`com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER`で保護する。
- provider icon、label、supported typesを宣言する。
- complication request時はWear DataStoreの保存済み正常値だけを読み、Data Layer通信の完了を待たない。

## 3. 対応型

| 型 | Primary | Secondary | 用途 |
|---|---|---|---|
| `RANGED_VALUE` | 0..100のlevel | `68%`、状態別monochromatic image | ゲージ対応スロット |
| `SHORT_TEXT` | `68%` | 状態別monochromatic image | 小型・円形スロット |
| `LONG_TEXT` | `68%` | 状態別monochromatic image | 横長・大きいスロット |

画像専用型は数値と`%`を同時に提供できないため対象外とする。型が未対応の場合は`NoDataComplicationData`を返す。

## 3.1 アイコン資産

| 状態 | 条件 | Google公式Material Symbol |
|---|---|---|
| 充電中 | `isCharging == true` | `battery_charging_full` |
| 低残量 | `isCharging == false && levelPercent <= 20` | `battery_alert` |
| 通常 | 上記以外 | `battery_full` |

- 判定順序は充電中、低残量、通常とし、20%以下で充電中の場合は充電中アイコンを優先する。
- 充電中の表示用title文字列は設定しない。充電状態はアイコンとcontent descriptionで伝え、TalkBack等の読み上げ情報からは削除しない。
- 20%はコンプリケーション表示の固定境界であり、ユーザーが変更できる通知しきい値とは独立する。
- Google公式Material Symbols Roundedの24px geometryをVector DrawableとしてWearアプリへ同梱する。単色コンプリケーション用に色指定だけを固定し、path geometryは変更しない。Material icon library全体への実行時依存は追加しない。
- `MonochromaticImage`の通常画像とambient画像へ同じburn-in safeな単色vectorを設定し、最終tintはウォッチフェイスへ委ねる。

## 4. 状態別データ

| 状態 | 全対応型のtext | 状態別icon | Content description |
|---|---|---|---|
| Fresh通常 | `68%` | `battery_full` | `スマートフォンの電池残量68%`相当 |
| Fresh充電中 | `68%`（充電中titleなし） | `battery_charging_full` | 残量と充電中を読む |
| Fresh低残量 | `20%` | `battery_alert` | 残量と非充電状態を読む |
| Delayed | 値を維持 | 充電/低残量/通常判定を維持 | 最終更新が遅れていることを含める |
| Stale | `68%!` | 充電/低残量/通常判定を維持 | 古い可能性と最終更新時刻を含める |
| Monitoring off | 値を維持 | 充電/低残量/通常判定を維持 | 監視停止中を含める |
| No data | `--%` placeholder | placeholder | Mobileデータなし |

文字数制限が厳しいため、画面表示は短くし、詳細はcontent descriptionとタップ後のWearアプリで示す。

## 5. 鮮度と更新

- Fresh/Delayed/Staleの境界はWear表示仕様と共通にする。
- DataItem受信とDataStore保存が成功した後、`ComplicationDataSourceUpdateRequester.requestUpdateAll()`を呼ぶ。
- しきい値変更、監視ON/OFF、充電状態変更も更新対象とする。
- 定期更新meta-dataへ極端に短い間隔を指定しない。システム要求時に現在時刻から鮮度を再計算する。
- 更新要求は短時間にまとめ、連続DataItemごとに無制限で呼ばない。

## 6. タップ動作

- `tapAction`でWearメイン画面W-001を開く。
- Stale時も同じ画面を開き、再試行を表示する。
- PendingIntentはimmutableかつ明示Intentとする。

## 7. プレビューと設定

- データソース選択画面用preview dataは`68%`のFresh状態とする。
- provider labelを日英で提供する。
- 設定Activityはv1.0では不要。しきい値の正本はMobileに置く。

## 8. アクセシビリティとプライバシー

- content descriptionへ対象が「Phone/Mobile」であることを含め、Watch自身の電池と誤認させない。
- 単色アイコンはウォッチフェイスのtintに適合するリソースを使う。
- 電池残量以外の個人データを返さない。

## 9. 受け入れ条件

- Pixel Watch 4の41mm/45mmで、少なくともGoogle提供の対応ウォッチフェイス1種ずつに追加できる。
- RANGED_VALUE、SHORT_TEXT、LONG_TEXTのスロットで正しい値と状態別アイコンを返す。
- 21%/20%の非充電境界と、20%以下で充電中の充電優先を確認する。
- 全対応型で充電中の表示用titleがなく、content descriptionでは充電中と読み上げられることを確認する。
- WFF v1～5の検証用フェイスで対応型のデータフィールドを参照できることを確認する。
- データ未受信、Stale、充電中、監視停止を確認する。
- 値更新後にウォッチフェイスが更新され、タップでW-001が開く。
- 対応しないウォッチフェイスへ強制表示しない。

## 10. 参考

- [About complications](https://developer.android.com/training/wearables/complications)
- [Expose data to complications](https://developer.android.com/training/wearables/complications/exposing-data)
- [Watch Face Format Complication](https://developer.android.com/reference/wear-os/wff/complication/complication)
- [Material Symbols guide](https://developers.google.com/fonts/docs/material_symbols)

上記公式資料は2026-08-10に確認した。
