# テスト計画書・テストケース

文書ID: TPC-001  
版: 0.8
状態: Draft  
最終更新: 2026-08-16

## 1. 目的

電池しきい値判定、バックグラウンド監視、Mobile/Wear同期、通知、表示、Mobile広告と同意、release最適化、再起動、異常系、必須端末互換性を検証し、v1.0のリリース可否を判断する。

## 2. テストレベル

| レベル | 対象 | 主な手段 |
|---|---|---|
| JVM単体 | domain rule、validator、mapper、freshness、順序 | JUnit、parameterized/property-based test |
| Android単体/統合 | DataStore、Receiver adapter、notification builder、Repository | Robolectricまたはinstrumented test |
| UI | Mobile/Wear Compose、Fold、文字列、アクセシビリティ | Compose UI test、screenshot test |
| Contract | Mobile senderとWear receiverのDataMap契約 | 共通fixture、境界値、旧schema |
| Emulator E2E | OS別、切断、再起動、権限 | Android/Wear emulator、ADB |
| 実機E2E | Pixel 10 Pro Fold + Pixel Watch 4 | 手動および実機ログ |
| 非機能 | 消費、遅延、安定性 | Battery Historian/Android Studio Profiler、時刻計測 |

## 3. 役割

### 仕様担当

- 同期データ形式、接続/切断/再接続、Stale表示、受け入れ条件を確定する。
- 未確定仕様があるテストを開始可能と偽らない。

### 実装担当

- Data Layer送信、Wear受信、DataStore、domain単体テストを実装する。
- テスト用fake clock、fake battery source、fake notifier、fixture builderを提供する。

### レビュー担当

- タイムスタンプ、連続更新競合、再接続、DataItem path、Android lifecycleを重点確認する。
- テストが実装詳細ではなく受け入れ結果を検証しているか確認する。

### テスト担当

- 通常同期、切断中更新、再接続、両端末再起動、Stale、連続更新、異常データを実施する。
- OS版、端末、権限、時刻、接続条件と証跡を記録する。

### 人間

- Pixel 10 Pro FoldとPixel Watch 4実機で確認する。
- バッテリー消費、通知遅延、振動/音、ウォッチフェイス表示を評価する。
- FGSのユーザー体験と配布ポリシーを含め最終承認する。

## 4. テスト環境

| ID | Mobile | Wear | 用途 |
|---|---|---|---|
| ENV-01 | API 33 emulator | API 30+ Wear emulator | 最低SDK回帰 |
| ENV-02 | API 34 emulator | Wear OS 4/5 emulator | FGS/permission |
| ENV-03 | API 35 emulator | Wear emulator | boot/FGS回帰 |
| ENV-04 | API 36 foldable emulator | Wear OS 6 emulator 41/45mm | target 36、adaptive UI |
| ENV-05 | Pixel 10 Pro Fold実機 | Pixel Watch 4 41mm実機 | 必須実機。入手構成に応じる |
| ENV-06 | Pixel 10 Pro Fold実機 | Pixel Watch 4 45mm実機 | 必須実機。入手構成に応じる |

41mm/45mm両方の実機を用意できない場合、未実施を明記し、片方をエミュレーターだけで代替して最終合格にしない。

## 5. テストデータと観測

- domain testでは任意の`BatterySnapshot`を注入する。
- E2EではADBの電池状態変更機能を使える環境と、実放電の両方で確認する。
- fake clockでFresh境界`2:00`、`2:00.001`、Stale境界`5:00`、`5:00.001`を検証する。
- DataMap fixtureは最小/最大、欠落、型違い、未知key、旧/未来schema、順不同を用意する。
- 証跡にはbuild SHA、application ID、署名種別、OS build、時刻、Mobile/Wearログ分類、スクリーンショットを含める。
- release buildでは機密値をログに出さず、テスト用debug診断画面からsequenceと結果を確認する。

## 6. 単体テストケース

