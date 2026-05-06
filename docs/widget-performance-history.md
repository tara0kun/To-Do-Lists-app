# ウィジェットパフォーマンス問題 格闘記録

このドキュメントは、To-Do List アプリの Android ウィジェットの「タップ・追加・更新が即時反映されない」という問題に対して、
これまで何を試して、何が効いて、何が効かなかったのか、最終的にどう判断したのかを時系列で記録したもの。

将来同じ問題に再び遭遇した時、または別の Android Glance 系プロジェクトでつまずいた時の参考資料として残す。

---

## 0. 前提：何を作っていたか

- Kotlin 2.0.21 + Jetpack Compose
- **ウィジェットは Jetpack Glance 1.1.1 で実装**
- 4 種のウィジェット
  - SimpleListWidget（簡易リスト）
  - AllTasksWidget（すべてのタスク）
  - OverdueWidget（期限切れ）
  - CompletedWidget（完了済み）
- 各ウィジェット内で
  - タスク行を `LazyColumn` で表示
  - 行のチェックボックスをタップすると完了切り替え
  - ヘッダの「+」ボタンで `QuickAddActivity` を開いてタスク追加
  - 「↻」ボタンで強制リフレッシュ
- バックグラウンド画像を任意で設定可能

---

## 1. 最初の問題報告

ユーザー報告（実機 = POCO F7 / HyperOS）：
> ウォジェット内での追加及びタップは即反映しない、ちょっとラグがある感じ？

最初は「ちょっとした遅延」程度の認識だった。
ここから 1 ヶ月にわたる試行錯誤が始まる。

---

## 2. 試した対策の時系列

### 試行 1：楽観 UI（optimistic UI）の導入

**症状**: タップ → 視覚反映まで明らかに 100〜200ms 以上のラグ。「タップしたのに何も起きていない」と感じる。

**仮説**: DB 更新が完了してから widget を再描画しているのが遅い。DB 更新の前に「もう完了済みになった」と楽観的に表示すべき。

**対策**:
- `ToggleTaskAction` 内で、Glance の `PreferencesGlanceStateDefinition` に `opt_done_<taskId>` というキーで「楽観値」を書き込む
- `TaskListWidgetContent` で `currentState<Preferences>()` から楽観値を読み、DB の `task.isDone` より優先する
- `effectiveDone = optimistic ?: task.isDone`

**結果**: 部分改善。アプリ側のコールバックが返るのは速くなった（数十 ms）。
**しかし体感は変わらず**。視覚反映の遅延は別の場所にあるとわかった。

---

### 試行 2：`WidgetWorkScope` でファイア・アンド・フォーゲット

**症状**: ActionCallback の `onAction` が返るまでに DB 書き込み・カレンダー削除・widget リフレッシュが終わるのを待っている。

**仮説**: コールバック自体を即座に返せれば、Glance の auto-recompose が早く走る。

**対策**:
- `WidgetWorkScope: CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)` を作成
- ActionCallback では「楽観 UI 書き込み」だけを待ち、DB 書き込み・refreshWidgets・カレンダー処理は `WidgetWorkScope.launch { ... }` で投げて即 return

**結果**: `onAction` の return が 10〜15ms に短縮。**しかし依然として体感は変わらず**。
このあたりで「アプリ側ではない何かが遅い」と疑い始める。

---

### 試行 3：`touchWidgetState` を入れて Glance auto-recompose を強制発火

**症状**: 楽観値を書いても、すぐに widget が再描画されるわけではない。

**仮説**: Glance はステート変更を検知して auto-recompose する。`touchWidgetState` で時刻スタンプをバンプすれば追加の再合成が走る。

**対策**:
- `refreshWidgets` の冒頭で各 widget インスタンスの Preferences にダミーキー `widget_touch` を書き込む

**結果**: **逆効果**。`updateAll` が直後に呼ばれるので二重発火。ログで計測したら `touchWidgetState` だけで 30〜90ms 食っていた。
後で削除（試行 8 参照）。

