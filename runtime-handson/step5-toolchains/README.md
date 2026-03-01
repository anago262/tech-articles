# Step 5: Gradle Toolchains

## このステップで学ぶこと

- Gradle Toolchains の仕組み
- 「Gradle を動かす Java」と「アプリをビルド・テストする Java」を分離できること
- Toolchains がバージョン互換性のガードレールになること

---

## 5.1 現状確認 -- Toolchains なし

まず、現在の Java バージョンを確認します。

```bash
java -version
```

```
openjdk version "17.x.x" ...
```

JDK 17 がインストールされています。

`step5-toolchains` ディレクトリに移動して、Gradle Wrapper を生成し、テストを実行します。

```bash
cd step5-toolchains/app
gradle wrapper
./gradlew test
```

実行結果:

```
> Task :app:test FAILED

AppTest > runsOnExpectedJavaVersion() FAILED
    org.opentest4j.AssertionFailedError: Expected Java 11, but got: 17.x.x

1 tests completed, 1 failed

FAILURE: Build failed with an exception.
```

テストが失敗しました。`runsOnExpectedJavaVersion` テストは Java 11 での実行を期待していますが、実際には JDK 17 で動いています。

**なぜ失敗するのか?** 今はまだ Toolchains を有効にしていないので、Gradle もアプリも同じ JDK 17 で動いています。Step 4 で学んだ通り、Gradle はテスト用に別の JVM プロセスを起動しますが、同じ JDK 17 を使っています。

```
現在の状態:
  Gradle デーモン (JDK 17)
    └── コンパイル → JDK 17 の javac
    └── テスト実行 → JDK 17 の JVM  ← テストが「Java 11 じゃない!」と失敗
```

---

## 5.2 Toolchains を有効にする

`app/build.gradle.kts` を開き、末尾のコメントを外します。

変更前:

```kotlin
// --- Step 5.2 で以下のコメントを外す ---
// java {
//     toolchain {
//         languageVersion.set(JavaLanguageVersion.of(11))
//     }
// }
```

変更後:

```kotlin
// --- Step 5.2 で以下のコメントを外す ---
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}
```

たった3行の追加で、「このプロジェクトは Java 11 で動かすべき」と宣言しました。

ビルドを実行します:

```bash
./gradlew build
```

初回実行時、以下のようなログが表示されます:

```
> Compiling with toolchain '/path/to/jdk-11'.
```

Gradle が JDK 11 を自動的に検出（またはダウンロード）して、コンパイルとテスト実行に使用しています。

**ポイント:** Gradle 自身は JDK 17 で動いたまま、コンパイルとテスト実行だけ JDK 11 で行います。これが Toolchains の核心です。

---

## 5.3 テストが Java 11 で動くことを確認

テストを実行します:

```bash
./gradlew test
```

実行結果:

```
BUILD SUCCESSFUL in Xs
3 actionable tasks: 3 executed
```

テストが成功しました! `runsOnExpectedJavaVersion` テストが通ったということは、テストが Java 11 上で実行されたことを意味します。

さらに詳細なログで確認しましょう:

```bash
./gradlew test --info 2>&1 | grep -i "toolchain\|jdk"
```

出力例:

```
Compiling with toolchain '/path/to/jdk-11'.
```

テスト内の `System.getProperty("java.version")` が `"11.x.x"` を返すようになったため、テストが成功しました。

---

## 5.4 Gradle 自体は JDK 17 のまま

Gradle デーモン自体がどの JDK で動いているか確認します:

```bash
./gradlew --version
```

出力に `JVM: 17.x.x` と表示されます。

```
------------------------------------------------------------
Gradle x.x.x
------------------------------------------------------------

...
JVM:          17.x.x (Eclipse Adoptium ...)
...
```

つまり、2つの JDK が同時に使われています:

```
Gradle デーモン (JDK 17)          ← Gradle 自身を動かす JDK
  └── コンパイル → JDK 11 の javac  ← アプリをコンパイルする JDK
  └── テスト実行 → JDK 11 の JVM   ← テストを実行する JDK
```

Step 4 で「テスト実行は別 JVM プロセス」と学びましたが、Toolchains はさらにその先を行きます -- **別プロセスの JDK バージョンそのものを変えられる**のです。

---

## 5.5 Toolchains のガードレール体験

Toolchains の真価は「使ってはいけない機能を検出する」ことにあります。

`app/src/main/java/handson/UserRecord.java` を開き、コメントを外します。

