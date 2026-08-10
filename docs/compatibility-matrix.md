# 互換性マトリクス

文書ID: CMX-001  
版: 0.2
状態: Draft  
基準日: 2026-08-10

## 1. 対応方針

必須認証端末はGoogle Pixel 10 Pro FoldとGoogle Pixel Watch 4である。エミュレーター合格だけでは必須端末対応とみなさず、リリース候補を同時にインストールした実機組合せで最終確認する。

## 2. Mobile OS

現在のMobileプロジェクト設定は`minSdk 33 / targetSdk 36`である。

| Android | API | 対応 | 試験レベル | 主な確認 |
|---|---:|---|---|---|
| Android 13 | 33 | Yes | Emulator/代表実機 | 通知runtime permission |
| Android 14 | 34 | Yes | Emulator | FGS type/permission、バックグラウンド制限 |
| Android 15 | 35 | Yes | Emulator | boot/FGS制限、edge-to-edge |
| Android 16 | 36 | Yes、基準 | Pixel 10 Pro Fold実機 | target 36、adaptive UI、通知、再起動 |
| Android 17以降 | 未確定 | 未保証 | リリース時評価 | behavior changes |

## 3. Wear OS

現在のWearプロジェクト設定は`minSdk 30 / targetSdk 36`である。

| Wear OS/API | 対応 | 試験レベル | 主な確認 |
|---|---|---|---|
| API 30相当以上 | Yes | Wear Emulator | 最低SDK、基本画面、DataStore |
| Wear OS 4/5 | Yes想定 | Emulator | Data Layer、Tile、Complication |
| Wear OS 6 | Yes、発売時基準 | Pixel Watch 4実機またはsystem image | 41/45mm、通知、Complication |
| Wear OS 7（提供済み端末） | Yes、現行確認 | 最新化したPixel Watch 4実機 | 更新後の回帰、target 36挙動 |
| 将来版 | 未保証 | リリース時評価 | behavior changes |

Pixel Watch 4は発売時Wear OS 6.0で、41mm/45mmがある。2026年のOS更新状況を考慮し、入手実機の最新安定版でも回帰試験する。

## 4. 必須端末

| 端末 | 構成 | 必須試験 |
|---|---|---|
| Google Pixel 10 Pro Fold | 外側6.4インチ、内側8インチ、Android 16以降 | 折りたたみ/展開、分割画面、監視FGS、通知、再起動 |
| Google Pixel Watch 4 41mm | Wear OS 6以降 | 小画面、フォント最大、Tile、通知、SHORT_TEXT/RANGED_VALUE/LONG_TEXT |
| Google Pixel Watch 4 45mm | Wear OS 6以降 | レイアウト、Tile、通知、Complication |
| 上記Mobile + Watch | Bluetooth、Wi-Fi/クラウド経路、切断 | 通常同期、切断、再接続、両端末再起動、期限切れイベント |

## 5. 画面・UIマトリクス

| Mobile状態 | Compact | Medium | Expanded | Fold変化中 |
|---|---:|---:|---:|---:|
| 外側画面 | Must | - | - | Must |
| 内側全画面 | - | Must | Must | Must |
| 分割画面/フリーフォーム | Must | Must | 条件次第 | Must |
| フォント100% | Must | Must | Must | Must |
| フォント最大/表示拡大 | Must | Must | Must | Must |

物理端末名でレイアウトを分岐せずWindow Size Classへ追従する。Android 16 targetの大画面挙動を前提に、orientationやaspect ratio固定に依存しない。

## 6. 接続マトリクス

| Mobile | Wear | 期待結果 |
|---|---|---|
| Bluetooth接続 | 到達可能 | urgent状態・イベント同期 |
| Bluetoothなし、ネットワークあり | 利用可能な経路あり | Data Layer経由で最終的に同期。遅延を許容 |
| 機内モード/完全切断 | 切断 | Mobile監視継続、WearはStale |
| 再接続 | 再到達 | 現在値再取得、最新sequenceへ収束 |
| Wearアプリなし | node非対応 | Mobile単体通知・監視を継続 |
| Google Play services利用不可 | API失敗 | Mobile単体継続、診断表示、クラッシュなし |

## 7. ウォッチフェイス互換性

| フェイス能力 | 結果 |
|---|---|
| SHORT_TEXTスロット | 対応 |
| RANGED_VALUEスロット | 対応 |
| LONG_TEXTスロット | 対応 |
| WFF v1～5の上記互換スロット | 同じ百分率・状態別単色アイコン・content descriptionフィールドを提供 |
| コンプリケーション対応だが上記3型なし | 非対応型としてNoDataまたはpicker非表示 |
| コンプリケーション非対応 | 表示不可。仕様上正常 |
| サードパーティ製フェイス | 対応型の任意フィールド採否・配置・tintはフェイス実装に依存。同一表示は保証せず、代表1種を参考試験 |

WFF互換性はデータソースが上記3型の仕様化されたフィールドを提供する範囲を指す。アプリがすべてのウォッチフェイスへ表示を強制することや、第三者フェイスを含む全フェイスで同じ配置・色・アイコン表示になることは対象外とする。

## 8. 配布・署名互換性

- Mobile/Wearのapplication IDと署名が同一でなければData Layer連携は非対応。
- debug同士、release同士で組み合わせる。debug/release混在を正式対応しない。
- 共通application IDは`com.magicitengineer.batterynotifier`とし、Kotlin namespaceは端末別に維持する。
- 同一Play Store掲載でMobileは偶数、Wearは奇数の重複しないversionCode系列を使い、同期schemaの互換範囲を明示する。
- debugは同一debug署名、releaseは同一Play App Signing証明書系列を使用し、署名秘密情報をリポジトリへ保存しない。

## 9. 公式情報

- [Pixel 10 Pro Fold specifications](https://store.google.com/product/pixel_10_pro_fold_specs)
- [Pixel Watch 4 specifications](https://store.google.com/product/pixel_watch_4_specs)
- [Pixel Watch compatibility requirements](https://support.google.com/googlepixelwatch/answer/12652073?hl=ja)
- [Android 16 behavior changes](https://developer.android.com/about/versions/16/behavior-changes-16)
- [Build adaptive apps](https://developer.android.com/develop/ui/compose/build-adaptive-apps)

端末・OS情報は変化するため、各リリース候補で公式ページと実機のOS buildを再確認する。
