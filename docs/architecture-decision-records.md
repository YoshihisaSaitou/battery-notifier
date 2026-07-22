# ADR（アーキテクチャ決定記録）

文書ID: ADR-INDEX  
版: 0.1  
最終更新: 2026-07-22

## 運用

- 状態は`Proposed`、`Accepted`、`Superseded`、`Rejected`のいずれかとする。
- 人間の最終承認前は原則`Proposed`とする。
- 決定を変更するときは過去記録を書き換えず、新ADRから置換対象を参照する。
- 本ファイルは初期設計の一覧である。規模が増えたら`docs/adr/NNNN-*.md`へ分割する。

## ADR-001 Kotlin + Jetpack Composeを採用する

- **状態**: Proposed
- **文脈**: MobileとWearの雛形はKotlin/Composeで作成済みで、Foldと円形画面への適応が必要。
- **決定**: MobileはMaterial 3 Compose、WearはWear Composeを使用する。XML Viewとの二重UIは作らない。
- **理由**: 単方向データフロー、プレビュー、adaptive UI、状態保持を統一できる。
- **結果**: Compose UI testとアクセシビリティ試験が必要。Wear固有部品はMobile Material部品で代用しない。

## ADR-002 簡易DDD + レイヤード構成を採用する

- **状態**: Proposed
- **文脈**: しきい値・再アーム・鮮度・順序にはテスト対象となるルールがある一方、小規模アプリである。
- **決定**: Presentation/Application/Domain/Data-Platformの論理レイヤをパッケージで分離する。各bounded contextをGradleモジュール化しない。
- **理由**: domainの単体テスト性を確保しつつ、ビルドとDIの複雑さを抑える。
- **結果**: Android型をdomainへ持ち込まない。機能増加時に物理モジュール分割を再評価する。

## ADR-003 Proto DataStoreを永続化に使用する

- **状態**: Proposed
- **文脈**: 設定、直近状態、順序、通知重複防止を原子的かつ型安全に保存する必要がある。
- **決定**: Mobile/Wearそれぞれ1つのProto DataStoreをRepository経由で使用する。
- **理由**: 小規模な構造化状態に適し、Coroutine/Flowとtransactional updateを利用できる。履歴DBは不要。
- **代替**: Preferences DataStoreはschemaと型安全性が弱い。Roomはv1.0に過剰。
- **結果**: proto migration、corruption handler、単一instanceを実装する。

## ADR-004 Data LayerのDataItemを同期の主経路にする

- **状態**: Proposed
- **文脈**: 切断中の更新と再接続収束が必要。
- **決定**: 最新状態と直近イベントは固定pathのDataItem/DataMap、即時状態要求だけMessageClientを使用する。
- **理由**: DataItemは切断中も同期対象として保持され、最新状態の収束に適する。Messageだけでは配送保証が弱い。
- **結果**: 各端末にDataStoreが必要。event pathを増殖させず、sequence/eventId/expiryで制御する。

## ADR-005 MobileとWearのapplication ID・署名を統一する

- **状態**: Accepted（2026-07-22、人間承認）
- **文脈**: 現在の雛形はMobileとWearで異なるapplication IDだが、Data Layerは同一package nameと署名を要求する。
- **決定**: 両アプリの`applicationId`を`com.magicitengineer.batterynotifier`へ統一し、Kotlin namespaceは端末別の現状値を維持する。debugは同一debug署名、releaseは同一Play App Signing証明書系列を使う。同一Google Play掲載で配布し、Mobileは偶数、Wearは奇数の重複しない`versionCode`系列を使う。
- **理由**: Data Layerのセキュリティ・到達要件を満たすため。
- **結果**: 現在の開発版データは移行せず再インストールを許容する。署名秘密情報はリポジトリへ保存しない。debug/release混在を正式対応しない。

## ADR-006 継続監視にspecialUse Foreground Serviceを採用する

- **状態**: Accepted（2026-07-22、人間がGoogle Play配布方針と実装を承認）
- **文脈**: 任意しきい値の`ACTION_BATTERY_CHANGED`はManifest Receiverで受信できず、周期WorkManagerでは通知遅延が大きい。
- **決定**: ユーザー開始・停止可能な`specialUse` FGSでReceiverを実行時登録する。ongoing通知を表示する。
- **理由**: アプリが非表示でもユーザーの期待する連続監視を維持できる。
- **代替**: WorkManagerは省電力だが即時性を満たさない。定期Alarm/pollingは不適切。
- **結果**: Play Console申告・動画・ポリシー審査、boot制限試験、24時間消費試験が必要。承認困難なら本ADRを置換する。
- **承認範囲**: `FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE`、specialUse subtype property、停止アクション付きongoing通知、実行時Receiver、再起動・更新後の復旧処理。`POST_NOTIFICATIONS`の宣言・要求はDEC-004の別承認とする。

## ADR-007 Mobile通知とWear通知を明示的に分離する