| ID | 対象 | 入力/操作 | 期待結果 |
|---|---|---|---|
| TC-U001 | 残量計算 | level=1, scale=3 | 33%へ正規化 |
| TC-U002 | 入力検証 | scale=0、level=-1、項目欠落 | invalidとして破棄、クラッシュなし |
| TC-U003 | 下降交差 | threshold=20、21→20 | eventを1件生成、disarm |
| TC-U004 | 非交差 | 20→19、19→20、20→21 | eventなし |
| TC-U005 | ジャンプ下降 | 25→18 | eventを1件生成 |
| TC-U006 | 初回低残量 | 初回18、threshold=20、既定設定 | eventなし、disarm |
| TC-U007 | ヒステリシス | 通知後21→20（hysteresis=2） | rearmせずeventなし |
| TC-U008 | 再アーム | 通知後22→21→20 | 22でarm、20で新event1件 |
| TC-U009 | 充電中 | armed、21→20、charging=true | eventなし、状態更新 |
| TC-U010 | 設定変更 | level=18でthreshold 15→20 | 変更だけではeventなし |
| TC-U011 | 順序 | stored sequence=10、受信9/10/11 | 9/10破棄、11採用 |
| TC-U012 | Freshness | 2分、2分超、5分超 | Fresh、Delayed、Stale |
| TC-U013 | event期限 | 期限直前/同時/超過 | 仕様どおり有効境界、超過は通知なし |
| TC-U014 | event重複 | 同じeventIdを10回 | 処理は1回 |
| TC-U015 | state validator | level=-1/101、threshold=4/101 | 全体を拒否 |
| TC-U016 | schema | v1、未来v2 | v1採用、v2をunsupportedとして保持値維持 |
| TC-U017 | 時刻 | capturedAtが受信より6分未来 | 異常として拒否/時刻警告 |
| TC-U018 | Locale | ja/enで同じevent数値 | 各Localeの文言、payloadに文言なし |
| TC-U019 | Wear通知再試行予約 | `RESERVED_FAILED`、attempt=1、期限内 | 原子的に`PENDING`、attempt=2となる |
| TC-U020 | Wear通知上限 | `RESERVED_FAILED`、attempt=3 | `FAILED_EXHAUSTED`となりadapterを呼ばない |
| TC-U021 | Wear通知再試行期限 | `RESERVED_FAILED`、attempt=1、`now > expiresAt` | `EXPIRED`となりadapterを呼ばない |
| TC-U022 | Wear通知再試行競合 | 同じeventへ並行して10回予約 | 予約成功は1回、同じ試行番号を重複しない |
| TC-U023 | 100%しきい値境界 | threshold=100、初回100→99→98→100→99 | 初回100はarmedでeventなし、最初の99で1件、98で重複なし、100で再arm、次の99で新event1件 |
| TC-U024 | 100%しきい値初回低値 | threshold=100、初回99 | eventなし、disarm |
| TC-U025 | backup exclusion | Mobile/Wearのbackup/data-extraction rules | 両Proto DataStoreファイルがcloud backupとdevice transferの双方から除外される |
| TC-U026 | Wearしきい値要求validator | 正常、範囲4/101、UUID不正、key欠落、型違い、未来schema | 正常だけを型付き要求へ変換し、異常payloadは全体拒否 |
| TC-U027 | Wearしきい値要求適用 | Mobile=20、request desired=15 / expected=20 | Mobileが15を保存し、設定変更だけではeventを生成せず、結果と新しいstate sequenceを原子的に確定 |
| TC-U028 | Wearしきい値競合 | Mobile=25、request desired=15 / expected=20 | `CONFLICT`、有効値25、設定・alert state・sequenceを変更しない |
| TC-U029 | Wearしきい値要求冪等性 | 同じ`requestId`を10回、最初の結果送信を失敗 | 設定適用とstate sequence割当は1回、保存済み同一結果を再送 |
| TC-U030 | Wear未確定要求復元 | 下書きと未確定`requestId`を保存してprocess再生成 | 下書きと状態を復元するが自動送信せず、明示再試行だけが同じ要求を送る |
| TC-U031 | Wearしきい値増減調整 | current=5/20/100、減少・増加操作 | 1操作ごとに範囲内で-1/+1となり、5未満・100超へ進まない |
| TC-U032 | 満充電到達 | enabled、monitoring、charging、99→100 | FullChargeReachedEventを1件生成し、同一sessionをdisarm |
| TC-U033 | 満充電重複/再arm | 同一sessionで100→99→100、unplug、次session 80→100 | 同一session追加0件、非充電を挟んだ次sessionで1件 |
| TC-U034 | 満充電境界 | 初回100、設定ON時100、OFF、監視停止、非充電100 | eventなし。設定ON時100は次sessionまでdisarm |
| TC-U035 | Wear満充電設定要求 | 正常、競合、同一requestId再試行、欠落/型違い/未来schema | 正常だけMobileが原子的に適用し、競合/冪等/全体拒否が契約どおり |
| TC-U036 | Mobile手動同期表示 | `IDLE`、`SYNCING`、成功、各エラー状態を表示モデルへ変換 | `SYNCING`だけ操作無効、進捗表示、同期中操作文言、専用状態文言となり、Idleの操作・状態文言を使用しない |
| TC-U037 | Mobileしきい値増減境界 | current=5/20/100、減少・増加操作 | 1操作ごとに範囲内で-1/+1となり、5未満・100超へ進まず、操作だけでは保存処理を呼ばない |
| TC-U038 | Mobileしきい値下書き状態 | saved=20でdraftを20→21→20と変更 | 21では未保存、20へ戻すと保存済み表示へ戻り、編集だけでは永続化・同期しない |
| TC-U039 | コンプリケーション状態アイコン | 21%非充電、20%非充電、0%非充電、20%充電、100%充電 | `battery_full`、`battery_alert`、`battery_alert`、`battery_charging_full`、`battery_charging_full`を選択し、百分率は入力値を維持する |
| TC-U040 | コンプリケーション型データ | 通常、充電、低残量、Staleを`SHORT_TEXT`、`RANGED_VALUE`、`LONG_TEXT`で構築 | 全型が百分率text、状態別monochromatic image、content descriptionを保持し、Fresh充電中の表示用titleはnull、content descriptionは充電状態を保持する。Staleのdescriptionは動的な更新経過を維持する |
| TC-U041 | コンプリケーションprovider manifest | Wearのsource manifestを解析 | `MainComplicationService`がlabel、`BIND_COMPLICATION_PROVIDER`、3対応型に加えて`android:icon="@drawable/ic_complication_provider_app_24"`を宣言する |
| TC-U042 | コンプリケーションprovider vector | provider vectorとlauncher monochrome vectorを解析 | providerは24dp、108×108 viewport、単一path、白fillで、pathDataがlauncher monochrome layerと一致する |
| TC-U043 | Stale記号の画面説明contract | Mobile/Wearの日英resourceと画面sourceを解析 | 両画面が末尾で同一条件（5分超、時刻ずれ、値が古い可能性）を説明し、Mobileは`今すぐ同期 / Sync now`、Wearは`同期を再試行 / Retry sync`へ案内する。Kotlinへ文言を直書きしない |
| TC-U044 | AdMob build variant contract | Mobile Gradle、manifest、BuildConfig設定を解析 | debugはGoogle demo application/banner IDだけ、releaseは指定production application/banner IDだけを組み込み、両variantでID種別が混在しない |
| TC-U045 | 広告要求gate | 同意不可、同意可能、初期化中、初期化完了、再通知を純粋stateへ入力 | `canRequestAds=true`後に初期化を最大1回開始し、初期化完了後だけbanner表示可能。falseへの変更で表示不可となり、重複callbackでも初期化を重複しない |
| TC-U046 | AdMob Mobile-only/privacy contract | Mobile/Wear依存、manifest、日英resource、sourceを解析 | Ads/UMP依存とapplication IDはMobileだけ、privacy optionsの日英文字列が揃い、広告ID・クリック・同意文字列のDataStore/Data Layer/log実装を追加していない |
| TC-U047 | Release optimization contract | Mobile/Wearのapp build scriptとproject Gradle propertiesを解析 | 両releaseでminifyとresource shrink、最適化済み既定ルール、AGP 8.13最適化resource shrinkが有効で、full mode無効化と不要な広域keep ruleがない |

