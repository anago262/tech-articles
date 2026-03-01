# claude-mem をコンテナ内で完全ローカル動作させるまでの全記録

## はじめに：この記事で分かること

claude-mem は、Claude Code（Anthropic の AI コーディング CLI）にセッションをまたいだ長期記憶を与えるプラグインです。通常はホストマシンに直接インストールして使いますが、Dev Container（開発用コンテナ）の中で動かそうとすると、依存関係の多さとコンテナ特有の制約から簡単には動きません。

この記事では、[devcontainer-claude-code](https://github.com/anago262/devcontainer-claude-code) プロジェクトで claude-mem をコンテナ内に完全に封じ込めて動作させるまでに行った全ての工夫を、Git の履歴を追いながら時系列で解説します。

### 対象読者

- Claude Code を Dev Container で使っている、または使いたい人
- claude-mem に興味があるが、セットアップの複雑さに困っている人
- コンテナ内でバックグラウンドサービスを管理する方法を学びたい人

### claude-mem のアーキテクチャをまず理解する

claude-mem は単なる MCP サーバーではありません。以下の 5 つのコンポーネントが連携して動きます：

```
┌─────────────────────────────────────────────────┐
│  Claude Code                                     │
│                                                   │
│  ┌───────────┐   ┌──────────────────────────┐    │
│  │ MCP Server │   │ Hooks (4種)              │    │
│  │ (検索API)  │   │  SessionStart            │    │
│  │            │   │  UserPromptSubmit        │    │
│  └─────┬─────┘   │  PostToolUse             │    │
│        │          │  Stop                    │    │
│        │          └───────────┬──────────────┘    │
│        │                      │                   │
│        ▼                      ▼                   │
│  ┌──────────────────────────────────┐             │
│  │  Worker Service (port 37777)     │             │
│  │  - 観察記録の処理                 │             │
│  │  - セッション管理                 │             │
│  │  - ベクトル埋め込み               │             │
│  └──────────────┬───────────────────┘             │
│                 │                                  │
│                 ▼                                  │
│  ┌──────────────────────────────────┐             │
│  │  ChromaDB (port 8100)            │             │
│  │  - ベクトルデータベース            │             │
│  │  - ONNX モデルで埋め込み生成       │             │
│  └──────────────────────────────────┘             │
└─────────────────────────────────────────────────┘
```

- **MCP サーバー**: Claude Code が `search`、`timeline`、`get_observations` などのツールで記憶を検索する窓口
- **Hooks（フック）**: Claude Code のライフサイクルイベント（セッション開始、ツール使用後、終了時など）に連動して、自動的に観察を記録するトリガー
- **Worker Service**: HTTP API（ポート 37777）で動くデーモン。フックからの観察データを処理し、ChromaDB に保存する
- **ChromaDB**: ベクトルデータベース。テキストを数値ベクトルに変換して類似検索を可能にする
- **ONNX 埋め込みモデル（all-MiniLM-L6-v2）**: テキストをベクトルに変換する AI モデル。ChromaDB が内部で使う

ホストマシンでは「ChromaDB は Docker コンテナで動かし、claude-mem はホストに入れる」という構成が一般的ですが、Dev Container 内ではそれ自体がコンテナなので、**全てをコンテナプロセスとして動かす**必要があります。

---

## Phase 1：ベースイメージの出発点

### 初期の Dockerfile（コミット `b0c854e`）

プロジェクト最初の Dockerfile には claude-mem の痕跡はありませんでした：

```dockerfile
FROM node:20

# 基本的な開発ツール（git, zsh, fzf, vim, nano など）
RUN apt-get update && apt-get install -y --no-install-recommends \
  less git procps sudo fzf zsh man-db unzip gnupg2 gh \
  iptables ipset iproute2 dnsutils aggregate jq nano vim \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

USER node

# Claude Code のインストール
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}

# Playwright（ブラウザ自動化）
USER root
RUN npx -y playwright install-deps chromium
USER node
RUN npx -y playwright install chromium
```

この時点では Claude Code と Playwright だけのシンプルな構成でした。MCP サーバーのテンプレートも 3 つだけ：

```json
{
  "mcpServers": {
    "spec-workflow": { "command": "npx", "args": ["-y", "@pimzino/spec-workflow-mcp@latest", "/workspace"] },
    "context7":     { "command": "npx", "args": ["-y", "@upstash/context7-mcp@latest"] },
    "playwright":   { "command": "npx", "args": ["-y", "@playwright/mcp@latest"] }
  }
}
```

起動コマンドもファイアウォールの初期化だけでした：

```json
"postStartCommand": "sudo /usr/local/bin/init-firewall.sh"
```

---

## Phase 2：claude-mem スタックの統合（コミット `6c4a40f`）

ここが最大の変更です。1 コミットで 10 ファイル、492 行の追加が行われました。

### 2.1 依存関係のインストール

claude-mem を動かすには、Node.js だけでなく Python と Bun も必要です。

```dockerfile
# Python パッケージマネージャ（ChromaDB のインストールに必要）
RUN apt-get update && apt-get install -y --no-install-recommends \
  ... python3-pip \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

# uv/uvx（Serena MCP サーバーの実行に必要）
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/

# ChromaDB（claude-mem のベクトル検索バックエンド）
RUN pip3 install --break-system-packages chromadb

# Bun（claude-mem の Worker Service ランタイム）
RUN curl -fsSL https://bun.sh/install | bash
ENV PATH="/home/node/.bun/bin:$PATH"
```

**なぜ `--break-system-packages` が必要か：** Debian/Ubuntu の新しいバージョンでは、システム Python の `pip install` がデフォルトで拒否されます。コンテナ内では仮想環境を作る意味がないので、このフラグで直接インストールします。

**なぜ Bun か：** claude-mem の Worker Service は内部で Bun をランタイムとして使用します。Node.js でも動きますが、claude-mem の `worker-service.cjs` は `bun` コマンドでの実行を前提としています。

### 2.2 claude-mem 本体のインストールとディレクトリ構造

```dockerfile
# claude-mem をグローバルインストール
RUN npm install -g claude-mem
ENV CLAUDE_MEM_SCRIPTS="/usr/local/share/npm-global/lib/node_modules/claude-mem/plugin/scripts"

# ディレクトリ構造とシンボリックリンクの作成
RUN mkdir -p /home/node/.claude-mem /home/node/.claude/plugins/marketplaces/thedotmack \
  && ln -s /usr/local/share/npm-global/lib/node_modules/claude-mem/plugin \
     /home/node/.claude/plugins/marketplaces/thedotmack/plugin
```

**ここが最初の壁でした。** claude-mem は Claude Code のプラグインシステムを使います。ホストでは `claude plugins install thedotmack/claude-mem` でインストールしますが、このコマンドは内部で Claude Code の認証を要求するため、Docker ビルド中（非インタラクティブ）では実行できません。

解決策は手動でのシンボリックリンク作成です：

```
claude-mem の実体
  /usr/local/share/npm-global/lib/node_modules/claude-mem/plugin/
    ├── scripts/
    │   ├── mcp-server.cjs      ← MCP サーバー
    │   ├── worker-service.cjs  ← Worker デーモン
    │   └── bun-runner.js       ← Bun ラッパー
    ├── hooks/                  ← フック定義
    └── package.json

Claude Code が期待するパス（シンボリックリンクで解決）
  /home/node/.claude/plugins/marketplaces/thedotmack/plugin → 上記実体へのリンク
```

### 2.3 ONNX 埋め込みモデルの事前ダウンロード

```dockerfile
# ChromaDB の ONNX 埋め込みモデルを事前ダウンロード
RUN python3 -c "from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2; ef = ONNXMiniLM_L6_V2(); ef(['warmup'])"
```

**なぜビルド時にダウンロードするのか：** このモデル（all-MiniLM-L6-v2、約 80MB）は、ChromaDB が初めてテキストをベクトルに変換する際に Hugging Face からダウンロードされます。しかし、Dev Container にはファイアウォールがあり、Hugging Face のドメインがホワイトリストに入っていなければダウンロードがブロックされます。ビルド時に済ませておけば、実行時のネットワーク制限を気にする必要がありません。

`ef(['warmup'])` で実際にテキストを処理させることで、モデルのダウンロードだけでなく ONNX ランタイムの初期化も完了させています。

### 2.4 起動スクリプト：3 つのサービスを順番に立ち上げる

Dev Container の起動時（`postStartCommand`）に 3 つのスクリプトを順番に実行します：

```json
"postStartCommand": "sudo /usr/local/bin/init-firewall.sh; /usr/local/bin/init-claude-mem-settings.sh; /usr/local/bin/start-chromadb.sh; /usr/local/bin/start-claude-mem-worker.sh"
```

起動順序には意味があります：

1. **`init-claude-mem-settings.sh`** — 設定ファイルの生成とフック登録
2. **`start-chromadb.sh`** — ChromaDB の起動（Worker が依存するため先に起動）
3. **`start-claude-mem-worker.sh`** — Worker Service の起動

#### スクリプト 1：`init-claude-mem-settings.sh`（設定初期化）

このスクリプトは 4 つの仕事をします：

**仕事 1：ChromaDB 接続設定の生成**

```bash
cat > "$SETTINGS_FILE" << 'EOF'
{
  "CLAUDE_MEM_CHROMA_MODE": "remote",
  "CLAUDE_MEM_CHROMA_HOST": "127.0.0.1",
  "CLAUDE_MEM_CHROMA_PORT": "8100",
  "CLAUDE_MEM_CHROMA_SSL": "false",
  "CLAUDE_MEM_PYTHON_VERSION": "3.11"
}
EOF
```

`"remote"` モードに設定しているのがポイントです。claude-mem には `"local"` モードもあり、ChromaDB をサブプロセスとして起動しますが、接続が不安定でした。`"remote"` にすることで、別プロセスとして起動済みの ChromaDB に HTTP で接続します。

**仕事 2：プラグインマーケットプレイス登録**

Claude Code はプラグインを `known_marketplaces.json` で管理しています。ここに `thedotmack`（claude-mem の作者名）を登録しないと、シンボリックリンクがあってもフックが発見されません：

```python
data['thedotmack'] = {
    'source': {'source': 'local', 'path': '$PLUGIN_DIR'},
    'installLocation': '$PLUGIN_DIR',
    'lastUpdated': '...'
}
```

**仕事 3：npm 依存関係のインストール**

プラグインディレクトリに `node_modules` がなければ `npm install` を実行します。ボリュームマウントでプラグインディレクトリが上書きされた場合の復旧措置です。

**仕事 4：Claude Code フックの登録**

claude-mem の核心機能は「フック」です。4 つのイベントにフックを登録します：

| イベント | タイミング | claude-mem の動作 |
|----------|-----------|-----------------|
| `SessionStart` | セッション開始時 | Worker デーモン起動 + 過去コンテキスト読み込み |
| `UserPromptSubmit` | ユーザーがプロンプトを送信した時 | セッション初期化 |
| `PostToolUse` | ツール使用後 | 観察（ファイル読み書き、コマンド実行等）の記録 |
| `Stop` | セッション終了時 | セッション要約の生成 + セッション完了処理 |

これらのフックは Claude Code の `settings.json` に書き込まれます。スクリプトは既存の設定を壊さないよう、マージロジックを持っています。

#### スクリプト 2：`start-chromadb.sh`（ChromaDB 起動）

```bash
# すでに起動しているか確認
if curl -s "http://127.0.0.1:${CHROMA_PORT}/api/v1/heartbeat" > /dev/null 2>&1; then
  echo "ChromaDB is already running on port ${CHROMA_PORT}"
  exit 0
fi

# バックグラウンドで起動
nohup chroma run --host 127.0.0.1 --port "$CHROMA_PORT" --path "$CHROMA_DATA_DIR" > "$CHROMA_LOG" 2>&1 &

# ヘルスチェックで起動完了を待つ（最大 15 秒）
for i in $(seq 1 15); do
  if curl -s "http://127.0.0.1:${CHROMA_PORT}/api/v1/heartbeat" > /dev/null 2>&1; then
    echo "ChromaDB started successfully"
    exit 0
  fi
  sleep 1
done
```

**冪等性の確保：** コンテナの再起動や再ビルドで二重起動しないよう、最初にヘルスチェックで既存プロセスを確認します。

**データの永続化：** ChromaDB のデータは `$HOME/.claude-mem/chromadb-data` に保存されます。`devcontainer.json` で Docker ボリューム（`claude-mem-data-${devcontainerId}`）をマウントしているため、コンテナを再ビルドしても記憶は消えません。

#### スクリプト 3：`start-claude-mem-worker.sh`（Worker 起動）

このスクリプトが最も複雑です。2 つの大きな問題を解決しています。

**問題 1：SIGHUP によるプロセス終了**

最初は `nohup bun worker-service.cjs &` で起動していましたが、これでは動きませんでした。`postStartCommand` のシェルが終了すると、バックグラウンドプロセスに SIGHUP が送られて Worker が死んでしまいます。

解決策は `worker-service.cjs start` コマンドの使用です。このコマンドは内部で `setsid` + `detached: true` を使ってデーモンを起動するため、シェルの終了の影響を受けません：

```bash
# ダメだったやり方
# nohup bun "$WORKER_SCRIPT" &

# 正しいやり方：setsid でデーモン化
bun "$WORKER_SCRIPT" start
```

**問題 2：SSL デフォルト化への対応**

claude-mem が内部で使う `chroma-mcp` の新しいバージョンでは、ChromaDB への接続時に SSL がデフォルトで有効になりました。コンテナ内の localhost 接続では SSL は不要なので、ランタイムパッチを適用します：

```bash
# worker-service.cjs の minified コードにパッチを当てる
if grep -q 'return s&&l.push("--ssl")' "$WORKER_SCRIPT" 2>/dev/null; then
  sed -i 's/return s&&l.push("--ssl")/return s?l.push("--ssl"):l.push("--ssl","false")/' "$WORKER_SCRIPT"
  echo "Applied --ssl false patch to worker-service.cjs"
fi
```

このパッチは、SSL 設定が true の場合は `--ssl` を渡し、false の場合は `--ssl false` を明示的に渡すように動作を変更します。

**3 段階のヘルスチェック：**

Worker の起動状態を 3 段階で判定します：

1. **readiness エンドポイント（`/api/readiness`）が 200** → 完全に起動済み、何もしない
2. **health エンドポイント（`/api/health`）が応答するが readiness は未応答** → 起動中、最大 30 秒待つ
3. **どちらも応答しない** → 新規起動が必要

### 2.5 MCP サーバー設定テンプレートの拡充

MCP サーバーのテンプレートに claude-mem を追加しました：

```json
{
  "claude-mem": {
    "type": "stdio",
    "command": "node",
    "args": [
      "/usr/local/share/npm-global/lib/node_modules/claude-mem/plugin/scripts/mcp-server.cjs"
    ]
  }
}
```

`npx` ではなく `node` で直接スクリプトを指定しているのは、npm グローバルインストールで既にファイルが存在するため、`npx` のパッケージ解決オーバーヘッドを避けるためです。

### 2.6 ファイアウォールのドメインホワイトリスト追加

claude-mem が外部通信する MCP サーバー用にドメインを追加：

```
# Context7（ライブラリドキュメント検索）
context7.com
clerk.context7.com

# Brave Search API
api.search.brave.com
```

### 2.7 devcontainer.json テンプレートの変更

```json
{
  "mounts": [
    "source=claude-code-config-${devcontainerId},target=/home/node/.claude,type=volume",
    "source=claude-mem-data-${devcontainerId},target=/home/node/.claude-mem,type=volume"
  ],
  "postStartCommand": "sudo /usr/local/bin/init-firewall.sh; /usr/local/bin/init-claude-mem-settings.sh; /usr/local/bin/start-chromadb.sh; /usr/local/bin/start-claude-mem-worker.sh"
}
```

`claude-mem-data` ボリュームを追加して、ChromaDB のデータと claude-mem の設定を永続化しています。

---

## Phase 3：CI での連続バグ修正

### 3.1 Playwright install-deps が chrome を認識しない（コミット `5f49d63`、`e86ef10`）

claude-mem 統合コミットの直後、CI でビルドが失敗しました。

**最初の試み（失敗）：**
```dockerfile
# install-deps は chromium と chrome を別々の引数として認識しなかった
RUN npx -y playwright install-deps chromium chrome
```

`install-deps` サブコマンドは `chrome` を有効な引数として認識しないため、node ユーザーでの `playwright install chrome` 時に `sudo` が要求されて失敗しました。

**最終的な解決策：**
```dockerfile
# root で --with-deps オプションを使い、システム依存関係とブラウザバイナリを一括インストール
USER root
RUN npx -y playwright install --with-deps chromium chrome && \
    chown -R node:node /home/node/.cache/ms-playwright
USER node
```

`--with-deps` フラグを使えば `install-deps` と `install` を別々に実行する必要がなくなります。

### 3.2 root インストールのパス問題（コミット `3156381`）

root で `playwright install` を実行すると、ブラウザが `/root/.cache/ms-playwright` にインストールされます。node ユーザーからはアクセスできません。

```dockerfile
# 環境変数でインストール先を node ユーザーのキャッシュに指定
ENV PLAYWRIGHT_BROWSERS_PATH=/home/node/.cache/ms-playwright
USER root
RUN npx -y playwright install --with-deps chromium chrome && \
    chown -R node:node /home/node/.cache
USER node
```

`chown` の対象も `/home/node/.cache/ms-playwright` から `/home/node/.cache` 全体に広げました。ONNX モデルのキャッシュ（`/home/node/.cache/chroma/onnx_models/`）も root で作成されたファイルの権限問題があったためです。

### 3.3 テストの修正（コミット `9cb45e9`、`873871a`）

スモークテストにも複数の問題がありました：

**claude-mem の存在確認：**
```bash
# NG: claude-mem は npm の bin フィールドを持たないので which では見つからない
check "claude-mem is installed"  which claude-mem

# OK: npm モジュールディレクトリの存在で確認
check "claude-mem is installed"  test -d /usr/local/share/npm-global/lib/node_modules/claude-mem
```

**ONNX モデルキャッシュの確認：**
```bash
# NG: HF_HUB_OFFLINE=1 での Python 実行は環境によって不安定
check "ONNX model is cached"  HF_HUB_OFFLINE=1 python3 -c "..."

# OK: ファイルシステムの存在確認のほうが確実
check "ONNX model is cached"  test -d /home/node/.cache/chroma/onnx_models/all-MiniLM-L6-v2
```

**ipset コマンドの確認：**
```bash
# NG: コンテナ内では ipset --version がカーネルモジュールにアクセスできず失敗
check "ipset is installed"  ipset --version

# OK: バイナリの存在確認だけで十分
check "ipset is installed"  which ipset
```

**Playwright ブラウザの確認：**
```bash
# NG: --dry-run の出力フォーマットがバージョンによって異なる
check "chromium browser exists"  npx playwright install --dry-run chromium 2>&1 | grep -q "already installed"

# OK: ディレクトリの直接確認
check "chromium browser exists"  test -d /home/node/.cache/ms-playwright/chromium-*
check "chrome browser exists"    google-chrome --version
```

### 3.4 PATH の問題（コミット `873871a`）

`iptables` と `ipset` は `/usr/sbin` にインストールされますが、node ユーザーのデフォルト PATH には含まれていませんでした：

```dockerfile
# node ユーザーの PATH に /usr/sbin を追加
ENV PATH="/usr/sbin:$PATH"
```

---

## Phase 4：ボリュームマウントとの戦い

### シンボリックリンクが消える問題

Dockerfile でシンボリックリンクを作成しても、`devcontainer.json` で `/home/node/.claude` にボリュームをマウントすると、初回マウント時にディレクトリの内容がボリュームにコピーされますが、**コンテナ再ビルド時に新しいイメージのシンボリックリンクはボリュームに反映されません**。

`init-claude-mem-settings.sh` はこれを考慮しています：

```bash
# ボリュームマウントでシンボリックリンクが消えた場合に再作成
if [ ! -L "$PLUGIN_DIR/plugin" ] && [ ! -d "$PLUGIN_DIR/plugin" ]; then
  ln -s /usr/local/share/npm-global/lib/node_modules/claude-mem/plugin "$PLUGIN_DIR/plugin"
  echo "Recreated claude-mem plugin symlink"
fi
```

同様に `known_marketplaces.json` への登録も、毎回チェックして必要なら追加します。

---

## 全体のデータフロー

コンテナ起動から Claude Code のセッション完了までの流れをまとめます：

```
コンテナ起動
    │
    ├─ 1. init-firewall.sh     → iptables ルール設定
    ├─ 2. init-claude-mem-settings.sh
    │      ├─ settings.json 生成（ChromaDB 接続先）
    │      ├─ プラグイン登録（known_marketplaces.json）
    │      ├─ npm 依存関係チェック
    │      └─ フック登録（Claude Code settings.json）
    ├─ 3. start-chromadb.sh    → ChromaDB 起動（ポート 8100）
    └─ 4. start-claude-mem-worker.sh
           ├─ SSL パッチ適用
           └─ Worker デーモン起動（ポート 37777）

Claude Code セッション開始
    │
    ├─ [Hook: SessionStart]
    │      ├─ Worker デーモン確認/起動
    │      └─ 過去のコンテキスト読み込み → ユーザーに表示
    │
    ├─ [Hook: UserPromptSubmit]
    │      └─ セッション初期化
    │
    ├─ [Hook: PostToolUse] ← ツール使用のたびに実行
    │      └─ 観察の記録 → Worker → ChromaDB にベクトル保存
    │
    └─ [Hook: Stop]
           ├─ セッション要約の生成
           └─ セッション完了処理
```

---

## 解決した問題のまとめ

| 問題 | 原因 | 解決策 |
|------|------|--------|
| Docker ビルド中に `claude plugins install` が使えない | 認証が必要なインタラクティブコマンド | `npm install -g` + 手動シンボリックリンク |
| ONNX モデルのダウンロードがファイアウォールでブロック | Hugging Face がホワイトリスト外 | ビルド時にモデルを事前ダウンロード |
| Worker が `postStartCommand` 終了後に死ぬ | SIGHUP がバックグラウンドプロセスに配信 | `worker-service.cjs start` の setsid デーモン化 |
| ChromaDB 接続が SSL エラー | 新バージョンの chroma-mcp が SSL をデフォルト化 | ランタイム sed パッチで `--ssl false` を明示 |
| ボリュームマウントでシンボリックリンクが消える | ボリュームがディレクトリを上書き | 起動スクリプトで毎回復元チェック |
| Playwright ブラウザが root のキャッシュに入る | root でインストールするとパスが `/root/.cache` | `PLAYWRIGHT_BROWSERS_PATH` で明示指定 + `chown` |
| ONNX キャッシュの権限エラー | root Playwright インストールが `.cache` 全体の所有権を変更 | `chown` 対象を `.cache` 全体に拡大 |
| `ipset --version` がコンテナ内で失敗 | カーネルモジュールへのアクセスが制限されている | `which ipset` でバイナリ存在確認に変更 |
| claude-mem が `which` で見つからない | npm パッケージに `bin` フィールドがない | ディレクトリ存在チェックに変更 |

---

## 他のプロジェクトとの比較

2026 年 3 月時点で、コンテナ内で MCP サーバーを管理する類似プロジェクトが複数存在しますが、claude-mem をコンテナ内でローカル動作させているものは他にありません：

| 特徴 | devcontainer-claude-code | centminmod/claude-code-devcontainers | agomusio/no-more-configs |
|------|------------------------|--------------------------------------|--------------------------|
| MCP プリキャッシュ | 7 つ | 3 つ | 2 つ（デフォルト無効） |
| ChromaDB + claude-mem 内蔵 | あり | なし | なし |
| ONNX モデル事前ダウンロード | あり | なし | なし |
| Worker デーモン管理 | あり | なし | なし |
| 再利用可能なベースイメージ | あり（GHCR 公開） | あり | あり |

---

## おわりに

claude-mem のコンテナ内ローカル動作は、単に `npm install -g claude-mem` で終わる話ではありません。5 つのコンポーネント（MCP サーバー、フック、Worker、ChromaDB、ONNX モデル）を全てコンテナのライフサイクルに合わせて管理する必要があり、ビルド時のモデル事前ダウンロード、ランタイムの SSL パッチ、ボリュームマウントとの整合性確保など、コンテナ特有の問題への対処が求められます。

しかし、一度セットアップすれば、コンテナを起動するだけで Claude Code が長期記憶を持った状態で使えるようになります。プロジェクトをまたいだ知識の蓄積、過去のバグ修正の参照、設計判断の記録など、開発体験が大きく向上します。
