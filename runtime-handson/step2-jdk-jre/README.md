# Step 2: JDK と JRE の違いを体験

## このステップで学ぶこと

- JDK と JRE の違い
- JDK に含まれる開発ツール
- JRE だけでもアプリは動くこと
- なぜ本番には JRE だけで十分なのか

---

## 2.1 今の環境を確認する

まず、この Dev Container にどんな Java 環境が入っているか確認しましょう。

```bash
java -version
```

```
openjdk version "17.0.x" 2024-xx-xx
OpenJDK Runtime Environment Temurin-17.0.x+x (build 17.0.x+x)
OpenJDK 64-Bit Server VM Temurin-17.0.x+x (build 17.0.x+x, mixed mode, sharing)
```

Eclipse Temurin JDK 17 がインストールされています。次に、ツールの場所を確認します。

```bash
which javac
```

```
/usr/local/sdkman/candidates/java/current/bin/javac
```

```bash
which java
```

```
/usr/local/sdkman/candidates/java/current/bin/java
```

```bash
echo $JAVA_HOME
```

```
/usr/local/sdkman/candidates/java/current
```

Dev Container には **JDK 17** がプリインストールされています。JDK には「コンパイラなどの開発ツール」と「JVM（実行エンジン）」の両方が含まれています。

---

## 2.2 JDK に入っているツール

JDK には多くのツールが同梱されています。`$JAVA_HOME/bin` の中身を覗いてみましょう。

```bash
ls $JAVA_HOME/bin | head -20
```

```
jar
jarsigner
java
javac
javadoc
javap
jcmd
jconsole
jdb
jdeprscan
jdeps
jfr
jhsdb
jimage
jinfo
jlink
jmap
jmod
jpackage
jps
```

主要なツールをまとめます。

| ツール | 役割 |
|--------|------|
| `javac` | コンパイラ。ソースコード (.java) をバイトコード (.class) に変換する |
| `java` | JVM。バイトコードを実行する |
| `javap` | 逆アセンブラ。.class ファイルの中身を人間が読める形で表示する |
| `jps` | 実行中の Java プロセス一覧を表示する |
| `jstack` | スレッドダンプを取得する（各スレッドが何をしているか確認できる） |
| `jmap` | メモリマップを取得する（ヒープダンプの取得に使う） |
| `jar` | JAR アーカイブの作成・展開を行う |
| `jshell` | Java コードを対話的に実行する（REPL） |

### jshell を試す

`jshell` は Java 9 で導入された対話的実行ツール（REPL）です。コードを1行ずつ試せます。

```bash
jshell
```

```
|  Welcome to JShell -- Version 17.0.x
|  For an introduction type: /help intro

jshell>
```

いくつかコードを入力してみましょう。

```
jshell> System.out.println(1 + 1)
2

jshell> String.format("Hello %s", "JVM")
$2 ==> "Hello JVM"

jshell> /exit
|  Goodbye
```

jshell は Java のコードを対話的に試せるツールです。ちょっとした動作確認に便利ですが、**JDK にしか含まれていません**。

---

## 2.3 JRE-only 環境との比較

JDK と JRE の違いを実際に確認してみましょう。Docker を使って JRE だけの環境を用意します。

まず、JRE のみのコンテナイメージを取得します。

```bash
docker pull eclipse-temurin:17-jre
```

JRE コンテナで `java` コマンドを実行してみます。

```bash
docker run --rm eclipse-temurin:17-jre java -version
```

```
openjdk version "17.0.x" 2024-xx-xx
OpenJDK Runtime Environment Temurin-17.0.x+x (build 17.0.x+x)
OpenJDK 64-Bit Server VM Temurin-17.0.x+x (build 17.0.x+x, mixed mode, sharing)
```

`java`（JVM）は問題なく動きます。では、`javac`（コンパイラ）はどうでしょうか。

```bash
docker run --rm eclipse-temurin:17-jre javac -version
```

```
OCI runtime exec failed: exec failed: unable to start container process:
exec: "javac": executable file not found in $PATH: unknown
```

エラーになります。**JRE には `java`（JVM）はあるが、`javac`（コンパイラ）はありません。** JRE はあくまで「実行環境」であり、開発ツールは含まれていないのです。

---

## 2.4 .class ファイルは JRE だけで動く

Step 1 で学んだとおり、`javac` でコンパイルすると `.class` ファイル（バイトコード）が生成されます。この `.class` ファイルは JRE だけで実行できるのでしょうか? 試してみましょう。

Step 1 の `Hello.java` をコンパイルし、その出力を JRE コンテナで実行します。

```bash
cd /workspaces/*/runtime-handson/step1-javac-basics/src
javac -d /tmp/out Hello.java
```

コンパイル結果を確認します。

