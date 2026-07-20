# コンプリケーション仕様書

文書ID: CPS-001  
版: 0.1  
状態: Draft  
最終更新: 2026-07-20

## 1. 目的と制約

対応するウォッチフェイスのコンプリケーションスロットへ、Mobile電池残量を提供する。アプリはデータソースであり、表示位置・フォント・最終描画はウォッチフェイスが制御する。

次は保証できない。

- コンプリケーション非対応ウォッチフェイスへの表示。
- ユーザー操作なしの自動配置。
- すべてのフェイスで同一レイアウト・色・アイコンになること。

## 2. Provider

- `SuspendingComplicationDataSourceService`を使用する。
- Manifestでserviceをexportし、`com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER`で保護する。
- provider icon、label、supported typesを宣言する。
- complication request時はWear DataStoreの保存済み正常値だけを読み、Data Layer通信の完了を待たない。

## 3. 対応型

| 型 | Primary | Secondary | 用途 |
|---|---|---|---|
| `RANGED_VALUE` | 0..100のlevel | `68%`、Phone battery title | ゲージ対応スロット |
| `SHORT_TEXT` | `68%` | Phone icon/短いtitle | 小型・円形スロット |

`LONG_TEXT`と画像専用型はv1.0対象外とする。型が未対応の場合は`NoDataComplicationData`を返す。

## 4. 状態別データ

| 状態 | RANGED_VALUE | SHORT_TEXT | Content description |
|---|---|---|---|
| Fresh | value=level、min=0、max=100 | `68%` | `スマートフォンの電池残量68%`相当 |
| Charging | Fresh + 充電monochromatic image | `68%` + 稲妻icon | 残量と充電中を読む |
| Delayed | 値を維持 | `68%` | 最終更新が遅れていることを含める |
| Stale | 値を維持しwarning icon | `68%!` | 古い可能性と最終更新時刻を含める |
| Monitoring off | 値を維持 | `68%` | 監視停止中を含める |
| No data | placeholder | `--%` placeholder | Mobileデータなし |

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
- RANGED_VALUEとSHORT_TEXTのスロットで正しい値を返す。
- データ未受信、Stale、充電中、監視停止を確認する。
- 値更新後にウォッチフェイスが更新され、タップでW-001が開く。
- 対応しないウォッチフェイスへ強制表示しない。

## 10. 参考

- [About complications](https://developer.android.com/training/wearables/complications)
- [Expose data to complications](https://developer.android.com/training/wearables/complications/exposing-data)