変更前:

```java
// public record UserRecord(String name, int age) {}
```

変更後:

```java
public record UserRecord(String name, int age) {}
```

`record` は **Java 16 以上**で導入された機能です。ビルドしてみましょう:

```bash
./gradlew build
```

実行結果:

```
> Task :app:compileJava FAILED

error: records are not supported in -source 11
public record UserRecord(String name, int age) {}
       ^
  (use -source 16 or higher to enable records)

FAILURE: Build failed with an exception.
```

**コンパイルエラー!** Toolchains が Java 11 でコンパイルしているため、Java 16 以降の `record` 構文は使えません。

これが Toolchains の**ガードレール機能**です。もし Toolchains を設定していなければ、開発者の PC にインストールされた JDK 17 でコンパイルが通ってしまい、本番環境の Java 11 にデプロイしたときに初めてエラーになる -- という事故を防いでくれます。

> 「自分のマシン (JDK 17) では動くのに、本番 (Java 11) では動かない!」を防ぐ仕組み

確認が終わったら、`UserRecord.java` の変更を元に戻しておきましょう（再びコメントアウト）。

---

## 5.6 Toolchains を外すとどうなるか

最後に、Toolchains を無効にするとどうなるか確認します。

`app/build.gradle.kts` の `java { toolchain { ... } }` ブロックを再びコメントアウトします:

```kotlin
// java {
//     toolchain {
//         languageVersion.set(JavaLanguageVersion.of(11))
//     }
// }
```

ビルドを実行します:

```bash
./gradlew build
```

```
BUILD SUCCESSFUL
```

コンパイルは通ります（`UserRecord.java` はコメントアウト済みのため）。

テストを実行します:

```bash
./gradlew test
```

```
> Task :app:test FAILED

AppTest > runsOnExpectedJavaVersion() FAILED
    org.opentest4j.AssertionFailedError: Expected Java 11, but got: 17.x.x
```

テストが再び失敗しました。Toolchains を外したことで、テスト実行が JDK 17 に戻ったためです。

**Toolchains なしの問題点:** 全ての環境で同じ JDK バージョンがインストールされている保証はありません。開発者 A は JDK 17、開発者 B は JDK 21、CI サーバーは JDK 11 -- こうした環境差異によるビルドの不整合を、Toolchains は `build.gradle.kts` の宣言だけで解消します。

---

## まとめ

```
Toolchains あり:
  Gradle (JDK 17) ─── コンパイル ──→ JDK 11 の javac ✓
                  └── テスト実行 ──→ JDK 11 の JVM   ✓
                  └── Java 16+ の機能 → コンパイルエラー ✓（ガードレール）

Toolchains なし:
  Gradle (JDK 17) ─── コンパイル ──→ JDK 17 の javac（本番と違う!）
                  └── テスト実行 ──→ JDK 17 の JVM  （本番と違う!）
                  └── Java 16+ の機能 → コンパイル成功（本番で動かない!）
```

- **Gradle Toolchains** = 「このプロジェクトが使うべき Java バージョン」をビルド設定に宣言する仕組み
- Gradle 自体の Java とアプリの Java を**分離**できる
- 本番と異なる Java 機能をうっかり使うのを防ぐ**ガードレール**

---

## ハンズオン完了!

おめでとうございます! 全5ステップを完走しました。

このハンズオンで歩んだ道のりを振り返りましょう:

| ステップ | 学んだこと |
|---------|-----------|
| **Step 1: javac/java の基礎** | ソースコード → バイトコード → JVM 実行。全ての基礎 |
| **Step 2: JDK と JRE の違い** | JDK = 開発ツール一式、JRE = 実行環境のみ |
| **Step 3: Gradle 導入** | 手動の javac を自動化。ディレクトリ規約と Wrapper |
| **Step 4: Gradle でテスト** | テスト実行は別 JVM プロセス。GradleDaemon と GradleWorkerMain |
| **Step 5: Gradle Toolchains** | その別 JVM のバージョンを制御。ビルド設定による再現性の保証 |

Step 1 の `javac Hello.java` から始まり、Step 5 の Toolchains まで -- Java のランタイムとビルドシステムの仕組みを、手を動かしながら体験してきました。ここで得た知識は、Java プロジェクトの開発環境構築やトラブルシューティングで必ず役に立ちます。

---

**前のステップ:** [Step 4: Gradle でテスト](../step4-gradle-test/README.md)

**[トップページに戻る](../README.md)**
