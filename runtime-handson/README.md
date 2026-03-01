# Java ランタイム & Gradle ハンズオン

## このハンズオンで学べること

- JVM の仕組み（ソースコード → バイトコード → 実行）
- JDK と JRE の違い
- Gradle ビルドシステムの基礎
- Gradle Toolchains による複数 JDK 管理

## 前提条件

- [VS Code](https://code.visualstudio.com/) がインストールされていること
- VS Code 拡張機能 [Dev Containers](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers) がインストールされていること
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) がインストールされ、**起動していること**

## 始め方

### 1. この `runtime-handson` フォルダを VS Code で開く

```bash
code runtime-handson
```

または VS Code のメニュー: **File → Open Folder...** で `runtime-handson` フォルダを選択。

> **重要:** 必ず `runtime-handson` フォルダを直接開いてください。親フォルダを開くと Dev Container が認識されません（`.devcontainer/` が `runtime-handson/` 直下にあるため）。

### 2. Dev Container で開き直す

VS Code の左下に緑色のアイコン `><` が表示されます。

1. `Cmd + Shift + P`（macOS）/ `Ctrl + Shift + P`（Windows/Linux）でコマンドパレットを開く
2. `Dev Containers: Reopen in Container` と入力して選択

初回はコンテナイメージのダウンロードに数分かかります。VS Code 右下に進捗が表示されます。

### 3. 環境を確認する

コンテナが起動したら、ターミナルを開いて確認します。

``Ctrl + ` ``（バッククォート）でターミナルを開き、以下を実行:

```bash
java -version
```
```
openjdk version "17.x.x" ...
```

```bash
gradle -version
```
```
------------------------------------------------------------
Gradle x.x.x
------------------------------------------------------------
```

両方のバージョンが表示されれば準備完了です。

### 4. ハンズオンを開始する

ターミナル内で各ステップのディレクトリに移動して進めます。Step 1 から順番に取り組んでください。

> **ヒント:** ターミナルが `runtime-handson` ディレクトリで開かない場合は、`ls` で `step1-javac-basics` などのフォルダが見えるディレクトリまで `cd` してください。

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
