# Step 3: Gradle 導入

## このステップで学ぶこと

- Gradle の役割（javac/java の自動化）
- Gradle のディレクトリ規約
- build.gradle.kts の読み方
- Gradle Wrapper の仕組み

---

## 3.1 Step 1 の振り返り -- 手動コンパイルの面倒さ

Step 1 では、以下のようにコンパイルと実行を手動で行いました。

```bash
javac -d ../out *.java        # ソースファイルを列挙してコンパイル
java -cp ../out Main           # クラスパスを指定して実行
```

これで学ぶには十分ですが、実際の開発で以下のような問題に直面します。

- **ファイルが増える** -- 数百ファイルを手で列挙するのは非現実的
- **外部ライブラリ** -- JAR ファイルをダウンロードしてクラスパスに追加する作業が必要
- **再現性** -- 「自分の環境では動くが他の人の環境では動かない」問題

これらを解決するのが **ビルドツール** です。Java の世界では **Gradle** が現在の主流です。

---

## 3.2 プロジェクト構造を確認する

ハンズオンのルートディレクトリから、`step3-gradle-intro` に移動して構造を確認しましょう。

```bash
cd step3-gradle-intro
```

> **ヒント:** 別のステップのディレクトリにいる場合は、`cd ..` や `cd ../..` でルートに戻ってから移動してください。`ls` でステップ一覧が見えればルートにいます。

```bash
find app -type f
```

```
app/build.gradle.kts
app/settings.gradle.kts
app/src/main/java/handson/App.java
```

各ファイルの役割を整理します。

| ファイル | 役割 |
|---|---|
| `app/src/main/java/handson/App.java` | メインのソースコード |
| `app/build.gradle.kts` | ビルド設定（何をコンパイルし、どう実行するか） |
| `app/settings.gradle.kts` | プロジェクト名の定義 |

ソースコードの配置場所に注目してください。`src/main/java/` というパスは **Gradle の規約（convention）** です。この規約に従えば、「ソースコードはどこにあるか」を設定ファイルに書く必要がありません。規約に従うことで設定が最小限になる -- これが Gradle の設計思想です。

---

## 3.3 build.gradle.kts の読み解き

`app/build.gradle.kts` の中身を見てみましょう。

```kotlin
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

わずか数行ですが、それぞれ重要な意味を持っています。

### `plugins { application }`

「このプロジェクトは実行可能なアプリケーションです」と宣言しています。この宣言により `run` タスク（アプリケーションの実行）が使えるようになります。

### `repositories { mavenCentral() }`

外部ライブラリのダウンロード元として **Maven Central リポジトリ** を指定しています。Maven Central は Java ライブラリの公開リポジトリで、世界中の開発者が公開したライブラリが集まっています。今回は外部ライブラリを使いませんが、後のステップで使うための準備です。

### `application { mainClass.set("handson.App") }`

`java` コマンドに渡すメインクラスを指定しています。Step 1 では `java -cp ../out Main` とコマンドラインでクラス名を指定していましたが、Gradle ではこのように設定ファイルに **宣言的に** 書きます。

> **ポイント:** Step 1 で手動指定していた「何をコンパイルするか」「どのクラスを実行するか」が、設定ファイルに宣言するだけで済むようになります。

---

## 3.4 Gradle Wrapper を生成する

`app/` ディレクトリに移動して、Gradle Wrapper を生成します。

```bash
cd app
gradle wrapper
```

> **前提:** この手順には `gradle` コマンド（Gradle 本体）が必要です。この Dev Container には SDKMAN 経由で Gradle がプリインストールされているので、そのまま使えます。Wrapper を一度生成すれば、以降は `gradle` コマンドは不要になります。

生成されたファイルを確認しましょう。

```bash
ls -la gradlew gradlew.bat gradle/
```

```
-rwxr-xr-x  gradlew
-rw-r--r--  gradlew.bat

