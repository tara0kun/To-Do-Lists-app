# To-Do-Lists-app

Android用のシンプルなToDoリストアプリ。Kotlin + Jetpack Compose + Room による最小構成のスターターです。

## 機能

- タスクの追加 / 完了チェック / 削除
- 完了済みタスクの一括削除
- Room による永続化（アプリ再起動後も保持）
- Material 3 + 端末カラー（Android 12+ のダイナミックカラー）
- ライト/ダークテーマ対応

## 技術スタック

- 言語: Kotlin 1.9.24
- UI: Jetpack Compose（BOM 2024.06.00）+ Material 3
- アーキテクチャ: ViewModel + StateFlow
- 永続化: Room 2.6.1（KSP 利用）
- minSdk 26 / targetSdk 34 / compileSdk 34
- Android Gradle Plugin 8.5.2 / Gradle 8.7

## プロジェクト構成

```
app/src/main/java/com/example/todolists/
├── MainActivity.kt
├── data/
│   ├── Task.kt              # Room Entity
│   ├── TaskDao.kt           # DAO
│   ├── TaskDatabase.kt      # Room Database
│   └── TaskRepository.kt
└── ui/
    ├── TaskScreen.kt        # Compose 画面
    ├── TaskViewModel.kt
    └── theme/Theme.kt
```

## ビルド方法

1. Android Studio（Hedgehog 以降推奨）でプロジェクトのルートを開く
2. Gradle 同期完了後、`app` 構成で実行
3. もしくは CLI から:

```bash
./gradlew :app:assembleDebug
```

> `gradlew` 本体（バイナリ）と `gradle-wrapper.jar` はリポジトリに含まれていません。Android Studio で初回起動時に生成されるか、既存の Gradle インストールから `gradle wrapper` で生成してください。

## 拡張のヒント

- 期日 / リマインダー追加 → `Task` に `dueAt: Long?` を追加し、WorkManager で通知
- カテゴリ分け → `Category` Entity と外部キー
- クラウド同期 → Firebase / Ktor バックエンドと連携