## 7. 統合・E2Eテストケース

### 7.1 通常同期

| ID | 前提・操作 | 期待結果 | 対応 |
|---|---|---|---|
| TC-E001 | 両アプリ接続、Mobile 67%非充電を送信 | Wear画面/Tile/Complicationが67%、Fresh | FR-030, FR-034, FR-050 |
| TC-E002 | 21→20%、threshold=20 | Mobile/Wear各1通知、値20% | AC-002 |
| TC-E003 | 充電開始 | 両画面とComplicationに充電状態、低残量eventなし | FR-016, FR-051 |
| TC-E004 | しきい値20→15を保存 | Mobile保存、Wear表示15、通知なし | FR-006 |

### 7.2 切断中の更新と再接続

| ID | 前提・操作 | 期待結果 | 対応 |
|---|---|---|---|
| TC-E010 | 接続後Bluetooth/Wi-Fiを切り、70→65→60 | Mobileは60を保持、Wearは時間経過でStale | AC-005 |
| TC-E011 | TC-E010から再接続 | Wearは中間値で止まらず60へ収束、Fresh | AC-006 |
| TC-E012 | 切断中21→20、2分以内に再接続 | Mobile即時通知、Wearは期限内に1通知 | FR-040 |
| TC-E013 | 切断中21→20、6分後に再接続 | Mobileのみ通知、Wearは最新状態表示のみ | AC-007 |
| TC-E014 | DataItem送信Taskを意図的に逆順完了 | WearとlastSyncedは最大sequence | FR-062 |