---

### 試行 4：複数経路の同時送信（ベルト＆ブレース）

**仮説**: `updateAll` だけでは届いていないかもしれない。複数経路で送れば 1 つは届くだろう。

**対策**: refreshWidgets で次の 3 つを順番にやる
1. `touchWidgetState`（試行 3）
2. `updateAll(context)` を 4 widget 並列実行
3. `Intent(ACTION_APPWIDGET_UPDATE)` をフォアグラウンド優先度でブロードキャスト

**結果**: わずかに安定するが体感は不変。後で 1 と 3 の必要性を再評価することに。

---

### 試行 5：診断ログの大量挿入（`WidgetDbg` タグ）

**目的**: 「アプリ側」「フレームワーク」「ランチャー」のどこが遅いのかを切り分ける。

**やったこと**: `ToggleTaskAction.onAction` と `TaskRepository.refreshWidgets` の各ステップに `Log.d(TAG, "[$timestamp] step done (${elapsed}ms)")` を仕込む。

**得られた結論**:
```
onAction enter:                              t = 0ms
optimistic state written:                    t = 11ms
onAction returning:                          t = 14ms
refreshWidgets start:                        t = 46ms
touchWidgetState done:                       t = 60ms (14ms 食う)
all 4 widgets updateAll done:                t = 110ms
refreshWidgets total:                        t = 120ms
repository.toggle done (DB+全部):            t = 150ms
```

**つまり**：
- 我々のコードは 150ms で完全に「Android に投げ終わっている」
- なのにユーザー体感は 1〜2 秒以上のラグ
- **問題はそれより先（ランチャーが画面を描く段階）にある**

ここでランチャー側の問題ではないかと最初に強く疑い始めた。

---

### 試行 6：実機 / Pixel 8 エミュ比較

**仮説の検証**: もし HyperOS のランチャー固有の問題なら、Pixel 8 エミュでは速いはず。

**結果**: **両方で同じくらい遅かった**。

これにより一度「ランチャー固有ではない、Android Widget 仕組みそのものの限界」と判断。
（実はこの判断は後に部分的に修正されることになる—試行 13 参照）

---

### 試行 7：振動フィードバック案 / cache 提案

**この時点での判断**: Android Widget IPC の物理的下限が ~100ms あるので、これより速くするのは無理。視覚遅延を埋めるためにタップ時に短く振動させる案を提案。

**結果**: ユーザーが「それより前に他にできることはないか」と粘ったため、提案は採用せず、別の最適化に進む。

これはユーザーの粘りが正解だった。妥協で終わらせず追加の最適化を探したことが後の解決につながる。

---

### 試行 8：`touchWidgetState` を削除

**症状**: ログで `touchWidgetState` が 30〜90ms かかっているのが見える。`updateAll` が直後に呼ばれるなら不要。

**対策**: `refreshWidgets` から `touchWidgetState` の呼び出しと関数本体を削除。`TOUCH_KEY` 定数も削除。

**結果**: **大成功**。`refreshWidgets total` が 80〜100ms → **17〜31ms** に短縮（3〜5倍速い）。
ただし**ユーザー体感は変わらず**。これにより「我々のコードはもう削るところがほぼない」と確信。

---

### 試行 9：`Image` ベースのカスタムチェックボックス → Glance `CheckBox` に置き換え

**転機の発見**: 別アプリ（Google Tasks など）のチェックボックスがなぜ即反映するのか調査した結果、Android 12+ の `RemoteViews.setOnCheckedChangeResponse()` API の存在を知る。

これは **CompoundButton（CheckBox / Switch）の状態をランチャー側でローカルにトグル**してから broadcast を送る仕組み。つまり**ランチャーが我々のアプリの応答を待たずに視覚を即更新する**。

