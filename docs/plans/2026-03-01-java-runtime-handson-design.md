# Java ランタイム & Gradle ハンズオン学習教材 — 設計

## 概要

Java/JVM のランタイムと Gradle ビルドシステムを段階的に体験するハンズオン教材。Dev Container 上で動作し、記事 `language-runtime-build-guide.md` の内容を実際に手を動かして理解する。

## 対象読者

- JVM/JDK/JRE の概念が薄い開発者
- Gradle を使ったことがない or 仕組みを理解したい開発者

## ゴール

Gradle Toolchains による複数 JDK 管理まで到達する。

## 実行環境

Dev Container（JDK 17 + Gradle）。ローカルに Java をインストールする必要なし。

## ディレクトリ構成

```
runtime-handson/
├── .devcontainer/
│   └── devcontainer.json          ← JDK 17 + Gradle + Docker-in-Docker
├── README.md                       ← 概要・前提条件・ステップ一覧
├── step1-javac-basics/
│   ├── README.md                   ← 手順書
│   └── src/
│       ├── Hello.java
│       ├── Main.java
│       └── Greeter.java
├── step2-jdk-jre/
│   ├── README.md
│   └── src/                        ← Step 1 のコンパイル済み .class を使う
├── step3-gradle-intro/
│   ├── README.md
│   └── app/                        ← gradle init で生成する構成
│       ├── build.gradle.kts
│       └── src/
├── step4-gradle-test/
│   ├── README.md
│   └── app/
│       ├── build.gradle.kts
│       └── src/
│           ├── main/java/
│           └── test/java/
└── step5-toolchains/
    ├── README.md
    └── app/
        ├── build.gradle.kts        ← Toolchains 設定あり
        └── src/
```

## 各ステップ設計

### Step 1: javac/java の基礎

**目的:** コンパイルと実行が別工程であることを手で体験する

**内容:**
1. `Hello.java` を書いて `javac` → `java` の基本フロー
2. `javap -c Hello` でバイトコードを覗く
3. `Main.java` が `Greeter.java` を呼ぶ構成 — 複数ファイルのコンパイル
4. `.class` を別ディレクトリ（`out/`）に出力（`javac -d out`）
5. クラスパスを指定して実行（`java -cp out Main`）
6. クラスパスが間違っている場合の `ClassNotFoundException` を体験

**学び:** ソースコード → バイトコード → JVM 実行の流れ。クラスパスの概念。

### Step 2: JDK と JRE の違いを体験

**目的:** JDK に含まれるツール群と JRE だけでは何ができないかを理解する

**内容:**
1. `which javac`, `java -version` で環境確認
2. `ls $JAVA_HOME/bin` で JDK 付属ツール一覧
3. Step 1 の `.class` を JRE だけで実行できることを確認
4. JRE-only Docker イメージで `javac` が使えないことを確認
5. `jps`, `jstack` など JDK 診断ツールを触る

**学び:** JDK = JRE + 開発ツール。本番に JRE だけ入れれば軽い理由。

### Step 3: Gradle 導入

**目的:** 手動の javac/java を Gradle に置き換え、ビルドツールの価値を体験する

**内容:**
1. `gradle init` で Java application プロジェクト生成
2. ディレクトリ構造の確認（src/main/java, src/test/java）
3. `build.gradle.kts` の読み解き
4. `./gradlew build` → `./gradlew run`
5. `build/` ディレクトリの中身確認
6. Step 1 の手動コンパイルとの対比

**学び:** Gradle は javac/java のラッパー。ディレクトリ規約。Gradle Wrapper。

### Step 4: Gradle でテスト

**目的:** テスト実行も Gradle が管理し、テスト時にアプリが JVM 上で動くことを理解する

**内容:**
1. JUnit 5 テストコード追加
2. `./gradlew test` 実行
3. テスト失敗させてレポート確認（`build/reports/tests/`）
4. `--info` でテスト実行 JVM をログ確認
5. `jps` で Gradle デーモンとテスト JVM が別プロセスであることを観察

**学び:** Gradle がテスト用に別 JVM を起動すること（Step 5 の伏線）。

### Step 5: Gradle Toolchains

**目的:** Gradle 自体の Java とアプリの Java を分離する仕組みを体験する

**内容:**
1. 現在の `java -version` 確認（JDK 17）
2. `build.gradle.kts` に Toolchains 設定（`languageVersion.set(JavaLanguageVersion.of(11))`）
3. `./gradlew build` — JDK 11 自動ダウンロード観察
4. `--info` でテストが JVM 11 で実行されていることを確認
5. Java 17 の `record` を書いてコンパイル失敗を体験
6. Toolchains を外すと通ることを確認 — 本番事故シナリオの理解

**学び:** Gradle Toolchains の実体験。なぜ必要なのかの体感。

## Dev Container 設定

```jsonc
{
  "name": "Java Runtime Hands-on",
  "image": "mcr.microsoft.com/devcontainers/java:17",
  "features": {
    "ghcr.io/devcontainers/features/docker-in-docker:2": {}
  },
  "postCreateCommand": "java -version && gradle -version"
}
```

Docker-in-Docker は Step 2 で JRE-only コンテナを起動するために必要。