### 7.3 再起動・プロセス

| ID | 前提・操作 | 期待結果 | 対応 |
|---|---|---|---|
| TC-E020 | 監視ONでMobile通常再起動 | OS許可範囲で監視復旧。失敗時はresumeRequiredを正しく表示 | UC-007 |
| TC-E021 | 監視OFFでMobile再起動 | FGSを開始しない | FR-003 |
| TC-E022 | Wear再起動 | 保存値を読み、経過時間に応じStale。更新後Fresh | UC-007 |
| TC-E023 | Mobile process kill（強制停止ではない） | Service/OS挙動後に状態復元、重複通知なし | NFR-011 |
| TC-E024 | ユーザーがアプリを強制停止 | 監視不能を仕様どおり扱い、再起動したと偽らない | NBS-001 |
| TC-E025 | Mobile/Wearアプリ更新 | 設定保持、schema v1読込、過去event重複なし | FR-066 |

### 7.4 古いデータ表示

| ID | 前提・操作 | 期待結果 | 対応 |
|---|---|---|---|
| TC-E030 | 最終受信から2分以内 | Fresh | FR-031 |
| TC-E031 | 2分超5分以内 | Delayedと相対時刻 | FR-031 |
| TC-E032 | 5分超 | 値維持+Stale+最終更新+再試行 | AC-005 |
| TC-E033 | 一度も受信なし | `--%`、接続ヘルプ、NoData complication | FR-033, FR-052 |
| TC-E034 | 端末時刻を前後へ変更 | クラッシュやFresh誤固定がなく、疑わしい時刻を表示 | Review項目 |

### 7.5 連続更新・競合

| ID | 前提・操作 | 期待結果 | 対応 |
|---|---|---|---|
| TC-E040 | 100ms間隔で30件、sequence 1..30 | Wear最終値は30のpayload、ANRなし | FR-018, FR-062 |
| TC-E041 | 同じevent callbackを並行10回 | Wear通知1件 | FR-041 |
| TC-E042 | しきい値sliderを連続操作後保存 | 保存値だけを同期し、送信stormなし | UI仕様 |
| TC-E043 | DataItem A/Bを順不同受信 | 最大sequenceのみ保存 | Review項目 |

### 7.6 異常データ

| ID | 入力 | 期待結果 | 対応 |
|---|---|---|---|
| TC-E050 | 未知path | 無視 | FR-065 |
| TC-E051 | 必須key欠落/型違い | 全体拒否、直前正常値維持 | AC-008 |
| TC-E052 | level=-1/101 | 全体拒否、診断count増加 | AC-008 |
| TC-E053 | 未来schemaVersion | 更新案内、クラッシュなし | FR-066 |
| TC-E054 | eventId 1,000文字/非UUID | event拒否、通知なし | Data設計 |
| TC-E055 | expiresAt < occurredAt | event拒否、通知なし | Data設計 |
| TC-E056 | DataStore破損fixture | corruption policyに従い安全に回復、重複通知なし | ADR-003 |

