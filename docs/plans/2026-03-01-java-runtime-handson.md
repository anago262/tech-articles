# Java ランタイム & Gradle ハンズオン教材 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Java/JVM のランタイムと Gradle を段階的に体験するハンズオン教材を `runtime-handson/` ディレクトリに作成する

**Architecture:** 5ステップ構成のハンズオン。共通の Dev Container（JDK 17 + Gradle + Docker-in-Docker）で動作。各ステップは独立したフォルダにサンプルコードと手順書（README.md）を持つ。

**Tech Stack:** Java 17, Gradle (Kotlin DSL), JUnit 5, Dev Containers, Docker-in-Docker

**Design doc:** `docs/plans/2026-03-01-java-runtime-handson-design.md`

---

### Task 1: プロジェクト基盤 — Dev Container + ルート README

**Files:**
- Create: `runtime-handson/.devcontainer/devcontainer.json`
- Create: `runtime-handson/README.md`

**Step 1: ディレクトリ作成**

```bash
mkdir -p runtime-handson/.devcontainer
```

**Step 2: devcontainer.json を作成**

```jsonc
// runtime-handson/.devcontainer/devcontainer.json
{
  "name": "Java Runtime Hands-on",
  "image": "mcr.microsoft.com/devcontainers/java:17",
  "features": {
    "ghcr.io/devcontainers/features/docker-in-docker:2": {}
  },
  "postCreateCommand": "java -version && gradle -version",
  "customizations": {
    "vscode": {
      "extensions": [
        "vscjava.vscode-java-pack"
      ]
    }
  }
}
```

**Step 3: ルート README.md を作成**

全体の概要、前提条件、各ステップの一覧と学習目標を書く。日本語で記述。

内容:
- このハンズオンで学べること（JVM の仕組み、JDK/JRE の違い、Gradle、Toolchains）
- 前提条件（VS Code + Dev Containers 拡張機能 + Docker）
- 各ステップの一覧表（番号、タイトル、所要時間目安、学べること）
- 始め方（「VS Code でこのフォルダを開き、Dev Container で再オープン」）

**Step 4: Commit**

```bash
git add runtime-handson/
git commit -m "docs: Java ランタイムハンズオン — プロジェクト基盤作成"
```

---

### Task 2: Step 1 — javac/java の基礎

**Files:**
- Create: `runtime-handson/step1-javac-basics/README.md`
- Create: `runtime-handson/step1-javac-basics/src/Hello.java`
- Create: `runtime-handson/step1-javac-basics/src/Greeter.java`
- Create: `runtime-handson/step1-javac-basics/src/Main.java`

**Step 1: サンプルコード — Hello.java**

```java
// runtime-handson/step1-javac-basics/src/Hello.java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, JVM!");
    }
}
```

**Step 2: サンプルコード — Greeter.java**

```java
// runtime-handson/step1-javac-basics/src/Greeter.java
public class Greeter {
    private String name;

    public Greeter(String name) {
        this.name = name;
    }

    public String greet() {
        return "Hello, " + name + "!";
    }
}
```

**Step 3: サンプルコード — Main.java**

```java
// runtime-handson/step1-javac-basics/src/Main.java
public class Main {
    public static void main(String[] args) {
        Greeter greeter = new Greeter("World");
        System.out.println(greeter.greet());
    }
}
```

**Step 4: 手順書 README.md を作成**

日本語で記述。セクション構成:

1. **このステップで学ぶこと** — コンパイルと実行は別工程、バイトコードの正体、クラスパス
2. **1.1 最初のコンパイルと実行**
   - `cd step1-javac-basics/src`
   - `javac Hello.java` を実行し、`ls` で `Hello.class` が生成されたことを確認
   - 解説: `javac` はソースコード → バイトコード変換器
   - `java Hello` で実行
   - 解説: `java` コマンドが JVM を起動し、バイトコードを実行する
3. **1.2 バイトコードを覗く**
   - `javap -c Hello` を実行
   - 出力の読み方の解説（`aload`, `invokevirtual` 等は JVM への命令であること）
   - ポイント: これが「中間コード」。OS に依存しない。JVM さえあれば動く
4. **1.3 複数ファイルのコンパイル**
   - `javac Main.java Greeter.java` でまとめてコンパイル
   - `java Main` で実行
   - `Main.java` だけコンパイルしようとするとどうなるか試す
