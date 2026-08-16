# Google Play掲載用画像仕様書

文書ID: GPLA-001  
版: 0.1  
状態: Draft  
最終更新: 2026-08-15

## 1. 目的

Google Play Consoleのデフォルトのストア掲載情報へ登録するBattery Notifierのアプリアイコンとフィーチャーグラフィックを、既存の視覚アイデンティティとGoogle Playの画像要件に合わせて作成する。

## 2. 共通方針

- `docs/app-icon-specification.md`の深いネイビー`#102A43`、白`#FFFFFF`、ミント`#4FD1A5`、アンバー`#FFB84D`を基準色とする。
- スマートフォンの電池監視、通知、ペア設定済みWear OS端末への状態共有を表し、ウォッチ自身の電池監視と誤認させない。
- ランキング、価格、割引、ダウンロード誘導、Google Playバッジ、第三者の商標、時限的な訴求を含めない。
- 掲載言語に依存せず共用できるよう、画像内に文字、数字、スローガンを含めない。
- 最終成果物、生成スクリプト、マニフェスト、生成経緯を`docs/assets/google-play/`へ保存する。

## 3. アプリアイコン

- 成果物名は`google-play-app-icon.png`とする。
- `docs/assets/app-icon-master.svg`から決定的に書き出し、既存ロゴの形状、比率、色を変更しない。
- 512 x 512 px、32-bit PNG、アルファチャンネルあり、1,024 KB以下とする。
- Google Play側のマスクを前提とし、画像自体へ角丸や円形マスクを追加しない。
- 誤認を招く新着ドット、ランキング、価格、Playカテゴリ、ダウンロード記号を追加しない。既存のアンバー通知バッジはアプリの通知機能を示す承認済みブランド要素として維持する。

## 4. フィーチャーグラフィック

- 成果物名は`google-play-feature-graphic.png`とする。
- 1,024 x 500 px、24-bit PNG、アルファチャンネルなし、15 MB以下とする。
- 中央部へ主要な視覚要素を置き、端は背景装飾だけにしてGoogle Playの表示形式による切り落としへ備える。
- 既存アイコンをそのまま大きく再掲せず、ブランド色と幾何学要素を拡張して、スマートフォン電池の監視、状態同期、通知を抽象的に表す。
- 細部を詰め込みすぎず、端末フレーム、人物、写真、第三者ロゴ、ウォーターマーク、文字を使用しない。

## 5. 代替テキスト

- 日本語: `スマートフォンの電池状態がWear OSへ同期され、設定した残量で通知されることを表すグラフィック`
- 英語: `Graphic showing phone battery status syncing to Wear OS with an alert at the selected level`

## 6. 受け入れ条件

- `AC-035`: アプリアイコンが512 x 512 pxの32-bit PNG、アルファチャンネルあり、1,024 KB以下であり、AIS-001のマスターSVGと同じ構図・色である。
- `AC-036`: フィーチャーグラフィックが1,024 x 500 pxの24-bit PNG、アルファチャンネルなし、15 MB以下であり、中央の主要要素が判別でき、禁止要素と文字を含まない。
- `AC-037`: マニフェストに寸法、形式、容量、SHA-256、代替テキスト、生成元、最終プロンプトを記録し、同梱スクリプトで最終書き出しを再現できる。
- `AC-038`: 人間がPlay Consoleへのアップロード前に、視覚品質、誤認の有無、ブランド整合性、実際のアップロード受理を確認する。

## 7. 公式根拠

2026-08-15にGoogle Play Console公式ヘルプを確認した。

- https://support.google.com/googleplay/android-developer/answer/9866151
- https://support.google.com/googleplay/android-developer/answer/9898842
- https://play.google.com/console/about/guides/featuring/