gradle/:
wrapper/
```

```bash
ls gradle/wrapper/
```

```
gradle-wrapper.jar
gradle-wrapper.properties
```

生成されたファイルの役割を整理します。

| ファイル | 役割 |
|---|---|
| `gradlew` | Linux/Mac 用の Gradle 起動スクリプト |
| `gradlew.bat` | Windows 用の Gradle 起動スクリプト |
| `gradle/wrapper/gradle-wrapper.properties` | 使用する Gradle バージョンの指定 |
| `gradle/wrapper/gradle-wrapper.jar` | Wrapper の実行エンジン（Gradle をダウンロードする仕組み） |

動作確認します。

```bash
./gradlew --version
```

```
------------------------------------------------------------
Gradle x.x.x
------------------------------------------------------------
...
```

バージョン情報が表示されれば成功です。

### なぜ Wrapper が必要なのか

`gradlew`（Gradle Wrapper）はプロジェクトに同梱する Gradle 起動スクリプトです。以下のメリットがあります。

- **チーム全員が同じ Gradle バージョンを使える** -- `gradle-wrapper.properties` にバージョンが固定されている
- **ローカルに Gradle をインストールしなくても動く** -- 初回実行時に指定バージョンの Gradle を自動ダウンロードする
- **CI/CD 環境でもそのまま動く** -- `./gradlew build` だけで済む

> **実務のルール:** プロジェクトでは常に `gradle` ではなく `./gradlew` を使います。`gradle` コマンドを直接使うのは Wrapper を生成するときだけです。

---

## 3.5 ビルドと実行

### ビルド

```bash
./gradlew build
```

```
BUILD SUCCESSFUL in Xs
```

`BUILD SUCCESSFUL` と表示されればビルド成功です。

### 実行

```bash
./gradlew run
```

```
> Task :run
Hello from Gradle!

BUILD SUCCESSFUL in Xs
```

`Hello from Gradle!` が表示されました。Step 1 では `javac` → `java` と2段階で実行していましたが、Gradle では `./gradlew run` の一発で完了します。

### 成果物を確認する

ビルドで何が生成されたか確認してみましょう。

```bash
find build/classes -name "*.class"
```

```
build/classes/java/main/handson/App.class
```

Step 1 と同じ `.class` ファイル（バイトコード）が生成されています。出力先が `build/classes/java/main/` という Gradle 規約のディレクトリになっただけです。

```bash
ls build/libs/
```

```
step3-gradle-intro.jar
```

**JAR ファイル** も生成されています。JAR（Java ARchive）は `.class` ファイルをまとめた ZIP アーカイブです。複数のクラスファイルを1つのファイルにまとめて配布できるため、実際のアプリケーション配布で使われます。

---

## 3.6 Gradle がやっていることの正体

Gradle は魔法ではありません。内部で何をしているか確認してみましょう。

```bash
./gradlew compileJava --info 2>&1 | grep -i "compil"
```

ログの中に、javac を呼び出している記述が確認できます。

つまり、**Gradle は結局 javac を呼んでいる** のです。Step 1 で手動実行していた作業を、Gradle が自動化しているにすぎません。

Gradle が自動化してくれること:

1. **コンパイル** -- `src/main/java/` 以下のソースを `javac` でコンパイル
2. **出力先管理** -- `.class` ファイルを `build/` に整理して出力
3. **JAR 生成** -- `.class` ファイルをまとめて JAR アーカイブを作成
4. **実行** -- 正しいクラスパスと `mainClass` を指定して `java` を起動
5. **依存管理** -- 外部ライブラリのダウンロードとクラスパス設定（後のステップで体験）

> **ポイント:** ビルドツールの本質は「手作業の自動化」です。javac/java の仕組みを理解していれば、Gradle がやっていることの意味がわかります。Step 1 の知識がここで活きてきます。

---

## まとめ

```
ソースコード (.java)
    | ./gradlew build（Gradle が javac を実行）
    v
バイトコード (.class) + JAR
    | ./gradlew run（Gradle が java を実行）
    v
実行結果
```

### このステップのポイント

- **Gradle は javac/java を自動化するビルドツール** -- 手動のコンパイル・実行を設定ファイルで宣言的に管理する
- **`src/main/java/` はディレクトリ規約** -- 規約に従えば設定が最小限で済む
- **`build.gradle.kts`** でプラグイン・リポジトリ・メインクラスを宣言的に記述する
- **`gradlew`（Gradle Wrapper）** でチーム全員が同じ Gradle バージョンを使える
- **ビルド成果物は `build/` ディレクトリ** に出力される（`.class` ファイルと JAR）

---

**前のステップ:** [Step 2: JDK と JRE の違い](../step2-jdk-jre/README.md)
**次のステップ:** [Step 4: Gradle でテスト](../step4-gradle-test/README.md)
