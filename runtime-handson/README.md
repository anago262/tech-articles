# Java ランタイム & Gradle ハンズオン

## このハンズオンで学べること

- JVM の仕組み（ソースコード → バイトコード → 実行）
- JDK と JRE の違い
- Gradle ビルドシステムの基礎
- Gradle Toolchains による複数 JDK 管理

## 前提条件

- VS Code がインストールされていること
- Dev Containers 拡張機能がインストールされていること
- Docker Desktop が起動していること

## 始め方

1. このフォルダを VS Code で開く
2. コマンドパレット → 「Dev Containers: Reopen in Container」を選択
3. コンテナのビルドが完了したら準備完了（JDK 17 + Gradle がインストール済み）

## ステップ一覧

| ステップ | タイトル | 所要時間目安 | 学べること |
|---------|---------|------------|-----------|
| [Step 1](step1-javac-basics/README.md) | javac/java の基礎 | 20分 | コンパイルと実行の仕組み・クラスパス |
| [Step 2](step2-jdk-jre/README.md) | JDK と JRE の違い | 15分 | JDK 付属ツール・JRE-only 環境との比較 |
| [Step 3](step3-gradle-intro/README.md) | Gradle 導入 | 20分 | ビルドツールの役割・ディレクトリ規約・Wrapper |
| [Step 4](step4-gradle-test/README.md) | Gradle でテスト | 15分 | テスト実行の仕組み・テスト用 JVM |
| [Step 5](step5-toolchains/README.md) | Gradle Toolchains | 20分 | 複数 JDK 管理・バージョン互換性の保証 |

## 関連記事

- [言語ランタイムとビルドの仕組み](../language-runtime-build-guide.md)
- [VS Code Dev Containers 完全ガイド](../vscode-devcontainers-guide.md)