### 7.7 権限と通知

| ID | 操作 | 期待結果 |
|---|---|---|
| TC-E060 | Mobile通知許可、Wear許可 | 各1件 |
| TC-E061 | Mobile拒否、Wear許可 | Wearのみ、Mobileに案内 |
| TC-E062 | Mobile許可、Wear拒否 | Mobileのみ、Wearに案内 |
| TC-E063 | 両方拒否 | 通知なし、状態/イベントは更新 |
| TC-E064 | battery_alerts channelを無効化 | クラッシュせず、画面に通知不可を表示 |
| TC-E065 | ongoing通知の停止をタップ | FGS停止、監視OFF、Wearへ反映 |
| TC-E066 | Mobile notification bridgeを観測 | Mobile通知の自動ミラー重複なし |
| TC-E067 | Wear初回投稿を失敗させ、期限内にアプリをforeground化 | attempt=2で再試行し、成功時のWear通知は1件 |
| TC-E068 | Wear投稿を3回とも失敗させ、その後foreground化と再試行操作 | `FAILED_EXHAUSTED`を表示し、4回目を投稿しない |
| TC-E069 | Wear投稿で権限拒否後に権限を許可してアプリ再開 | 過去eventを再投稿せず、設定状態を更新する |

### 7.8 UI・多言語・互換性