- **状態**: Proposed
- **文脈**: Androidの通知自動ブリッジとWearローカル通知を併用すると重複し得る。
- **決定**: Mobile通知は`localOnly`、到達イベントをData Layerで送り、Wearが独自にローカル通知する。
- **理由**: eventId、有効期限、権限、Locale、再接続時動作をアプリ側で決定できる。
- **結果**: Wear未インストール時はMobile通知だけになる。自動ブリッジをfallbackにしない。

## ADR-008 Complication Data Sourceとして電池残量を提供する

- **状態**: Proposed
- **文脈**: インストール済みウォッチフェイスで残量を確認したいが、他社フェイスへ直接描画はできない。
- **決定**: `RANGED_VALUE`と`SHORT_TEXT`を返すdata source serviceを実装する。
- **理由**: Wear OSの標準拡張点で、対応する任意のウォッチフェイスから選択できる。
- **結果**: ユーザーによる配置が必要で、非対応フェイスは対象外。Stale/NoDataを型ごとに定義する。

## ADR-009 設定の正本をMobileへ置く

- **状態**: Proposed
- **文脈**: MobileとWearの双方でしきい値を編集すると競合解決が必要になる。
- **決定**: v1.0の設定編集はMobileだけで行い、Wearは読み取り専用とする。
- **理由**: 単一writerにより同期契約とUXを簡潔に保てる。
- **結果**: WearからはMobileで設定する案内を表示する。Watch単体設定は将来ADRとする。

## ADR-010 Window Size Classによるadaptive UIを採用する

- **状態**: Proposed
- **文脈**: Pixel 10 Pro Foldは外側/内側画面、分割表示で利用可能領域が動的に変わる。
- **決定**: 端末名、物理解像度、orientationではなく現在のWindow Size ClassでUIを切り替える。
- **理由**: Foldの状態変化とAndroid 16大画面挙動に耐え、他端末にも一般化できる。
- **結果**: Compact/Medium/Expanded、fold/unfold中、multi-windowのUIテストが必要。

## ADR-011 手動DIから開始する

- **状態**: Proposed
- **文脈**: 依存はDataStore、Repository、通知、Data Layer、ViewModel程度で、小規模である。
- **決定**: Application container/factoryによる手動DIを使う。
- **理由**: annotation processingと学習・設定コストを抑える。
- **結果**: Service/ViewModel生成が複雑化、または複数scope管理が必要になればHiltを再評価する。

## ADR-012 仕様駆動・ループエンジニアリングのファイル運用を採用する

- **状態**: Proposed
- **文脈**: CodexとClaude Codeを併用し、レート制限・セッション終了を跨いで作業する。
- **決定**: `docs`を仕様の正本、`AGENTS.md`を共通規約、`.agents/roles/*.md`を役割、`.agents/work-items/*/state.yaml`を動的状態とする。`CLAUDE.md`は`AGENTS.md`読込を指示する。
- **理由**: エージェント固有の会話履歴に依存せず、仕様→実装→レビュー→テスト→人間承認のループを再開できる。
- **結果**: 各作業終了時にstateと証跡を更新し、仕様変更とコード変更を同じwork itemで追跡する。

### state.yaml最小schema

```yaml
work_item_id: BN-001
title: phone-to-watch-battery-sync
status: in_progress # todo|in_progress|blocked|in_review|in_test|human_verification|done
current_role: implementation
spec_refs:
  - docs/wear-os-integration-specification.md
owner_agent: codex # codex|claude-code|human|unassigned
last_updated: 2026-07-20T00:00:00+09:00
artifacts: []
checks: []
blockers: []
next_actions: []
handoff_notes: ""
```

仕様担当は同期形式・接続状態・Stale・ACを確定し、実装担当はData Layer/受信/DataStore/単体テスト、レビュー担当は時刻・競合・再接続・path・lifecycle、テスト担当は正常/切断/再接続/再起動/Stale/連続/異常データ、人間は実機・消費・遅延・最終承認を担当する。

## ADR-013 MobileとWearでユーザー起点の通知権限要求を行う

- **状態**: Accepted（2026-07-22、人間承認）
- **文脈**: Android 13以降ではMobileとWearの各インストールで`POST_NOTIFICATIONS`の実行時許可が必要であり、拒否時も監視・保存・同期を継続する必要がある。
- **決定**: 両アプリで`POST_NOTIFICATIONS`を宣言する。初回起動直後には要求せず、用途説明後の通知設定操作から各端末で個別に要求する。初回要求をDataStoreへ記録し、拒否またはdismiss後はOSダイアログを自動再表示せず、権限状態とシステム通知設定への導線を表示する。
- **理由**: 通知の価値を説明してから同意を求め、ユーザーの拒否を尊重しながら通知以外の中核処理を継続するため。
- **結果**: Mobile/Wearの権限組合せとchannel無効化を試験する。承認範囲は通知権限とそのUXに限定し、自動通知再試行条件はDEC-003で別途決定する。

## 参考

- [Android architecture recommendations](https://developer.android.com/topic/architecture/recommendations)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Wear Data Layer](https://developer.android.com/training/wearables/data/overview)