**対策**:
- 自作の `Image(checkboxRes).clickable(toggleAction)` を Glance 公式の `CheckBox(checked, onCheckedChange = toggleAction)` に置き換え

**結果**: **チェックボックスのタップが即反映に**。長期にわたる「タップ遅延」問題の主要部分が解決。

---

### 試行 9 の途中で発生した障害 1：CheckBox 初期化クラッシュ

**症状**: ウィジェットが「コンテンツが表示できません」と表示される。

**Logcat**:
```
java.lang.IllegalArgumentException: Cannot provide resource-backed
ColorProviders to CheckBoxColors
  at androidx.glance.appwidget.CheckboxDefaults.colors(CheckBox.kt:189)
```

**原因**: `CheckboxDefaults.colors()` は **fixed ColorProvider しか受け付けない**仕様。
我々が渡していた `GlanceTheme.colors.primary` 等は **resource-backed**（内部的に `@ColorRes` を参照）なので拒否された。

**対策**: 背景画像ありのときだけ fixed white の ColorProvider を渡し、それ以外は引数なしの `CheckboxDefaults.colors()` を使う。

**結果**: 表示復活。

---

### 試行 9 の途中で発生した障害 2：CheckBox の flip-back（チェックが付いてすぐ戻る）

**症状**: タップ → 一瞬チェックが付く → すぐ消える。

**原因の特定**: `TaskRepository.toggle()` の最後に `clearOptimisticForWidgets()` を呼んでいた。これは

1. ローカルトグル直後にランチャーが checked=true を表示
2. アクション内で `optimistic[id] = true` を書く（OK）
3. 同期的に bg work に入り、`update()` → DB コミット → `refreshWidgets()`
4. 直後に `clearOptimisticForWidgets()` で `optimistic` キーを削除
5. 状態変更で Glance が再合成 → `optimistic = null`、DB 読みがまだ反映前なら `isDone = false` → `CheckBox(checked=false)` を push
6. ランチャーが setCompoundButtonChecked(false) で**ローカルトグルを上書き** → 戻る

**対策**:
- `clearOptimisticForWidgets()` を `toggle()` から削除
- `markOptimisticForWidgets()` も削除（同じレースを起こす可能性 + 元々 calling widget は ToggleTaskAction で書いている、他の widget は updateAll で DB から拾う）
- 楽観キーは「次のタップで上書きされるまで残しておく」設計に

**結果**: flip-back 解消。チェックボックスが付いたまま残るようになった。

---

### 試行 10：`QuickAddActivity` の `delay(400)` を削除

**症状**: 「タスクを追加」ボタンを押してから「ダイアログが閉じる」まで体感的に長い。

**原因**: 元々 `repository.add(text)` の後に `delay(400)` を入れていた。
コメントには「broadcasts が配送される猶予」と書いていたが、実は `repository.add()` 内の `refreshWidgets()` がすでに updateAll を await しているので、その時点で RemoteViews は飛んでいる。**400ms 待つ意味は無かった**。

**対策**: `delay(400)` を削除。

**結果**: ダイアログが即閉じるようになった。

---

### 試行 11：背景画像のクロップ機能改善

ウィジェットパフォーマンスとは少し別の話だが、ユーザーの要望：
> 背景画像の切り抜きをシステム側で勝手に行っているので、ユーザーがどこからどこまでを使うのかを指定できるようにしたい

**最初の実装**: `CropImageActivity` を作って、ピンチ・ドラッグでパン／ズームできるようにし、ビューポート全体を切り抜き範囲とした。

**追加要望**: 「枠が見えなくてどこからどこまでかわからない」「実際のウィジェットのサイズが表示されてそれ通り設定されるようにしてほしい」

**対策**:
- `AppWidgetManager.getAppWidgetOptions(id)` で配置済みウィジェットの寸法（dp）を取得
- 4 種のうち面積最大のウィジェットのアスペクト比を採用
- クロップ枠を中央に配置して、枠外をディムオーバーレイで暗くする
- 白いボーダーと L 字コーナーマーカーを描画

