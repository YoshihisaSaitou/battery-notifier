# アプリアイコン仕様書

文書ID: AIS-001  
版: 0.1  
状態: Draft  
最終更新: 2026-08-09

## 1. 目的

Battery NotifierのMobileアプリとWearアプリを同じ製品として識別でき、スマートフォンの電池監視と通知を小さい表示でも判別できるランチャーアイコンを定義する。

## 2. ビジュアルコンセプト

- 主題は「Mobileのバッテリー」と「通知」とする。Watch自身のバッテリー監視を示す表現にはしない。
- 中央の縦型バッテリーを主シルエットとし、右上の通知バッジで通知機能を表す。
- 背景は深いネイビー`#102A43`、バッテリー本体は白`#FFFFFF`、充電量はミント`#4FD1A5`、通知バッジはアンバー`#FFB84D`を基準色とする。
- 文字、パーセント値、端末固有の外形、AndroidまたはGoogleの商標図形を含めない。
- MobileとWearは同じ図柄・色・比率を使用する。

## 3. Androidリソース契約

- Adaptive Iconのbackground、foreground、monochromeを提供する。
- 各レイヤーは`108 x 108 dp`のviewportを使用し、識別に必要な図形は中央`66 x 66 dp`のsafe zone内へ収める。
- foregroundは背景色に依存せず輪郭が読め、OEMの円・角丸四角・スクワークル等のmaskで主図形が欠けない構成にする。
- monochromeは単色tint時にもバッテリーと通知バッジの関係が判別できる単純なシルエットとし、background drawableを含めない。
- Legacy launcher iconはmdpi、hdpi、xhdpi、xxhdpi、xxxhdpiの通常形とround形を提供し、Adaptive Iconと同じ構図を維持する。
- MobileとWearの`ic_launcher`および`ic_launcher_round`は同一の最終アセットから生成する。

## 4. 構図

- バッテリー本体は角丸長方形と上部端子で構成し、safe zone中央へ配置する。
- バッテリーの充電量は下側から約60%を満たす面として表現する。実際の現在残量を示す可変表示ではない。
- 通知バッジはバッテリー右上へ重ね、バッテリー輪郭との間に十分な分離を持たせる。
- 影、細線、写真表現、グラデーション、過度な立体表現を避け、小サイズでの輪郭を優先する。

## 5. 受け入れ条件

- `AC-025`: Given MobileとWearを対応launcherで表示、When 通常・round・themed iconを確認、Then 両アプリが同一のBattery Notifier図柄を表示し、主図形がmaskで欠けず、themed iconでもバッテリーと通知バッジを判別できる。
- Mobile/Wear両プロジェクトのresource processing、unit test、lintが成功する。
- 48px相当のlegacy iconと円形previewで、文字なしにバッテリー通知アプリとして識別可能であることを人間が確認する。

## 6. 検証根拠

AndroidのAdaptive Icon公式ガイドを2026-08-09に確認した。レイヤーは`108 x 108 dp`、ロゴは`48 x 48 dp`以上`66 x 66 dp`以下、外周18dpはmaskと視覚効果用であり、themed iconにはmonochrome layerを使用する。

- https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
- https://developer.android.com/studio/write/create-app-icons

