# Step 4: Gradle でテスト

## このステップで学ぶこと

- Gradle でのテスト実行の仕組み
- JUnit 5 テストの書き方と実行
- テストレポートの見方
- Gradle がテスト用に別の JVM プロセスを起動すること

---

## 4.1 プロジェクト構造

まず `step4-gradle-test` ディレクトリに移動します。

```bash
cd step4-gradle-test
```

ディレクトリ構造を確認しましょう。

```
step4-gradle-test/
├── settings.gradle.kts
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/java/handson/
        │   └── Calculator.java      ← プロダクションコード
        └── test/java/handson/
            └── CalculatorTest.java   ← テストコード
```

`src/main/java` にプロダクションコード、`src/test/java` にテストコードを配置します。これは Gradle（と Maven）の標準的なディレクトリ構成です。

### build.gradle.kts の新しい部分

```kotlin
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
```

- **`testImplementation(...)`** -- テスト用の依存ライブラリを宣言しています。JUnit 5（Jupiter）をテストフレームワークとして使います。`testImplementation` はテストコードのコンパイル時・実行時のみ使われ、プロダクションコードには含まれません。
- **`tasks.test { useJUnitPlatform() }`** -- JUnit 5 のテストランナー（JUnit Platform）を使う設定です。これがないと Gradle はテストを検出できません。

Step 1 では `javac` の `-cp` オプションで手動で classpath を管理していましたが、Gradle が依存ライブラリのダウンロードから classpath の設定まで全て自動で解決してくれます。

---

## 4.2 テストを実行する

まず Gradle Wrapper を生成し、テストを実行します。

```bash
cd app
gradle wrapper
./gradlew test
```

実行結果:

```
BUILD SUCCESSFUL in Xs
3 actionable tasks: 3 executed
```

3つのテストが全てパスしました。

`./gradlew test` は内部で以下の3段階を実行しています:

1. **プロダクションコードをコンパイル** (`compileJava`) -- `Calculator.java` → `Calculator.class`
2. **テストコードをコンパイル** (`compileTestJava`) -- `CalculatorTest.java` → `CalculatorTest.class`
3. **テストを実行** (`test`) -- JUnit 5 でテストメソッドを検出・実行

---

## 4.3 テストレポートを見る

`./gradlew test` を実行すると、Gradle は自動的に HTML 形式のテストレポートを生成します。

```bash
open build/reports/tests/test/index.html
```

> macOS 以外の場合は、上記パスをブラウザで直接開いてください。

`./gradlew test` の出力に `HTML test report generated at` というパスが表示される場合もあります（`--info` オプション付きで表示されやすくなります）。

レポートには以下の情報が含まれます:

- テストの成功/失敗の一覧
- 各テストの実行時間
- 失敗時のエラーメッセージとスタックトレース

CI/CD パイプラインでテスト結果を確認する際にこの HTML レポートが役立ちます。

---

## 4.4 テストを失敗させてみる

テストが失敗するとどうなるか体験しましょう。

`CalculatorTest.java` を編集して、わざと間違った期待値を設定します:

```java
// Before
assertEquals(5, calc.add(2, 3));

// After（期待値を 999 に変更）
assertEquals(999, calc.add(2, 3));
```

テストを実行します:

```bash
./gradlew test
```

実行結果:

```
> Task :app:test FAILED

CalculatorTest > addsTwoNumbers() FAILED
    org.opentest4j.AssertionFailedError: expected: <999> but was: <5>

3 tests completed, 1 failed

FAILURE: Build failed with an exception.
```

`expected: <999> but was: <5>` -- 期待値は 999 だが実際の結果は 5 だった、というエラーです。

テストレポート (`build/reports/tests/test/index.html`) を再度開くと、失敗したテストが赤く表示されていることを確認できます。

確認が終わったら、変更を元に戻しておきましょう:

```java
assertEquals(5, calc.add(2, 3));
```

テストがコードの品質を守る仕組みとして機能していることが分かります。意図しない変更を加えた場合、テストが即座に検出してくれます。

---

## 4.5 テスト実行の仕組みを観察する

ここからが本ステップの核心です。Gradle がテストをどのように実行しているか、プロセスレベルで観察しましょう。

### ターミナルを2つ開く

**ターミナル 1** で verbose モードでテストを実行します:

```bash
./gradlew test --info
```

**ターミナル 2** で、テスト実行中に以下のコマンドを入力します:

```bash
jps
```

### --info 出力を観察する

`--info` オプション付きの出力には、以下のような行が含まれます:

```
Starting process 'Gradle Test Executor 1'.
  Working directory: ...
  Command: /path/to/java ...
```

Gradle がテスト実行のために別のプロセスを起動していることが分かります。

### jps 出力を観察する

`jps` の出力には、以下のようなプロセスが表示されます:

```
12345 GradleDaemon
12346 GradleWorkerMain
```

- **GradleDaemon** -- Gradle 自身のプロセスです。ビルドスクリプトの解析やタスクの実行制御を行います。
- **GradleWorkerMain** -- テストを実際に実行するプロセスです。Gradle が生成した別の JVM プロセスです。

### なぜ別プロセスなのか

Gradle はテスト実行を**別の JVM プロセス（フォークプロセス）**で行います。

```
┌─────────────────────┐     ┌─────────────────────────┐
│   GradleDaemon      │     │   GradleWorkerMain      │
│   (JDK 17)          │────>│   (JDK 17)              │
│                     │     │                         │
│   ビルドスクリプト   │     │   Calculator.class      │
│   の解析・実行       │     │   CalculatorTest.class   │
│                     │     │   JUnit 5               │
└─────────────────────┘     └─────────────────────────┘
    Gradle 自身                テスト実行用プロセス
```

今は両方とも同じ JDK 17 を使っていますが、これが分離されていることが重要です。次の Step 5 では、**テスト用 JVM のバージョンだけを変更する**ことを学びます。

---

## まとめ

- `./gradlew test` でテスト実行。JUnit 5 がテストフレームワーク
- テスト結果は HTML レポート (`build/reports/tests/test/index.html`) で確認可能
- Gradle はテスト実行のために**別の JVM プロセス**を起動する
  - `GradleDaemon`: Gradle 自身のプロセス
  - `GradleWorkerMain`: テストを実行するプロセス
- この「テスト用 JVM」のバージョンを制御するのが次の Step 5 の **Toolchains**

---

**前のステップ:** [Step 3: Gradle 導入](../step3-gradle-intro/README.md)
**次のステップ:** [Step 5: Gradle Toolchains](../step5-toolchains/README.md)