これにより「クロップ後にウィジェットでどう見えるか」が事前にわかるようになった。

---

### 試行 12：再び現れた追加遅延

**症状**: チェックボックスは速くなったが、**新規追加した項目がリストに即現れない**。「5 秒以上経ってやっと出る」「他の動作で出てくる」「リフレッシュボタンも効かないことがある」「Pixel 8 エミュでも同じ」。

**新たなログ採取**: タップから視覚反映までの完全な Logcat（フィルタ無し）を取得。

**判明した事実**：

1. 我々の `refreshWidgets total` は 13〜101ms で完了している（ログで確認）
2. ランチャー（`com...nexuslauncher`）の EGL 統計：
   ```
   EGL_emulation app_time_stats:
     avg=12009.52ms min=8.59ms max=83981.81ms count=7
   ```
   ランチャーの**1 フレームの最大描画時間が 84 秒**。平均でも 12 秒。
3. つまりランチャーが新しい RemoteViews を画面に描くのに数秒〜数十秒かかっている

**判断**: アプリ側はもう限界。ボトルネックは **ランチャーが RemoteViews ツリーを画面に再構築する処理**そのもの。

---

### 試行 13：「他アプリは即反映なのに、なぜ我々は無理なのか」の調査

ユーザーから決定打的な質問：
> けど、ほかの既存アプリはしっかりと動くのはなんで？

調べてみたら、**Glance を使っているリスト型ウィジェットの実用例がほとんど無い**ことが判明：

| アプリ／ウィジェット | API |
|---|---|
| Google ToDo | 従来型 (`AppWidgetProvider` + `RemoteViewsService`) |
| Google Keep | 従来型 |
| Google カレンダー | 従来型 |
| Gmail | 従来型 |
| Pixel ランチャー標準ウィジェット | 従来型 |
| Spotify | 従来型 (リスト無し型) |
| 我々 | **Glance** |

**Glance と従来型の決定的な違い**：

| 項目 | Glance | 従来型 |
|---|---|---|
| リスト描画 | LazyColumn を毎回フル構築 → RemoteViews ツリー全体を一括 push | `RemoteViewsService` がアイテムをストリーム配信、ランチャーが必要分だけ取得 |
| 部分更新 | 不可（API が無い） | `notifyAppWidgetViewDataChanged()` でリスト部分だけ「データ変わった」と通知 → 差分更新 |
| ランチャー側の処理 | RemoteViews ツリー全破棄して再構築 | リストアイテムだけ差し替え |

**つまり Glance は「リスト全体を毎回投げ直す」アーキテクチャで、ランチャー側に毎回フルレンダリングを要求している**。
従来型は「データ変わったよ」と通知するだけで、ランチャーが差分処理する。

これが我々のアプリと Google ToDo の体感差の根本。

---

## 3. 最終的に判明したこと

### アプリ側のパイプライン
```
タップ                       (t = 0ms)
launcher → broadcast IPC      (t = ~30ms)
ToggleTaskAction.onAction      (t = ~30ms)
optimistic state written       (t = ~40ms)
onAction return                (t = ~45ms)
WidgetWorkScope start          (t = ~50ms)
DB write (dao.update)          (t = ~55ms)
refreshWidgets start           (t = ~60ms)
4 widgets updateAll done       (t = ~80ms)  ← ここまで我々のコード
```

我々のコードは概ね **100ms 以内**に完了している。これは限界近い。

### Android Widget 仕組み側（変えられない）
```
AppWidgetManager → launcher への RemoteViews IPC: 〜10〜30ms
launcher が RemoteViews をパース・ビューを再構築:    Glance 経由だと**数百 ms 〜 数秒**
launcher が次の vsync で描画:                       〜16ms
```