5. **1.4 出力先ディレクトリとクラスパス**
   - `mkdir -p out && javac -d out src/*.java` — `.class` を `out/` に出力
   - `ls out/` で確認
   - `java -cp out Main` で実行
   - 解説: `-cp`（クラスパス）は「.class ファイルを探す場所」を JVM に教える
6. **1.5 クラスパスを間違えてみる**
   - `java Main`（`-cp` なし）→ ClassNotFoundException
   - `java -cp . Main`（間違ったディレクトリ）→ ClassNotFoundException
   - 解説: JVM は指定された場所にしか .class を探しに行かない
7. **まとめ** — ソースコード → javac → バイトコード(.class) → java(JVM) → 実行 の図

**Step 5: Commit**

```bash
git add runtime-handson/step1-javac-basics/
git commit -m "docs: Step 1 — javac/java の基礎ハンズオン"
```

---

### Task 3: Step 2 — JDK と JRE の違いを体験

**Files:**
- Create: `runtime-handson/step2-jdk-jre/README.md`

**Step 1: 手順書 README.md を作成**

日本語で記述。セクション構成:

1. **このステップで学ぶこと** — JDK と JRE の違い、JDK 付属ツール
2. **2.1 今の環境を確認する**
   - `java -version` — バージョンとディストリビューション
   - `which javac` / `which java` — コマンドの場所
   - `echo $JAVA_HOME` — JDK のインストール先
   - 解説: Dev Container には JDK 17 がインストールされている
3. **2.2 JDK に入っているツール**
   - `ls $JAVA_HOME/bin | head -20` でツール一覧
   - 主要ツールの表（javac, java, javap, jps, jstack, jmap, jar, jshell）と1行説明
   - `jshell` を起動して対話的に Java を実行してみる（`System.out.println(1+1)` → `/exit`）
4. **2.3 JRE-only 環境との比較**
   - `docker run --rm eclipse-temurin:17-jre java -version` — JRE だけで java は動く
   - `docker run --rm eclipse-temurin:17-jre javac -version` — javac がないのでエラー
   - 解説: JRE には実行エンジン（JVM）しかない。コンパイラは入っていない
5. **2.4 .class ファイルは JRE だけで動く**
   - Step 1 の `.class` を JRE コンテナにコピーして実行:
     ```bash
     cd ../step1-javac-basics/src
     javac -d /tmp/out Hello.java
     docker run --rm -v /tmp/out:/app eclipse-temurin:17-jre java -cp /app Hello
     ```
   - 解説: コンパイル済みバイトコードは JRE（JVM）だけで実行できる → 本番に JDK は不要
6. **2.5 JDK 診断ツールを触る**
   - ターミナルを2つ開く
   - ターミナル1: `java -cp /tmp/out Hello`（すぐ終わるので、代わりにスリープするプログラムを使う）
     - `runtime-handson/step2-jdk-jre/src/SlowApp.java` を作成:
       ```java
       public class SlowApp {
           public static void main(String[] args) throws Exception {
               System.out.println("Running... (press Ctrl+C to stop)");
               Thread.sleep(Long.MAX_VALUE);
           }
       }
       ```
   - ターミナル2: `jps` でプロセス一覧 → `jstack <PID>` でスレッドダンプ
   - 解説: これらの診断ツールは本番トラブルシューティングで使う。JDK にしか入っていない
7. **2.6 イメージサイズの比較**
   - `docker images eclipse-temurin` で JDK と JRE のサイズを比較
   - 解説: JRE は JDK の約半分。本番に JRE だけ入れる理由
8. **まとめ** — JDK = JRE + 開発ツール の図。開発では JDK、本番では JRE

**Step 2: SlowApp.java を作成**

```java
// runtime-handson/step2-jdk-jre/src/SlowApp.java
public class SlowApp {
    public static void main(String[] args) throws Exception {
        System.out.println("Running... (press Ctrl+C to stop)");
        Thread.sleep(Long.MAX_VALUE);
    }
}
```

**Step 3: Commit**

```bash
git add runtime-handson/step2-jdk-jre/
git commit -m "docs: Step 2 — JDK と JRE の違いハンズオン"
```

---

### Task 4: Step 3 — Gradle 導入

**Files:**
- Create: `runtime-handson/step3-gradle-intro/README.md`
- Create: `runtime-handson/step3-gradle-intro/app/build.gradle.kts`
- Create: `runtime-handson/step3-gradle-intro/app/settings.gradle.kts`
- Create: `runtime-handson/step3-gradle-intro/app/src/main/java/handson/App.java`