```bash
ls /tmp/out/
```

```
Hello.class
```

この `.class` ファイルを JRE コンテナにマウントして実行します。

```bash
docker run --rm -v /tmp/out:/app eclipse-temurin:17-jre java -cp /app Hello
```

```
Hello, JVM!
```

JRE だけで実行できました。コンパイル済みの `.class` ファイルがあれば、JVM（`java` コマンド）さえあれば動きます。

> **ポイント:** つまり、本番サーバーには JDK（コンパイラ等の開発ツール）をインストールする必要はありません。コンパイル済みのアプリケーションと JRE があれば十分です。

---

## 2.5 JDK 診断ツールを触る

JDK には本番トラブルシューティング用の診断ツールも含まれています。`jps` と `jstack` を実際に使ってみましょう。

### 準備: ターミナルを2つ開く

VS Code で **Terminal > New Terminal** を選択し、ターミナルを2つ用意してください。

### Terminal 1: SlowApp を起動する

```bash
cd /workspaces/*/runtime-handson/step2-jdk-jre/src
javac SlowApp.java
java SlowApp
```

```
Running... (press Ctrl+C to stop)
```

プログラムが起動し、停止せずに待機し続けます（`Thread.sleep` で無限に待機しています）。

### Terminal 2: jps でプロセスを確認する

もう一方のターミナルで、実行中の Java プロセスを確認します。

```bash
jps
```

```
123 SlowApp
456 Jps
```

`SlowApp` が実行中であることがわかります。先頭の数字がプロセス ID（PID）です。

### Terminal 2: jstack でスレッドダンプを取得する

`jps` で確認した PID を使って、スレッドダンプを取得します。

```bash
jstack <PID>
```

`<PID>` は `jps` で表示された SlowApp の PID に置き換えてください。例えば PID が 123 なら `jstack 123` です。

出力例（抜粋）:

```
"main" #1 prio=5 os_prio=0 cpu=12.34ms elapsed=5.67s tid=0x... nid=0x... waiting on condition [0x...]
   java.lang.Thread.State: TIMED_WAITING (sleeping)
        at java.lang.Thread.sleep(java.base@17.0.x/Native Method)
        at SlowApp.main(SlowApp.java:4)
```

`main` スレッドの状態が `TIMED_WAITING (sleeping)` であること、`SlowApp.java:4` の `Thread.sleep` で待機していることが読み取れます。

### Terminal 1: SlowApp を停止する

Terminal 1 に戻り、`Ctrl+C` で SlowApp を停止します。

> **ポイント:** `jps` と `jstack` は本番の Java アプリケーションが応答しなくなったときのトラブルシューティングに使う診断ツールです。これらは **JDK にしか含まれていません**。本番環境では JRE だけで運用するのが基本ですが、障害調査のために JDK をインストールすることもあります。

---

## 2.6 イメージサイズの比較

最後に、JDK イメージと JRE イメージのサイズを比較してみましょう。

JDK イメージをまだ取得していなければ、先に pull します。

```bash
docker pull eclipse-temurin:17-jdk
```

サイズを比較します。

```bash
docker images eclipse-temurin --format "table {{.Tag}}\t{{.Size}}"
```

```
TAG        SIZE
17-jdk     406MB
17-jre     212MB
```

JRE イメージは JDK イメージの **概ね半分程度のサイズ** です。

本番コンテナに JRE を使うメリット:

- **デプロイが速い** -- イメージのダウンロード・転送にかかる時間が短くなる
- **攻撃対象面が小さい** -- コンパイラや診断ツールが含まれないため、侵入された場合のリスクが減る
- **ディスク使用量が少ない** -- コンテナレジストリやノードのストレージを節約できる

---

## まとめ

```
JDK（Java Development Kit）
┌──────────────────────────────────┐
│  開発ツール                       │
│   javac, javap, jshell,          │
│   jps, jstack, jmap ...          │
│                                  │
│  ┌────────────────────────────┐  │
│  │ JRE（Java Runtime Env）    │  │
│  │  java (JVM)                │  │
│  │  標準ライブラリ              │  │
│  └────────────────────────────┘  │
└──────────────────────────────────┘
```

### このステップのポイント

- **JDK = JRE + 開発ツール**。JDK は開発に必要なすべてを含むフルセット
- **JRE だけでもコンパイル済みのアプリは動く**。実行に必要なのは JVM と標準ライブラリだけ
- **開発には JDK、本番には JRE** が基本方針。イメージサイズと攻撃対象面を削減できる
- **JDK の診断ツール**（jps, jstack 等）は障害調査に役立つ

---

**前のステップ:** [Step 1: javac/java の基礎](../step1-javac-basics/README.md)

**次のステップ:** [Step 3: Gradle 導入](../step3-gradle-intro/README.md)