| ID | 操作 | 期待結果 |
|---|---|---|
| TC-E070 | Pixel Fold外側→内側→外側 | 入力、監視状態、navigationを保持、clipなし |
| TC-E071 | 分割画面幅を連続変更 | Window Size Classへ追従、操作可能 |
| TC-E072 | Pixel Watch 4 41/45mm | 主要情報、スクロール、操作が欠けない |
| TC-E073 | 日英切替 | 全画面・通知・Tile・Complication labelが切替 |
| TC-E074 | Mobile=ja、Wear=en | 各端末のLocaleで表示/通知 |
| TC-E075 | TalkBack + 最大フォント | 意味順に読み上げ、色のみ依存なし、操作可能 |
| TC-E076 | 対応/非対応watch face | 対応slotで選択可、非対応faceは正常に対象外 |
| TC-E077 | Wearで20→15を保存 | Mobileが15を永続化・再評価し、phone-state受信後にWearが適用済み15を表示。設定変更だけの通知なし |
| TC-E078 | Wear編集中にMobileで20→25、その後Wearがexpected=20で15を保存 | Mobileは25を維持し、Wearへ競合と有効値25を表示 |
| TC-E079 | Wear切断中に15を保存操作 | Mobileは変更されず、Wearは下書きと未保存表示を保持。再接続だけでは送信しない |
| TC-E080 | TC-E079後に再接続して明示的に再試行 | 同じ`requestId`を送信し、Mobile適用後にWearが15へ収束 |
| TC-E081 | Mobile適用後にresult messageだけを破棄し、Wearで再試行 | Mobileの設定・sequence・通知eventは重複せず、保存済み結果が返る |
| TC-E082 | Wearを要求送信中に再起動 | 下書きと未確定状態を復元し、自動送信しない。再試行または破棄が可能 |
| TC-E083 | Pixel Watch 4 41/45mm、最大フォント、日英、TalkBackで非セグメント型スライダーの減少・増加操作を使い5/100境界を編集し、リューズで画面をスクロール | 値、範囲、減少、増加、保存、未保存/競合状態が欠けず意味順に読み上げられ、1%刻みで範囲外へ進まない。リューズでは下書き値が変わらず画面をスクロールできる |
| TC-E084 | Wearで20%からInlineSliderの減少操作を連続して15%にし、保存前後の要求を観測 | 保存前はWear下書きだけが15%となり変更要求は0件。保存後は最終値15%の要求だけが1件送信される |
| TC-E085 | Mobileで満充電通知をON、充電中99→100 | Mobile/Wear各1件の満充電通知、phone-stateはON、低残量通知との誤分類なし |
| TC-E086 | TC-E085と同じ充電中に100→99→100、その後unplugして80から再充電→100 | 同じ充電sessionの追加通知なし。次sessionで各端末1件 |
| TC-E087 | Mobile/WearそれぞれからON/OFF変更後に両アプリを再起動 | Mobile Protoの確定値へ収束し、再起動だけで未確定要求や通知を自動送信しない |
| TC-E088 | Wear切断中に満充電通知を切替、再接続、その後ユーザーが再操作 | 再接続だけではMobile設定不変。再操作後にphone-stateへ収束 |
| TC-E089 | Wear期待値OFF中にMobileをONへ変更後、Wearが切替要求 | Mobile有効値を維持して現在のphone-stateを再送し、設定、sequence、満充電eventを重複させない |
| TC-E090 | Mobileのスマートフォン電池同期欄を日英で表示し、「今すぐ同期」を押して送信完了を遅延させる | ボタン付近に通常は自動同期され、この操作は自動同期されない場合または最新状態への手動更新に使う説明を日英で表示する。押下後は直ちにボタンが無効化され、進捗表示、同期中操作文言、専用状態文言が500ms以上かつ完了まで表示される。処理中に「今すぐ同期 / Sync now」「同期できます。 / Ready to sync.」を表示せず、結果確定後だけ結果表示へ移る |
| TC-E091 | Mobile/WearのAdaptive Iconを円・角丸四角・スクワークルmaskでpreview | すべてのmaskでバッテリー本体と通知バッジが欠けず、両アプリが同一図柄である |
| TC-E092 | Android 13以降でthemed iconを有効化し、Mobile/Wearのmonochrome表示を確認 | launcherのtintで単色表示され、バッテリーと通知バッジのシルエットを判別できる |
| TC-E093 | mdpiからxxxhdpiまでの通常・round legacy iconを48px相当を含む表示で確認 | テンプレート図柄が残らず、文字や細部の潰れがなく、Battery Notifier図柄を識別できる |
| TC-E094 | Pixel 10 Pro Foldの外側/内側/分割画面でMobile通知しきい値欄を日英・最大フォント・TalkBackで表示し、5/20/100から左右ボタンとスライダーを操作 | 減少ボタン・スライダー・増加ボタンが同じ行に並び、スライダー直下に数値目盛りが表示されない。各ボタンは48dp以上で意味順に読み上げられ、全操作が1%刻みで範囲外へ進まない。20→21は未保存と表示され、保存前の永続化・同期は0件、保存後は最終値21だけが1件反映される |
| TC-E095 | WFF v1～5の検証用フェイスで`SHORT_TEXT`、`RANGED_VALUE`、`LONG_TEXT`スロットへ通常、充電中、低残量、Stale、No Dataを要求 | 対応型が仕様どおりの百分率、状態別単色アイコン、content descriptionを受け取り、各WFFの定義した配置とtintで描画される。第三者フェイスでの同一配置は合否対象外 |
| TC-E096 | Pixel Watch 4 41/45mmで対応WFFフェイスへコンプリケーションを設定し、通常表示/AOD、日英、TalkBackで21%非充電、20%非充電、20%充電を確認 | 通常、低残量、充電中のGoogle公式Material Symbolと百分率を識別でき、20%充電では充電中が優先される。充電中の可視文字列は表示されず、対象がスマートフォンで充電中であることを読み上げ、tintとambient表示で欠損しない |
| TC-E097 | Pixel Watch 4 41/45mmの対応ウォッチフェイスでコンプリケーション追加画面を開き、データソース一覧からBattery Notifierを探す | Battery Notifierのlabelとアプリアイコンと同じシルエットの単色アイコンが表示され、選択後はスロット内で状態別アイコンと百分率が表示される |
| TC-E098 | Pixel 10 Pro Fold外側/内側/分割画面とPixel Watch 4 41/45mmでMobile/Wear画面末尾までスクロールし、日英・最大フォント・TalkBackでStale記号の説明を確認 | 両端末で説明が常時到達可能かつ欠けず、`!`の意味、5分超・時刻ずれ、接続・時刻確認、各端末の手動同期操作を理解できる順序で読み上げる |
| TC-E099 | debug Mobileを起動して広告要求可能状態にする | Google demo bannerだけが最下部に1枠表示され、Test Ad表示を確認できる。production IDへのrequestとproduction広告のclickは0件 |
| TC-E100 | UMP debug geography/test deviceで初回、同意、拒否、dismiss、通信失敗を再現 | 必要なformを先に表示し、`canRequestAds=false`ではSDK初期化・広告request・専用余白0件。本来の監視・設定・同期・通知は操作可能 |
| TC-E101 | Pixel 10 Pro Fold外側/内側/分割画面、日英、最大フォント、TalkBackでdebug demo bannerを表示して幅を変更 | bannerは現在幅に適応して最下部に1枠だけ表示され、system navigationと全スクロール内容・主要操作を覆わず、広告と本来コンテンツを区別できる |
| TC-E102 | UMPがprivacy optionsをrequiredと返す状態で日英画面の操作を実行し、選択を変更 | 日英の導線が欠けず、UMP formを再表示できる。変更後に`canRequestAds`を再評価し、falseなら表示中AdViewを破棄して広告余白を除去する |
| TC-E103 | release APK/AABをオフラインで解析し、実広告要求は行わない | merged manifestは指定production application ID、BuildConfigは指定production banner IDを保持し、debug demo IDとの混在、WearへのAds/UMP依存、アプリ独自の広告ID保存/ログ/Data Layer送信がない |