**Step 1: サンプルコード — App.java**

```java
// runtime-handson/step3-gradle-intro/app/src/main/java/handson/App.java
package handson;

public class App {
    public String getGreeting() {
        return "Hello from Gradle!";
    }

    public static void main(String[] args) {
        System.out.println(new App().getGreeting());
    }
}
```

**Step 2: build.gradle.kts**

```kotlin
// runtime-handson/step3-gradle-intro/app/build.gradle.kts
plugins {
    application
}

repositories {
    mavenCentral()
}

application {
    mainClass.set("handson.App")
}
```

**Step 3: settings.gradle.kts**

```kotlin
// runtime-handson/step3-gradle-intro/app/settings.gradle.kts
rootProject.name = "step3-gradle-intro"
```

**Step 4: Gradle Wrapper を生成**

手順書内で `cd runtime-handson/step3-gradle-intro/app && gradle wrapper` を実行する指示を書く。
ただし、教材としては Wrapper 生成済みのものを含めるのが親切。

手順書では `gradle wrapper` コマンドの説明をした上で、`./gradlew` を使う流れにする。

**Step 5: 手順書 README.md を作成**

セクション構成:

1. **このステップで学ぶこと** — Gradle の役割、ディレクトリ規約、Gradle Wrapper
2. **3.1 Step 1 の振り返り — 手動コンパイルの面倒さ**
   - Step 1 では `javac` で全ファイルを指定し、`-d` で出力先を指定し、`-cp` で実行した
   - ファイルが増えるとコマンドが長くなる。依存ライブラリの管理も必要。これを自動化するのがビルドツール
3. **3.2 プロジェクト構造を確認する**
   - `tree app/` でディレクトリ構造を表示
   - `src/main/java/` — メインのソースコード
   - `build.gradle.kts` — ビルド設定（Kotlin DSL）
   - `settings.gradle.kts` — プロジェクト名
   - 解説: この構造は Gradle の規約。規約に従うことで設定が最小限になる
4. **3.3 build.gradle.kts の読み解き**
   - `plugins { application }` — 「これは実行可能なアプリケーションだ」と宣言
   - `repositories { mavenCentral() }` — ライブラリのダウンロード元
   - `application { mainClass.set(...) }` — `java` コマンドに渡すメインクラス
   - 解説: Step 1 で手動で指定していたことが宣言的に書ける
5. **3.4 Gradle Wrapper とは**
   - `gradle wrapper` を実行 → `gradlew`, `gradlew.bat`, `gradle/wrapper/` が生成される
   - `./gradlew --version` で確認
   - 解説: `gradlew` はプロジェクトに同梱する Gradle の起動スクリプト。チーム全員が同じバージョンの Gradle を使える。ローカルに Gradle をインストールしなくても動く
6. **3.5 ビルドと実行**
   - `./gradlew build` — コンパイル + テスト + JAR 作成
   - `./gradlew run` — アプリを実行
   - `tree build/` で成果物を確認:
     - `build/classes/java/main/handson/App.class` — Step 1 と同じ `.class` ファイル
     - `build/libs/step3-gradle-intro.jar` — JAR（.class をまとめたアーカイブ）
7. **3.6 Gradle がやっていることの正体**
   - `./gradlew build --info` で詳細ログ
   - ログの中に `javac` の呼び出しが含まれていることを確認
   - 解説: Gradle は結局 `javac` を呼んでいる。Step 1 の手動作業を自動化しているだけ
8. **まとめ** — Gradle は javac/java のラッパー + 依存管理 + 規約ベースの自動化

**Step 6: Commit**

```bash
git add runtime-handson/step3-gradle-intro/
git commit -m "docs: Step 3 — Gradle 導入ハンズオン"
```

---

### Task 5: Step 4 — Gradle でテスト

**Files:**
- Create: `runtime-handson/step4-gradle-test/README.md`
- Create: `runtime-handson/step4-gradle-test/app/build.gradle.kts`
- Create: `runtime-handson/step4-gradle-test/app/settings.gradle.kts`
- Create: `runtime-handson/step4-gradle-test/app/src/main/java/handson/Calculator.java`
- Create: `runtime-handson/step4-gradle-test/app/src/test/java/handson/CalculatorTest.java`

**Step 1: サンプルコード — Calculator.java**

