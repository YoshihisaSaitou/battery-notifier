# Wear OS連携仕様書

文書ID: WIS-001  
版: 0.1  
状態: Draft  
最終更新: 2026-07-20

## 1. 目的

Mobileの電池状態と到達イベントをWearへ安全かつ省電力に同期し、切断・再接続・再起動・連続更新後も最新状態へ収束させる。

## 2. 前提条件

- Wearable Data Layer APIを使用する。
- MobileとWearのapplication IDおよび署名証明書を一致させる。
- Google Play servicesのAPI利用可否を実行時に確認する。
- 対象はAndroid phoneとWear OSの組合せであり、iOSペアリングは対象外とする。
- Data Layerを永続ストレージにせず、両端末のDataStoreを真実のソースとする。

## 3. API選択

| API | 用途 | 理由 |
|---|---|---|
| DataClient / DataItem | 最新状態と到達イベント | 切断中にバッファされ、再接続時に同期できる |
| MessageClient | Wearからの即時状態要求 | 保存不要のベストエフォート要求に適する |
| NodeClient | 現在到達可能なnodeの診断 | UIの接続参考情報に使う |
| CapabilityClient | 対応アプリ/nodeの発見 | Mobile/Wear機能の存在確認に使う |

## 4. 送信契機

- 監視開始・停止。
- 残量または充電状態の変更。
- しきい値変更。
- 到達イベント生成。
- nodeの再到達。
- Wearからの状態要求。
- Mobile再起動後の復旧。
- ユーザーの「今すぐ同期」。

表示遅延が利用体験へ直結するため、状態変化とイベントは`setUrgent()`を使用する。ただし同じBatterySnapshotを短時間に連続送信しないよう250～500msの集約窓を設ける。

## 5. 接続状態別動作

### 接続中

1. MobileはDataStoreへ状態を確定する。
2. 固定パスのstate DataItemを更新する。
3. 到達時はevent DataItemも更新する。
4. WearはListener Serviceで受信し、検証後に保存する。
5. UI、Tile、コンプリケーションを更新し、有効イベントなら通知する。

### 切断中

- Mobile監視とMobile通知を継続する。
- DataItemへ最新状態を書き続ける。固定パスのため最終値へ収束する。
- Wearは保存済み値を表示し、5分超でStaleとする。
- 到達イベントは5分で期限切れとし、遅れて届いた警告でユーザーを混乱させない。

### 再接続

1. node/capability変化または送信成功で再接続を検知する。
2. Mobileは古いキャッシュを再送するのではなく、現在の電池値を再取得する。
3. sequenceを増やし、stateをurgent送信する。
4. Wearはsequence比較で最新だけを採用する。
5. eventが期限内なら通知し、期限切れなら処理済みにして通知しない。

## 6. Wear受信サービス

- `WearableListenerService`でアプリ非表示時もDataItemイベントを処理する。
- Manifestのpath prefix/filterを`/battery-notifier/v1/`へ限定する。
- `onDataChanged`内で長時間処理せず、検証・DataStore保存・通知更新を構造化Coroutineへ移す。
- Activityの`onResume` listenerだけに依存しない。
- サービスの重複呼び出しに備えてeventIdとsequenceで冪等にする。

## 7. ライフサイクル

| 状況 | 動作 |
|---|---|
| Mobile Activity停止 | FGSが監視を継続。UI listenerは解除 |
| Mobile process再生成 | DataStoreから状態復元後、現在値で再評価 |
| Wear Activity停止 | Listener Serviceは受信可能。UIタイマーは停止 |
| Wear process再生成 | DataStoreを読み、Freshnessを再計算 |
| Google Play services更新 | clientを保持し続けず、必要時に再生成 |
| アプリ更新 | schemaVersionを維持し、後方互換Readerを最低1版保持 |

## 8. 連続更新と競合

- Mobile側イベント処理を直列化する。
- DataItem送信結果の返却順は状態順序を保証しないため、Wearは必ずpayload sequenceで判断する。
- 送信AのTaskが送信Bより後に完了しても、DataStoreの`lastSyncedSequence`は最大値だけを保存する。
- 同じ値の再送にも新しいsequenceを含め、DataItem変更として認識させる。
- Watchでは通知処理済み記録を通知投稿前に原子的に予約し、並行callbackによる二重投稿を防ぐ。投稿失敗時は再試行可能な状態を明示する。

## 9. セキュリティ

- Data Layerの同一package/署名検証を利用する。
- 端末識別子、アカウント、位置、ネットワーク情報をpayloadへ含めない。
- 受信値を信頼せず[data-design.md](data-design.md)の順序で検証する。
- 低レベルsocketや独自Bluetooth通信を実装しない。

## 10. 診断項目

- Data Layer API利用可否。
- 到達可能node数と表示名。ただし永続ログへ端末名を保存しない。
- 最終送信sequence/時刻/結果分類。
- 最終受信sequence/時刻。
- invalid payload、unsupported schema、duplicate、out-of-orderの件数。

## 11. 受け入れ条件

- AC-004～AC-008を満たす。
- Bluetooth直結、Wi-Fi利用、機内モードによる切断、再接続で結果を確認する。
- 30回の連続更新後、Wear値が最大sequenceの値と一致する。

## 12. 参考

- [Wear OS Data Layer overview](https://developer.android.com/training/wearables/data/overview)
- [Sync data items](https://developer.android.com/training/wearables/data/data-items)
- [Handle Data Layer events](https://developer.android.com/training/wearables/data/events)