### ランチャー側のばらつき
- **Pixel 8 エミュ**: GPU エミュレーションが重く、フレーム時間が突発的に数秒〜数十秒
- **HyperOS**: 省電力で widget 更新を遅延キューに入れる
- 両方で症状が似るのは「launcher が描画を遅延させる」という根は同じだから

### 何が解決できて、何ができなかったか

| 項目 | 状態 |
|---|---|
| チェックボックスのタップ即反映 | ✅ 解決（CompoundButton ローカルトグル） |
| アプリ側の処理時間 | ✅ 80ms → 25ms に短縮（限界） |
| QuickAddActivity の閉じる速度 | ✅ 400ms 削除で改善 |
| 楽観 UI の flip-back バグ | ✅ 解決 |
| 背景画像のユーザー指定クロップ | ✅ 実装 |
| ウィジェット形状の表示 | ✅ 実装 |
| **タスク追加の即反映** | ❌ Glance のアーキテクチャ的に無理 |
| **削除の即反映** | ❌ 同上 |
| **再描画全般のランチャー依存遅延** | ❌ Glance では構造的に解決不可 |

---

## 4. 判断と次のアクション

「リスト型ウィジェットで即反映」を実現しているアプリは全て **`AppWidgetProvider` + `RemoteViewsService`（従来型）** を使っている。Glance は新しいけれど、**リスト系では実戦投入されていない API**。Google 自身も Tasks / Keep / Calendar / Gmail で使っていない。

**結論**: Glance を捨てて従来型で書き直す。

書き直しの計画は別ドキュメントまたは別のセクションで管理する。

---

## 5. 学んだこと

1. **「公式推奨 API」が常に最善とは限らない**
   - Google が Glance を推しているからといって、自社主要アプリで使っていないなら、何か理由がある
   - 採用例の少なさは red flag

2. **計測してから直す**
   - `WidgetDbg` ログで初めて「アプリ側はもう限界、launcher が遅い」と切り分けできた
   - 計測なしで「全部速くする」と最適化に走るのは時間の無駄

3. **アーキテクチャ的限界は最適化では超えられない**
   - 試行 1〜10 はすべて「アプリ側の効率化」
   - でも本質は「Glance は毎回フル RemoteViews を投げる」というアーキテクチャ
   - これを変えるには API そのものを変えるしかない

4. **CompoundButton の `setOnCheckedChangeResponse` (Android 12+) は超強力**
   - ローカルでトグルしてから broadcast を送るので、IPC 待たずに視覚反映
   - 知らないと「なぜ Google Tasks のチェックは速いのか」が永遠の謎になる

5. **エミュレータの GPU 性能はあてにならない**
   - Pixel 8 エミュの EGL 統計で 84 秒のフレーム時間が出るのは異常だが、これがランチャーに影響して widget 更新の遅延に直結する
   - 実機でのテストの方が信頼性高い

6. **「ユーザーの粘り」が正解だった**
   - 試行 7 で「もう物理的限界だから振動フィードバックで誤魔化そう」と提案したが、ユーザーが「他にないのか」と粘ったおかげで試行 8 以降の改善に至った
   - 「もう無理」と思っても掘る価値はある

---

## 6. コミット履歴（主要なもの）

| コミット | 内容 |
|---|---|
| `c1523e1` | 診断ログ `WidgetDbg` を全経路に挿入 |
| `09fcc34` | `touchWidgetState` を削除（80→25ms） |
| `8d667ff` | `Image` チェックボックスを Glance `CheckBox` に置き換え |
| `0bab350` | CheckBox の resource-backed ColorProvider クラッシュを修正 |
| `02ec33a` | flip-back 解消のため `clearOptimisticForWidgets` を削除 |
| `c4785d4` | Glance 1.1.1 で公開されていない `ToggleableStateKey` 参照を削除 |
| `c5a48a4` | `QuickAddActivity` の `delay(400)` を削除 |
| `46f762c` | クロップ枠を実際のウィジェット形状に合わせる |
