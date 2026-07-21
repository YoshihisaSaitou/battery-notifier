# Battery Notifier ドキュメント索引

最終更新: 2026-07-21
文書状態: Draft

## 目的

このディレクトリは、Androidスマートフォンの電池残量を監視し、スマートフォンとWear OSへ通知・表示するBattery Notifierの仕様駆動開発（Spec-Driven Development）の正本である。

要件・設計・テストの追跡には、次のIDを使用する。

- `PR-*`: プロダクト要件
- `FR-*`: 機能要件
- `NFR-*`: 非機能要件
- `UC-*`: ユースケース
- `AC-*`: 受け入れ条件
- `TC-*`: テストケース
- `ADR-*`: アーキテクチャ決定

## 文書一覧

| 文書 | 内容 |
|---|---|
| [product-requirements.md](product-requirements.md) | 目的、スコープ、成功条件、制約 |
| [functional-requirements.md](functional-requirements.md) | 機能・非機能要件と受け入れ条件 |
| [wear-os-display-specification.md](wear-os-display-specification.md) | Wear OSアプリ、Tile、鮮度表示 |
| [use-case-specification.md](use-case-specification.md) | 利用者・システム別ユースケース |
| [notification-background-monitoring-specification.md](notification-background-monitoring-specification.md) | 電池監視、しきい値判定、バックグラウンド処理 |
| [wear-os-integration-specification.md](wear-os-integration-specification.md) | Data Layer同期、切断・再接続、競合制御 |
| [notification-bridge-specification.md](notification-bridge-specification.md) | MobileからWatchへの通知イベント連携、重複防止 |
| [complication-specification.md](complication-specification.md) | ウォッチフェイス用コンプリケーション |
| [screen-specification-and-navigation.md](screen-specification-and-navigation.md) | Mobile/Wear画面仕様、画面遷移図 |
| [system-architecture-design.md](system-architecture-design.md) | 簡易DDD、レイヤ、モジュール構成 |
| [data-design.md](data-design.md) | ドメインモデル、DataStore、同期データ形式 |
| [localization-specification.md](localization-specification.md) | 日本語・英語対応 |
| [permissions-and-privacy-specification.md](permissions-and-privacy-specification.md) | 権限、データ取扱い、Play要件 |
| [test-plan-and-cases.md](test-plan-and-cases.md) | テスト方針、端末試験、テストケース |
| [compatibility-matrix.md](compatibility-matrix.md) | OS・端末・画面・接続の互換性 |
| [architecture-decision-records.md](architecture-decision-records.md) | ADR一覧と決定内容 |
| [development-workflow.md](development-workflow.md) | 環境プリフライト、Gradle実行、数値型テスト、実DataStore試験、独立レビュー準備 |

## 仕様変更ルール

1. 実装前に対象機能の仕様と受け入れ条件を更新する。
2. 仕様変更時は関連する要件ID、テストケース、ADRを同じ作業項目で更新する。
3. 未決事項は暗黙に実装せず、文書内の「未決事項」または新しいADRとして記録する。
4. 実装状態は`.agents/work-items/*/state.yaml`、恒久ルールは`AGENTS.md`、役割別ルールは`.agents/roles/*.md`で管理する。
5. CodexとClaude Codeは同じ仕様文書と状態ファイルを参照し、会話履歴だけを引き継ぎ情報にしない。

## エージェント運用ファイル

| ファイル | 用途 |
|---|---|
| [AGENTS.md](../AGENTS.md) | プロジェクト全体の共通ルール、工程ゲート、引き継ぎ規則 |
| [CLAUDE.md](../CLAUDE.md) | Claude Codeが`AGENTS.md`と作業状態を読むための起点 |
| [specification.md](../.agents/roles/specification.md) | 仕様担当の責務と完了条件 |
| [implementation.md](../.agents/roles/implementation.md) | 実装担当の責務と完了条件 |
| [review.md](../.agents/roles/review.md) | レビュー担当の重点確認項目 |
| [test.md](../.agents/roles/test.md) | テスト担当の必須シナリオと証跡ルール |
| [human.md](../.agents/roles/human.md) | 実機確認、消費・遅延評価、最終承認 |
| [BN-001 state.yaml](../.agents/work-items/bn-001-phone-to-watch-battery-sync/state.yaml) | スマートフォンからWearへの同期機能の現在工程、担当、証跡、次アクション |
| [BN-001 review checklist](../.agents/work-items/bn-001-phone-to-watch-battery-sync/review-checklist.md) | 独立レビュアーの適格性、再現コマンド、重点確認、finding形式 |

## 用語

- **Mobile**: `BatteryNotifierAndroidMobileApp`で開発するAndroidスマートフォンアプリ。
- **Wear**: `BatteryNotifierAndroidWearApp`で開発するWear OSアプリ。
- **監視対象電池**: v1.0ではMobile端末の電池。
- **しきい値**: 通知を発生させる電池残量の百分率。
- **Fresh**: Watchに保存されたMobile電池情報が所定の鮮度以内である状態。
- **Stale**: 最終同期から所定時間を超え、最新性を保証できない状態。