```java
// runtime-handson/step4-gradle-test/app/src/main/java/handson/Calculator.java
package handson;

public class Calculator {
    public int add(int a, int b) {
        return a + b;
    }

    public int divide(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Division by zero");
        }
        return a / b;
    }
}
```

**Step 2: サンプルコード — CalculatorTest.java**

```java
// runtime-handson/step4-gradle-test/app/src/test/java/handson/CalculatorTest.java
package handson;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CalculatorTest {
    private final Calculator calc = new Calculator();

    @Test
    void addsTwoNumbers() {
        assertEquals(5, calc.add(2, 3));
    }

    @Test
    void dividesTwoNumbers() {
        assertEquals(4, calc.divide(12, 3));
    }

    @Test
    void throwsOnDivisionByZero() {
        assertThrows(ArithmeticException.class, () -> calc.divide(1, 0));
    }
}
```

**Step 3: build.gradle.kts**

```kotlin
// runtime-handson/step4-gradle-test/app/build.gradle.kts
plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("handson.Calculator")
}
```

**Step 4: settings.gradle.kts**

```kotlin
// runtime-handson/step4-gradle-test/app/settings.gradle.kts
rootProject.name = "step4-gradle-test"
```

**Step 5: 手順書 README.md を作成**

セクション構成:

1. **このステップで学ぶこと** — Gradle のテスト実行、テスト用 JVM が別プロセスで立つこと
2. **4.1 プロジェクト構造**
   - `src/main/java/` — プロダクションコード
   - `src/test/java/` — テストコード
   - `build.gradle.kts` の `dependencies` に JUnit 5 を追加していること
3. **4.2 テストを実行する**
   - `cd app && gradle wrapper && ./gradlew test`
   - `BUILD SUCCESSFUL` を確認
4. **4.3 テストレポートを見る**
   - `open build/reports/tests/test/index.html`（またはブラウザで開く手順）
   - テスト結果がHTMLレポートで見られることを確認
5. **4.4 テストを失敗させてみる**
   - `CalculatorTest.java` の `assertEquals(5, ...)` を `assertEquals(999, ...)` に変更
   - `./gradlew test` → FAIL
   - レポートで失敗内容を確認
   - 元に戻す
6. **4.5 テスト実行の仕組みを観察する**
   - ターミナルを2つ開く
   - ターミナル1: `./gradlew test --info` — 詳細ログを見る
   - ターミナル2: テスト実行中に `jps` を叩く
   - ログの中から「Executing test ... using JDK ...」を探す
   - `jps` の出力で `GradleDaemon` と `GradleWorkerMain`（テスト実行プロセス）が別々に見える
   - 解説: Gradle はテスト実行のために**別の JVM プロセス**を起動する。次の Step 5 で、この「テスト用 JVM」のバージョンを制御する
7. **まとめ** — Gradle のテスト実行は別 JVM。これが Toolchains の伏線

**Step 6: Commit**

```bash
git add runtime-handson/step4-gradle-test/
git commit -m "docs: Step 4 — Gradle テスト実行ハンズオン"
```

---

### Task 6: Step 5 — Gradle Toolchains

**Files:**
- Create: `runtime-handson/step5-toolchains/README.md`
- Create: `runtime-handson/step5-toolchains/app/build.gradle.kts`
- Create: `runtime-handson/step5-toolchains/app/settings.gradle.kts`
- Create: `runtime-handson/step5-toolchains/app/src/main/java/handson/App.java`
- Create: `runtime-handson/step5-toolchains/app/src/main/java/handson/UserRecord.java`
- Create: `runtime-handson/step5-toolchains/app/src/test/java/handson/AppTest.java`

**Step 1: サンプルコード — App.java（Java 11 互換）**

```java
// runtime-handson/step5-toolchains/app/src/main/java/handson/App.java
package handson;

public class App {
    public String getGreeting() {
        return "Hello from Java 11 compatible code!";
    }

    public static void main(String[] args) {
        System.out.println(new App().getGreeting());
        System.out.println("Java version: " + System.getProperty("java.version"));
    }
}
```

**Step 2: サンプルコード — UserRecord.java（Java 16+ の record、最初はコメントアウト）**

```java
// runtime-handson/step5-toolchains/app/src/main/java/handson/UserRecord.java
package handson;

// この record は Java 16 以上でしか使えない。
// Step 5.5 でコメントを外して Toolchains の効果を体験する。

// public record UserRecord(String name, int age) {}
```

**Step 3: サンプルコード — AppTest.java**

