# Step 1: javac/java の基礎

## このステップで学ぶこと

- ソースコード (.java) をコンパイルしてバイトコード (.class) に変換する流れ
- バイトコードの正体
- 複数ファイルのコンパイル
- クラスパスの概念

---

## 1.1 最初のコンパイルと実行

まず、ソースディレクトリに移動します。

```bash
cd step1-javac-basics/src
```

`Hello.java` をコンパイルします。

```bash
javac Hello.java
```

`javac` は **Java Compiler** です。人間が書いたソースコード (.java) を、JVM が理解できるバイトコード (.class) に変換します。

コンパイルが成功したか確認しましょう。

```bash
ls *.class
```

```
Hello.class
```

`Hello.class` が生成されていれば成功です。次に、これを実行します。

```bash
java Hello
```

```
Hello, JVM!
```

`java` コマンドは JVM（Java Virtual Machine）を起動し、指定されたクラスの `main` メソッドを実行します。

> **注意:** `java Hello.class` ではなく `java Hello` と拡張子なしで指定します。`java` コマンドに渡すのはファイル名ではなくクラス名です。

---

## 1.2 バイトコードを覗く

コンパイルで生成された `.class` ファイルの中身を覗いてみましょう。`javap` はバイトコードを人間が読める形に逆アセンブルするツールです。

```bash
javap -c Hello
```

出力例:

```
Compiled from "Hello.java"
public class Hello {
  public Hello();
    Code:
       0: aload_0
       1: invokespecial #1                  // Method java/lang/Object."<init>":()V
       4: return

  public static void main(java.lang.String[]);
    Code:
       0: getstatic     #7                  // Field java/lang/System.out:Ljava/io/PrintStream;
       3: ldc           #13                 // String Hello, JVM!
       5: invokevirtual #15                 // Method java/io/PrintStream.println:(Ljava/lang/String;)V
       8: return
}
```

`Code:` 以下に並んでいるのが JVM への命令列です。

- `getstatic` -- 静的フィールド（ここでは `System.out`）を取得する
- `ldc` -- 定数（ここでは文字列 `"Hello, JVM!"`）をスタックにロードする
- `invokevirtual` -- メソッド（ここでは `println`）を呼び出す

これらは JVM が理解する命令で「オペコード」と呼ばれます。人間が日常的に読むものではありませんが、「.class ファイルにはこういった命令が入っている」ことを知っておくことが重要です。

> **ポイント:** このバイトコードは OS に依存しません。Windows でコンパイルした .class ファイルを Linux の JVM でそのまま実行できます。これが "Write Once, Run Anywhere" と呼ばれる Java の特徴です。

---

## 1.3 複数ファイルのコンパイル

実際のプロジェクトでは複数のファイルが互いに依存します。`Main.java` は `Greeter.java` を使っています。

まとめてコンパイルしてみましょう。

```bash
javac Main.java Greeter.java
```

実行します。

```bash
java Main
```

```
Hello, World!
```

`Main` が `Greeter` を使って挨拶を生成し、それを出力しています。

### 依存関係の自動解決を試す

実は、`Main.java` だけを指定してもコンパイルできます。

```bash
rm -f *.class          # 一度クリーンアップ
javac Main.java
```

`javac` は `Main.java` が `Greeter` クラスを参照していることを検出し、`Greeter.java` も自動的にコンパイルします。

```bash
ls *.class
```

```
Greeter.class  Main.class
```

`Main.class` と `Greeter.class` の両方が生成されていることを確認できます。`javac` がソースコードの依存関係を解決してくれるのです。

---

## 1.4 出力先ディレクトリとクラスパス

ここまではソースファイルと同じディレクトリに .class ファイルが出力されていました。実際の開発では、ソースコードとコンパイル結果を分離するのが一般的です。

まず、既存の .class ファイルをクリーンアップします。

```bash
rm -f *.class
```

出力先ディレクトリを作成し、そこにコンパイル結果を出力します。

```bash
mkdir -p ../out
javac -d ../out *.java
```

`-d` オプションは .class ファイルの出力先ディレクトリを指定します。

```bash
ls ../out/
```

```
Greeter.class  Hello.class  Main.class
```

3 つの .class ファイルがすべて `out/` ディレクトリに出力されています。

では、この状態で実行してみましょう。

```bash
java -cp ../out Main
```

```
Hello, World!
```

`-cp`（`-classpath` の省略形）は **クラスパス** を指定するオプションです。クラスパスとは、「.class ファイルを探す場所」を JVM に教える仕組みです。

ここでは `../out` を指定することで、「`out/` ディレクトリの中から `Main.class` を探して実行してください」と JVM に伝えています。

> **ポイント:** コンパイルしたファイルと実行するファイルが別の場所にあっても、クラスパスで正しい場所を指示すれば問題なく動作します。

---

## 1.5 クラスパスを間違えてみる

クラスパスの重要性を体感するために、意図的に間違えてみましょう。

現在 `src/` ディレクトリにいますが、.class ファイルは `out/` にあります。クラスパスを指定せずに実行するとどうなるでしょうか。

```bash
java Main
```

```
Error: Could not find or load main class Main
Caused by: java.lang.ClassNotFoundException: Main
```

カレントディレクトリを明示的に指定しても同じです。

```bash
java -cp . Main
```

```
Error: Could not find or load main class Main
Caused by: java.lang.ClassNotFoundException: Main
```

どちらもエラーになります。JVM は指定された場所（クラスパス）にしか .class ファイルを探しに行きません。クラスパスを指定しない場合はカレントディレクトリがデフォルトになりますが、`src/` には .class ファイルがないためエラーになります。

正しいパスを指定すれば動きます。

```bash
java -cp ../out Main
```

```
Hello, World!
```

> **教訓:** `ClassNotFoundException` や `Could not find or load main class` というエラーに遭遇したら、まずクラスパスの設定を疑いましょう。

---

## まとめ

```
ソースコード (.java)
    | javac（コンパイル）
    v
バイトコード (.class)
    | java -cp <場所>（JVM 起動）
    v
実行結果
```

### このステップのポイント

- **javac** = コンパイラ。ソースコード (.java) をバイトコード (.class) に変換する
- **java** = JVM（実行エンジン）。バイトコードを読み取って実行する
- **.class ファイル** はバイトコードであり、OS に依存しない
- **クラスパス** (-cp) は JVM に .class ファイルの場所を教える仕組み。正しく指定しないと `ClassNotFoundException` になる

---

**次のステップ:** [Step 2: JDK と JRE の違いを体験](../step2-jdk-jre/README.md)