### 7.9 Release最適化

| ID | 前提・操作 | 期待結果 |
|---|---|---|
| TC-E104 | Mobile/WearでJVM test、lint、assembleRelease、bundleReleaseを順次実行し、mappingとAAB metadataを検査 | 両プロジェクトの全コマンドが成功し、各releaseに非空mapping.txtがあり、各AABが対応するR8 mapping metadataを含む |
| TC-E105 | 最適化済みrelease候補をPixel 10 Pro FoldとPixel Watch 4へ導入し、起動、設定永続化、監視、手動/自動同期、通知、Wear画面/Tile/Complication、Mobile UMP/広告gateを確認 | クラッシュ、ANR、Android entry pointやresourceの欠落、同期/通知回帰がなく、production広告を要求・clickせずにHumanがrelease候補を承認できる |

## 8. 非機能テスト

### バッテリー消費

1. Pixel 10 Pro Foldを同じOS build、通信、画面、利用条件にする。
2. 監視OFFで24時間の消費を記録する。
3. 満充電・同等条件で監視ONを24時間記録する。
4. FGS CPU時間、wakeups、Data Layer送信回数、追加消費ポイントを比較する。
5. 目安3ポイント未満を初期判定とし、ばらつきを考慮して人間が承認する。

### 通知・同期遅延

- 接続中のしきい値到達時刻、Mobile通知時刻、Wear受信/通知時刻を30回計測する。
- Wear通知の95パーセンタイル60秒以内を目標とする。
- Bluetooth、Wi-Fi、画面OFF、Doze相当で分けて記録する。
- 絶対保証ではなく、外れ値と原因を記録して人間が許容可否を判断する。

### 安定性

- 8時間監視、100回の状態更新、10回の接続切替、両端末各5回再起動を行う。
- crash、ANR、通知重複、DataStore例外、FGS例外が0件であること。

## 9. 合否基準

- Must要件に紐づくテストがすべてPass。
- Pixel 10 Pro Fold + Pixel Watch 4実機の必須シナリオがPass。
- Critical/High不具合0件。
- debugでGoogle demo広告を確認し、production成果物は静的解析だけでIDを確認する。自動/開発者試験でproduction広告を要求・クリックしない。
- Medium不具合は回避策、影響、期限を人間が承認。
- バッテリー消費と通知遅延を人間が承認。
- production配布時はAdMob同意メッセージ、対象年齢、privacy policy、Data safety、広告申告を人間が承認。
- テスト証跡と`state.yaml`が更新されている。

## 10. 中止・再開基準

- application ID/署名不一致、FGS開始不能、DataStore破損で状態喪失、再現性ある通知重複が見つかった場合はE2Eを中止する。
- 原因修正、影響範囲の単体/統合テスト追加、レビュー完了後に該当環境から再開する。
- 未実施をPassとして扱わず、`blocked`または`not_run`で記録する。