```java
// runtime-handson/step5-toolchains/app/src/test/java/handson/AppTest.java
package handson;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppTest {
    @Test
    void greetingIsNotNull() {
        assertNotNull(new App().getGreeting());
    }

    @Test
    void runsOnExpectedJavaVersion() {
        String version = System.getProperty("java.version");
        System.out.println("Test is running on Java: " + version);
        // Toolchains が Java 11 を指定しているなら、ここで 11 が表示されるはず
        assertTrue(version.startsWith("11"), "Expected Java 11, but got: " + version);
    }
}
```

**Step 4: build.gradle.kts（Toolchains なし — 最初の状態）**

```kotlin
// runtime-handson/step5-toolchains/app/build.gradle.kts
plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("handson.App")
}

// --- Step 5.2 で以下のコメントを外す ---
// java {
//     toolchain {
//         languageVersion.set(JavaLanguageVersion.of(11))
//     }
// }
```

**Step 5: settings.gradle.kts**

```kotlin
// runtime-handson/step5-toolchains/app/settings.gradle.kts
rootProject.name = "step5-toolchains"
```

**Step 6: 手順書 README.md を作成**

セクション構成:

1. **このステップで学ぶこと** — Gradle Toolchains で「Gradle の Java」と「アプリの Java」を分離
2. **5.1 現状確認 — Toolchains なし**
   - `java -version` → JDK 17 であることを確認
   - `cd app && gradle wrapper && ./gradlew test` → テスト失敗（`runsOnExpectedJavaVersion` が Java 17 で動くので `startsWith("11")` が false）
   - 解説: 今は Gradle もアプリも JDK 17 で動いている
3. **5.2 Toolchains を有効にする**
   - `build.gradle.kts` の末尾のコメントを外す:
     ```kotlin
     java {
         toolchain {
             languageVersion.set(JavaLanguageVersion.of(11))
         }
     }
     ```
   - `./gradlew build` を実行 — Gradle が JDK 11 を自動ダウンロードするログを観察
   - 解説: Gradle 自身は JDK 17 で動いたまま、コンパイルとテストだけ JDK 11 で実行する
4. **5.3 テストが Java 11 で動くことを確認**
   - `./gradlew test` → 今度は PASS（テスト内の `startsWith("11")` が true になる）
   - `./gradlew test --info | grep "using JDK"` でテスト JVM が 11 であることをログ確認
5. **5.4 Gradle 自体は JDK 17 のまま**
   - `./gradlew --version` → Gradle 自身は JDK 17 で動いている
   - 解説: Gradle デーモン（JDK 17）がコンパイル・テスト用に JDK 11 の JVM を別プロセスで起動
6. **5.5 Toolchains のガードレール体験**
   - `UserRecord.java` のコメントを外す（`record` は Java 16+ の機能）
   - `./gradlew build` → コンパイルエラー（Java 11 では `record` が使えない）
   - 解説: Toolchains が「このプロジェクトは Java 11 で動く」ことを保証している。本番が Java 11 なら、開発者が新機能をうっかり使ってもここで検出される
7. **5.6 Toolchains を外すとどうなるか**
   - `build.gradle.kts` の `java { toolchain { ... } }` を再度コメントアウト
   - `./gradlew build` → コンパイル成功（JDK 17 では `record` が使える）
   - `./gradlew test` → `runsOnExpectedJavaVersion` が失敗（Java 17 で動くため）
   - 解説: Toolchains なしだと「開発者のマシンにたまたま入っている JDK」でビルドされる。チームで環境がバラバラになり、本番と違う Java で動くリスクがある
8. **まとめ** — Toolchains は「このプロジェクトが動くべき Java バージョン」をビルド設定に宣言する仕組み

**Step 7: Commit**

```bash
git add runtime-handson/step5-toolchains/
git commit -m "docs: Step 5 — Gradle Toolchains ハンズオン"
```

---

### Task 7: 全体の整合性確認と最終コミット

**Step 1: ルート README.md にステップ間の導線を追記**

各ステップの README.md の末尾に「次のステップ」リンクがあることを確認。なければ追記。

**Step 2: ディレクトリ構造の最終確認**

```bash
tree runtime-handson/ -I 'gradle|.gradle|build'
```

設計ドキュメントの構成と一致していることを確認。

**Step 3: Commit**

```bash
git add runtime-handson/
git commit -m "docs: Java ランタイムハンズオン — 全体整合性確認"
```
