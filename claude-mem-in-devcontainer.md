# claude-mem をコンテナ内で完全ローカル動作させるまでの全記録

## はじめに：この記事で分かること

claude-mem は、Claude Code（Anthropic の AI コーディング CLI）にセッションをまたいだ長期記憶を与えるプラグインです。通常はホストマシンに直接インストールして使いますが、**ホストですら安定動作させるのが簡単ではありません**。その上で Dev Container（開発用コンテナ）の中で動かそうとすると、さらに多くの壁にぶつかります。

この記事では、ホストマシンで claude-mem の接続問題と格闘した 2026 年 2 月 22 日の経験から始まり、[devcontainer-claude-code](https://github.com/anago262/devcontainer-claude-code) プロジェクトでコンテナ内に完全に封じ込めて動作させるまでの全工程を時系列で解説します。

### 対象読者

- Claude Code を使っていて claude-mem に興味がある人
- claude-mem のセットアップで MCP error -32000 に苦しんでいる人
- Dev Container 内でバックグラウンドサービスを管理する方法を学びたい人

---

## Part 1：claude-mem とは何か

### claude-mem の目的

Claude Code は強力な AI コーディングツールですが、**セッションをまたいだ記憶を持ちません**。昨日修正したバグ、先週の設計判断、過去に読んだファイルの内容——これらは全て次のセッションでは忘れられています。

claude-mem はこの問題を解決します。Claude Code のセッション中に行われた全ての操作（ファイルの読み書き、コマンド実行、設計判断など）を「観察（observation）」として記録し、次のセッション開始時に関連する過去の文脈を自動的に注入します。

### claude-mem のデフォルト構成

claude-mem を `claude plugins install thedotmack/claude-mem` でインストールすると、以下の構成でセットアップされます：

```
ホストマシン
├── Claude Code
│   ├── MCP サーバー（mcp-server.cjs）     ← 記憶の検索 API
│   └── Hooks（4種）                        ← 操作の自動記録
│       ├── SessionStart     → Worker 起動 + 過去コンテキスト注入
│       ├── UserPromptSubmit → セッション初期化
│       ├── PostToolUse      → 観察の記録
│       └── Stop             → セッション要約 + 完了処理
│
├── Worker Service（port 37777）             ← HTTP API デーモン
│   ├── 観察データの処理と保存
│   ├── セッション管理
│   └── ChromaDB へのベクトル埋め込み要求
│
├── SQLite DB（~/.claude-mem/claude-mem.db）  ← メタデータ + FTS5 全文検索
│
└── ChromaDB 接続（CHROMA_MODE で制御）
    ├── "local" モード → サブプロセスとして chroma-mcp を起動
    └── "remote" モード → 外部の ChromaDB サーバーに HTTP 接続
```

ここで重要なのは、claude-mem には**二重のデータストア**があるということです：

- **SQLite**: 観察のメタデータ、セッション情報、全文検索（FTS5）。これだけでも基本的な記憶機能は動く
- **ChromaDB**: ベクトル埋め込みによるセマンティック検索。「似た概念」を見つけるのに使う

ChromaDB がなくても SQLite だけで動作しますが、セマンティック検索ができないため記憶の精度が大幅に落ちます。

---

## Part 2：ホストでの苦戦（2026年2月22日）

### 2.1 最初のエラー：MCP error -32000

claude-mem をインストールした直後、以下のエラーが繰り返し発生しました：

```
MCP error -32000: Connection closed
```

このエラーは claude-mem が内部で起動する `chroma-mcp`（ChromaDB と通信するための MCP サブプロセス）の接続に失敗したことを意味します。

### 2.2 local モードの問題

claude-mem のデフォルトは `CHROMA_MODE: "local"` です。このモードでは、Worker Service が内部で `uvx chroma-mcp` コマンドを実行し、ChromaDB をサブプロセスとして起動します。

```
Worker Service
  └── uvx chroma-mcp --client-type persistent --path ~/.claude-mem/chroma
        └── 内蔵 ChromaDB
```

これが不安定だった理由は複数あります：

**理由 1：chroma-mcp のスポーン失敗**

claude-mem のログ（`~/.claude-mem/logs/`）を確認すると、Worker が `uvx chroma-mcp` を起動しても **4 ミリ秒以内に接続が閉じられる**パターンが見えました：

```
uvx chroma-mcp --client-type http --host 127.0.0.1 --port 8100
→ MCP error -32000: Connection closed（377ms で失敗）
```

手動で同じコマンドを実行すると正常に動作します。問題は自動スポーンのプロセス管理にありました。

**理由 2：スポーンストーム**

接続に失敗すると Worker がリトライし、chroma-mcp プロセスが大量に生成される「スポーンストーム」が発生しました。v10.3.0 で 10 秒のバックオフが導入されましたが、根本解決には至りませんでした。

**理由 3：macOS/Windows 固有の問題**

GitHub の Issue を調べると、同じ問題が macOS（Issue #695）と Windows（Issue #675）の両方で報告されていました：

- **macOS**: StdioClientTransport がサブプロセスをスポーンするが、4ms で接続が切れる
- **Windows**: プロセススポーンの方法自体が異なり、uvx/Python のサブプロセス管理が失敗する

PR #1065 で「5 層防御」（接続 Mutex、プロセス数ガード、サーキットブレーカー等）が実装されましたが、完全な解決策とはなりませんでした。

**理由 4：ChromaMcpManager のアーキテクチャ変更**

v10.3.0 で `ChromaServerManager`（ネイティブバイナリ方式）から `ChromaMcpManager`（stdio MCP クライアント方式）に刷新されました。WASM やネイティブバイナリの segfault は解消しましたが、新しい接続管理に起因する問題が生まれました。

### 2.3 SSL デフォルトの罠（Issue #1182）

Worker のソースコード（minified された `worker-service.cjs`）を読み解くと、もう一つの問題が見つかりました：

```javascript
// buildCommandArgs() の該当箇所（minified）
return s && l.push("--ssl")
```

`chroma-mcp` v0.2.6 は **SSL をデフォルトで true** にする仕様です。しかし claude-mem の `buildCommandArgs()` は、`CLAUDE_MEM_CHROMA_SSL` が `"true"` の時だけ `--ssl` フラグを付け、`"false"` の時は**何も渡さない**。結果、chroma-mcp のデフォルト（SSL=true）が有効になり、ローカルの HTTP ChromaDB に HTTPS で接続しようとして失敗します。

### 2.4 解決策：Docker で ChromaDB を外部起動 + remote モード

local モードの不安定さを回避するため、ChromaDB を Docker コンテナとして独立起動し、remote モードで接続する構成に変更しました：

```bash
# ChromaDB を Docker コンテナで起動
docker run -d --name chromadb \
  --restart unless-stopped \
  -p 8100:8000 \
  -v chromadb-data:/chroma/chroma \
  chromadb/chroma:latest
```

```json
// ~/.claude-mem/settings.json
{
  "CLAUDE_MEM_CHROMA_MODE": "remote",
  "CLAUDE_MEM_CHROMA_HOST": "127.0.0.1",
  "CLAUDE_MEM_CHROMA_PORT": "8100",
  "CLAUDE_MEM_CHROMA_SSL": "false"
}
```

さらに `worker-service.cjs` に手動パッチを適用し、`--ssl false` が明示的に渡されるようにしました：

```bash
# パッチ前
return s && l.push("--ssl")

# パッチ後
return s ? l.push("--ssl") : l.push("--ssl", "false")
```

### 2.5 ChromaDB バージョン互換性

最初に ChromaDB 0.6.3 をデプロイしましたが、v1 API が deprecated であることが判明。`chromadb/chroma:latest`（v1.0.0）に置き換え、v2 API のヘルスチェック（`/api/v2/heartbeat`）で動作を確認しました。ただし claude-mem の起動スクリプトでは v1 エンドポイント（`/api/v1/heartbeat`）も残して後方互換性を確保しています。

### 2.6 ゾンビプロセスの発見

プロセスを調べると、MCP サーバー（`mcp-server.cjs`）が **3 つ同時に動いている**ことが発覚。それぞれ異なる時間に起動され、蓄積していました。これも接続不安定の一因でした。Worker は 1 つだけ正しく動いていましたが、複数の MCP サーバーが ChromaDB 接続を競合させていた可能性があります。

### 2.7 この時点でのまとめ：CLAUDE.md に記録

苦戦の末に安定動作させた知見を `~/.claude/CLAUDE.md` に文書化しました。要点は：

- local モード（サブプロセス方式）は不安定 → **Docker + remote モードを推奨**
- `worker-service.cjs` への SSL パッチが必要（パッチ箇所は 2 つ：`cache` 側と `marketplaces` 側）
- claude-mem アップデート後は `cache` 側のパッチ再適用が必要
- エラー発生時はコンテナ再起動 → Worker kill → Claude Code 再起動の順

---

## Part 3：コンテナ化の発想と設計

### 3.1 なぜコンテナ内に入れたいのか

ホストでの構成は動きましたが、問題があります：

1. **環境依存**: ホストに Docker Desktop、Python、Bun、claude-mem のパッチなど多数の前提条件がある
2. **再現性**: チームメンバーが同じセットアップを再現するのが困難
3. **Dev Container との二重管理**: コーディングは Dev Container 内、ChromaDB はホストの Docker——管理が分散する

理想は **Dev Container を起動するだけで全てが揃う** 状態です。

### 3.2 設計方針

ホストでの経験から、以下の方針を決定しました：

1. **local モードは使わない** — サブプロセススポーンの不安定さはコンテナ内でも同じ。remote モードで ChromaDB を別プロセスとして起動する
2. **ChromaDB はコンテナ内プロセス** — ホストでは Docker コンテナだったが、Dev Container 内では `chroma run` コマンドでネイティブプロセスとして起動
3. **ONNX モデルはビルド時にダウンロード** — ファイアウォールで実行時のダウンロードがブロックされる可能性がある
4. **SSL パッチは起動スクリプトで自動適用** — ホストでの手動パッチの教訓を活かし、自動化する
5. **`npm install -g` + シンボリックリンク** — `claude plugins install` はビルド時に使えないため

---

## Part 4：ベースイメージの出発点

### 初期の Dockerfile（コミット `b0c854e`）

プロジェクト最初の Dockerfile には claude-mem の痕跡はありませんでした：

```dockerfile
FROM node:20

# 基本的な開発ツール
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

Claude Code と Playwright だけのシンプルな構成でした。

---

## Part 5：claude-mem スタックの統合（コミット `6c4a40f`）

ここが最大の変更です。1 コミットで 10 ファイル、492 行の追加。

### 5.1 3 つのランタイムが必要

claude-mem を動かすには Node.js だけでなく **Python と Bun** も必要です。これは claude-mem の内部アーキテクチャに起因します：

- **Node.js**: claude-mem 本体（MCP サーバー、フック処理）
- **Bun**: Worker Service のランタイム（`worker-service.cjs` は Bun での実行を前提）
- **Python**: ChromaDB（Python 製のベクトルDB）+ ONNX 埋め込みモデル

```dockerfile
# Python（ChromaDB に必要）
RUN apt-get install -y python3-pip
RUN pip3 install --break-system-packages chromadb

# Bun（Worker Service に必要）
RUN curl -fsSL https://bun.sh/install | bash
ENV PATH="/home/node/.bun/bin:$PATH"
```

`--break-system-packages` は、Debian の新しいバージョンでシステム Python への pip install がデフォルト拒否されるためです。コンテナ内では仮想環境は不要なので直接インストールします。

### 5.2 プラグインのインストール問題

**ホストでの正規インストール方法:**

```bash
claude plugins install thedotmack/claude-mem
```

**Docker ビルド中にこれが使えない理由:**

このコマンドは内部で Claude Code の認証を要求します。Docker ビルドは非インタラクティブなので認証できません。

**解決策: npm install + 手動シンボリックリンク:**

```dockerfile
# npm でパッケージだけインストール
RUN npm install -g claude-mem
ENV CLAUDE_MEM_SCRIPTS="/usr/local/share/npm-global/lib/node_modules/claude-mem/plugin/scripts"

# Claude Code が期待するパスにシンボリックリンクを作成
RUN mkdir -p /home/node/.claude-mem /home/node/.claude/plugins/marketplaces/thedotmack \
  && ln -s /usr/local/share/npm-global/lib/node_modules/claude-mem/plugin \
     /home/node/.claude/plugins/marketplaces/thedotmack/plugin
```

Claude Code はプラグインを `~/.claude/plugins/marketplaces/<作者名>/plugin` に期待します。npm グローバルインストール先からシンボリックリンクを張ることで、プラグインコマンドなしでもフックが発見されるようにしました。

ただし、これだけでは不十分です。Claude Code はプラグインの存在を `known_marketplaces.json` で管理しているため、そこへの登録も必要です（後述の起動スクリプトで対応）。

### 5.3 ONNX 埋め込みモデルの事前ダウンロード

```dockerfile
RUN python3 -c "from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2; ef = ONNXMiniLM_L6_V2(); ef(['warmup'])"
```

ChromaDB がテキストをベクトルに変換する際に使う AI モデル（all-MiniLM-L6-v2、約 80MB）は、初回使用時に Hugging Face からダウンロードされます。しかし Dev Container にはファイアウォールがあり、Hugging Face はホワイトリスト外です。ビルド時にダウンロードしておけば実行時のネットワーク制限を気にせずに済みます。

`ef(['warmup'])` で実際にテキストを処理させることで、モデルのダウンロードだけでなく ONNX ランタイムの初期化も完了させています。

### 5.4 起動スクリプト：3 つのサービスのオーケストレーション

Dev Container の起動時に 4 つのスクリプトを順番に実行します：

```json
"postStartCommand": "sudo /usr/local/bin/init-firewall.sh; /usr/local/bin/init-claude-mem-settings.sh; /usr/local/bin/start-chromadb.sh; /usr/local/bin/start-claude-mem-worker.sh"
```

#### スクリプト 1：`init-claude-mem-settings.sh`（192行）

このスクリプトは 4 つの仕事をします：

**仕事 1: ChromaDB 接続設定の生成**

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

ホストでの教訓から `"remote"` モードを選択しています。local モードのサブプロセススポーン問題を回避するためです。

`CLAUDE_MEM_PYTHON_VERSION` を `"3.11"` に固定しているのは、コンテナ内の Python バージョンが 3.11 であるためです（ホストでは Python 3.13 で互換性問題があった）。

**仕事 2: プラグインマーケットプレイス登録**

```python
data['thedotmack'] = {
    'source': {'source': 'local', 'path': '$PLUGIN_DIR'},
    'installLocation': '$PLUGIN_DIR',
    'lastUpdated': '...'
}
```

`known_marketplaces.json` に `thedotmack` を登録しないと、シンボリックリンクがあっても**フックが発見されません**。MCP サーバーは動くが観察が記録されない、という部分的な故障になります。

**仕事 3: npm 依存関係の復元**

ボリュームマウントでプラグインディレクトリが上書きされた場合に `node_modules` を再インストールします。

**仕事 4: Claude Code フックの自作登録**

ここが最も重要で複雑な部分です。通常 `claude plugins install` がやってくれるフック登録を、**全て手書きで再現**する必要があります。

**なぜ自作が必要か：** `claude plugins install` は (1) パッケージのダウンロード、(2) `known_marketplaces.json` への登録、(3) フックの `settings.json` への登録を一括で行います。Docker ビルドでは (1) を `npm install -g` で、(2) を仕事 2 で代替しましたが、(3) だけは自動では行われません。フックが登録されていないと、**MCP サーバーは動くのに観察が一切記録されない** という状態になります。

**フックの構造：** Claude Code のフックは `~/.claude/settings.json` の `hooks` オブジェクトに記述します。各イベント名をキーとし、matcher（どのタイミングで発火するか）と実行するコマンドの配列を定義します。

`init-claude-mem-settings.sh` は以下の JSON 構造を Python スクリプトで生成し、既存の `settings.json` にマージします：

```python
worker_base = "/usr/local/share/npm-global/lib/node_modules/claude-mem/plugin/scripts"
bun_runner = f"node {worker_base}/bun-runner.js"
worker_cmd = f"{worker_base}/worker-service.cjs"

claude_mem_hooks = {
    "SessionStart": [
        {
            "matcher": "startup|clear|compact",
            "hooks": [
                {
                    "type": "command",
                    "command": f"{bun_runner} {worker_cmd} start",
                    "timeout": 60,
                },
                {
                    "type": "command",
                    "command": f"{bun_runner} {worker_cmd} hook claude-code context",
                    "timeout": 60,
                },
            ],
        }
    ],
    "UserPromptSubmit": [
        {
            "hooks": [
                {
                    "type": "command",
                    "command": f"{bun_runner} {worker_cmd} hook claude-code session-init",
                    "timeout": 60,
                }
            ]
        }
    ],
    "PostToolUse": [
        {
            "hooks": [
                {
                    "type": "command",
                    "command": f"{bun_runner} {worker_cmd} hook claude-code observation",
                    "timeout": 120,
                }
            ]
        }
    ],
    "Stop": [
        {
            "hooks": [
                {
                    "type": "command",
                    "command": f"{bun_runner} {worker_cmd} hook claude-code summarize",
                    "timeout": 120,
                },
                {
                    "type": "command",
                    "command": f"{bun_runner} {worker_cmd} hook claude-code session-complete",
                    "timeout": 30,
                },
            ]
        }
    ],
}
```

**各フックの役割を分解すると：**

| イベント | コマンド | 何をしているか |
|----------|---------|--------------|
| `SessionStart` | `worker-service.cjs start` | Worker デーモンが起動していなければ起動する（setsid デーモン化） |
| `SessionStart` | `worker-service.cjs hook claude-code context` | 過去のセッションから関連コンテキストを検索し、会話冒頭に注入する |
| `UserPromptSubmit` | `worker-service.cjs hook claude-code session-init` | 新しいセッションを初期化し、メタデータを記録する |
| `PostToolUse` | `worker-service.cjs hook claude-code observation` | ツール使用（ファイル読み書き、コマンド実行等）を観察として記録する |
| `Stop` (1) | `worker-service.cjs hook claude-code summarize` | セッション全体の要約を生成して保存する |
| `Stop` (2) | `worker-service.cjs hook claude-code session-complete` | セッションの完了処理（統計更新等）を行う |

**コマンドの実行パス：** 全てのフックは `bun-runner.js` を経由して実行されます。これは Bun ランタイムのラッパーで、`node bun-runner.js worker-service.cjs <サブコマンド>` の形式で呼び出されます。コンテナ内では Bun が `/home/node/.bun/bin/bun` にインストールされているため、`bun-runner.js` がパスを解決して実行します。

**`matcher` の意味：** `SessionStart` フックの `"matcher": "startup|clear|compact"` は、セッション開始の種類を指定しています。`startup`（新規起動）、`clear`（`/clear` コマンド）、`compact`（コンテキスト圧縮）のいずれかの場合にフックが発火します。他のイベント（`UserPromptSubmit`、`PostToolUse`、`Stop`）は matcher がないため、常に発火します。

**マージロジック：** スクリプトは既存の `settings.json` に claude-mem 以外のフックが既に登録されている場合でも安全に動作します：

```python
existing_hooks = settings.get("hooks", {})
for event, hook_list in claude_mem_hooks.items():
    if event not in existing_hooks:
        existing_hooks[event] = hook_list
    else:
        # claude-mem のフックが既に存在するか確認
        has_cm = any(
            "worker-service" in h.get("command", "")
            for group in existing_hooks[event]
            for h in group.get("hooks", [])
        )
        if not has_cm:
            existing_hooks[event].extend(hook_list)
```

イベントごとに `worker-service` を含むコマンドが既にあるかチェックし、なければ追加します。これにより、ユーザーが独自のフックを追加していても上書きされません。

#### スクリプト 2：`start-chromadb.sh`（29行）

```bash
# 冪等性: すでに起動していれば何もしない
if curl -s "http://127.0.0.1:${CHROMA_PORT}/api/v1/heartbeat" > /dev/null 2>&1; then
  echo "ChromaDB is already running on port ${CHROMA_PORT}"
  exit 0
fi

# コンテナ内ネイティブプロセスとして起動（ホストではDockerだったもの）
nohup chroma run --host 127.0.0.1 --port "$CHROMA_PORT" --path "$CHROMA_DATA_DIR" > "$CHROMA_LOG" 2>&1 &

# 起動完了を最大15秒待つ
for i in $(seq 1 15); do
  if curl -s "http://127.0.0.1:${CHROMA_PORT}/api/v1/heartbeat" > /dev/null 2>&1; then
    echo "ChromaDB started successfully"
    exit 0
  fi
  sleep 1
done
```

ホストでは Docker コンテナだった ChromaDB を、Dev Container 内では **`pip install` された `chroma` CLI でネイティブプロセスとして起動** しています。Docker-in-Docker は不要です。

#### スクリプト 3：`start-claude-mem-worker.sh`（68行）

このスクリプトはホストでの全ての教訓を集約しています。

**教訓 1（SSL パッチの自動適用）：**

ホストでは手動で `worker-service.cjs` を編集していました。コンテナでは起動スクリプトで自動化：

```bash
if grep -q 'return s&&l.push("--ssl")' "$WORKER_SCRIPT" 2>/dev/null; then
  sed -i 's/return s&&l.push("--ssl")/return s?l.push("--ssl"):l.push("--ssl","false")/' "$WORKER_SCRIPT"
  echo "Applied --ssl false patch to worker-service.cjs"
fi
```

`grep` でパッチ対象が存在するか確認してから `sed` で適用する冪等設計です。既にパッチ済みならスキップされます。

**教訓 2（SIGHUP によるプロセス終了の回避）：**

最初のアプローチ `nohup bun worker-service.cjs &` では、`postStartCommand` のシェルが終了すると SIGHUP でバックグラウンドプロセスが kill されました。

```bash
# NG: シェル終了時に SIGHUP で死ぬ
# nohup bun "$WORKER_SCRIPT" &

# OK: setsid でセッションリーダーから切り離す
bun "$WORKER_SCRIPT" start
```

`worker-service.cjs start` は内部で `setsid` + `detached: true` を使ったデーモン化を行うため、シェルのライフサイクルから独立します。

**教訓 3（3 段階ヘルスチェック）：**

ゾンビプロセスの教訓から、起動状態を 3 段階で判定します：

1. **readiness（`/api/readiness`）が 200** → 完全に起動済み、何もしない
2. **health（`/api/health`）は応答するが readiness は未応答** → 起動中、最大 30 秒待つ
3. **どちらも応答しない** → 新規起動が必要

### 5.5 MCP サーバー設定

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

`npx` ではなく `node` で直接パスを指定し、パッケージ解決のオーバーヘッドを避けています。

### 5.6 devcontainer.json のボリューム設計

```json
{
  "mounts": [
    "source=claude-code-config-${devcontainerId},target=/home/node/.claude,type=volume",
    "source=claude-mem-data-${devcontainerId},target=/home/node/.claude-mem,type=volume"
  ]
}
```

2 つのボリュームで設定とデータを永続化：
- `claude-code-config`: Claude Code の設定、プラグイン情報、フック登録
- `claude-mem-data`: ChromaDB のデータ、SQLite DB、ログ

コンテナを再ビルドしても**記憶は消えません**。

---

## Part 6：ボリュームマウントとの戦い

### シンボリックリンクが消える問題

Dockerfile でシンボリックリンクを作成しても、`devcontainer.json` で `/home/node/.claude` にボリュームをマウントすると、**初回マウント時にディレクトリの内容がボリュームにコピーされますが、コンテナ再ビルド時に新しいイメージのシンボリックリンクはボリュームに反映されません**。

`init-claude-mem-settings.sh` はこれを毎回チェックして復元します：

```bash
# ボリュームマウントでシンボリックリンクが消えた場合に再作成
if [ ! -L "$PLUGIN_DIR/plugin" ] && [ ! -d "$PLUGIN_DIR/plugin" ]; then
  ln -s /usr/local/share/npm-global/lib/node_modules/claude-mem/plugin "$PLUGIN_DIR/plugin"
  echo "Recreated claude-mem plugin symlink"
fi
```

---

## Part 7：CI での連続バグ修正

### 7.1 Playwright のインストール権限（コミット `5f49d63` → `e86ef10` → `3156381`）

3 回の修正を要しました：

| 試行 | 方法 | 結果 |
|------|------|------|
| 1 | `install-deps chromium chrome` → `install chromium chrome` | `install-deps` が `chrome` を認識せず失敗 |
| 2 | root で `--with-deps` 一括 | ブラウザが `/root/.cache` に入り node ユーザーからアクセス不可 |
| 3 | `PLAYWRIGHT_BROWSERS_PATH` を明示 + `chown` | 成功 |

最終形：

```dockerfile
ENV PLAYWRIGHT_BROWSERS_PATH=/home/node/.cache/ms-playwright
USER root
RUN npx -y playwright install --with-deps chromium chrome && \
    chown -R node:node /home/node/.cache
USER node
```

`chown` の対象を `/home/node/.cache` 全体に広げたのは、ONNX モデルのキャッシュ（`/home/node/.cache/chroma/onnx_models/`）の権限問題も同時に解決するためです。

### 7.2 テストの脆弱性（コミット `9cb45e9`、`873871a`）

スモークテストに複数の問題がありました：

| テスト対象 | 失敗した方法 | 成功した方法 | 理由 |
|-----------|------------|------------|------|
| claude-mem | `which claude-mem` | `test -d .../claude-mem` | npm パッケージに `bin` フィールドがない |
| ONNX モデル | `HF_HUB_OFFLINE=1 python3 -c "..."` | `test -d .../all-MiniLM-L6-v2` | Python 実行は環境依存で不安定 |
| ipset | `ipset --version` | `which ipset` | コンテナ内でカーネルモジュールにアクセス不可 |
| Playwright | `--dry-run` の grep | `test -d ...` / `google-chrome --version` | 出力フォーマットがバージョンで変わる |

### 7.3 PATH の問題（コミット `873871a`）

`iptables` と `ipset` は `/usr/sbin` にインストールされますが、node ユーザーの PATH には含まれていませんでした：

```dockerfile
ENV PATH="/usr/sbin:$PATH"
```

### 7.4 ARM64 ビルドの失敗

Apple Silicon（M1/M2）で Docker ビルドすると、Google Chrome が Linux ARM64 をサポートしていないため失敗しました。ローカルテストでは `--platform linux/amd64` を指定して QEMU エミュレーションでビルドします。

---

## Part 8：全体のデータフロー

コンテナ起動から Claude Code のセッション完了までの流れをまとめます：

```
コンテナ起動（postStartCommand）
    │
    ├─ 1. init-firewall.sh        → iptables ルール設定（ネットワーク制限）
    ├─ 2. init-claude-mem-settings.sh
    │      ├─ settings.json 生成（remote モード、ポート 8100、SSL 無効）
    │      ├─ シンボリックリンク復元（ボリュームで消えた場合）
    │      ├─ プラグイン登録（known_marketplaces.json）
    │      ├─ npm 依存関係チェック
    │      └─ フック登録（Claude Code settings.json にマージ）
    ├─ 3. start-chromadb.sh       → ChromaDB 起動（ポート 8100、ヘルスチェック待ち）
    └─ 4. start-claude-mem-worker.sh
           ├─ SSL パッチ自動適用（冪等）
           ├─ 3段階ヘルスチェック
           └─ Worker デーモン起動（ポート 37777、setsid でデタッチ）

Claude Code セッション
    │
    ├─ [SessionStart Hook]       → Worker 確認 + 過去コンテキスト注入
    ├─ [UserPromptSubmit Hook]   → セッション初期化
    ├─ [PostToolUse Hook] ×N     → 観察記録 → Worker → ChromaDB にベクトル保存
    ├─ [MCP: search/timeline/get_observations] → 過去記憶の検索
    └─ [Stop Hook]               → セッション要約生成 + 完了処理
```

---

## Part 9：解決した問題のまとめ

### ホストで発見した問題（2月22日）

| 問題 | 原因 | 解決策 |
|------|------|--------|
| MCP error -32000 接続失敗 | local モードの chroma-mcp サブプロセスが 4ms で切断 | Docker で ChromaDB 外部起動 + remote モード |
| スポーンストーム | 接続失敗 → リトライ → 大量プロセス生成 | remote モードで根本回避 |
| SSL エラー | chroma-mcp v0.2.6 が SSL デフォルト true | worker-service.cjs に手動パッチ |
| ChromaDB バージョン互換性 | 0.6.3 の v1 API が deprecated | latest（v1.0.0）に更新 |
| ゾンビ MCP サーバー | 複数の mcp-server.cjs が蓄積 | プロセス kill + 再起動 |

### コンテナ化で発見した問題

| 問題 | 原因 | 解決策 |
|------|------|--------|
| `claude plugins install` が使えない | Docker ビルドは非インタラクティブ | `npm install -g` + シンボリックリンク |
| フックが発見されない | `known_marketplaces.json` に未登録 | 起動スクリプトで動的登録 |
| ONNX モデルがダウンロードできない | ファイアウォールが Hugging Face をブロック | ビルド時に事前ダウンロード |
| Worker が起動後すぐ死ぬ | SIGHUP がバックグラウンドプロセスに配信 | `worker-service.cjs start` の setsid デーモン化 |
| SSL パッチが毎回必要 | パッチはイメージに含められない（npm install 後に適用） | 起動スクリプトで冪等な自動パッチ |
| シンボリックリンクが消える | ボリュームマウントがディレクトリを上書き | 起動スクリプトで毎回復元 |
| Playwright ブラウザのパス | root インストールが `/root/.cache` に配置 | `PLAYWRIGHT_BROWSERS_PATH` + `chown` |
| `.cache` 全体の権限 | root Playwright インストールが ONNX キャッシュも巻き込む | `chown` 対象を `.cache` 全体に拡大 |

---

## Part 10：他のプロジェクトとの比較

2026 年 3 月時点で、コンテナ内で MCP サーバーを管理する類似プロジェクトが複数存在しますが、claude-mem をコンテナ内でローカル動作させているものは他にありません：

| 特徴 | devcontainer-claude-code | centminmod | no-more-configs |
|------|------------------------|------------|-----------------|
| MCP プリキャッシュ | 7 つ | 3 つ | 2 つ（デフォルト無効） |
| ChromaDB + claude-mem 内蔵 | あり | なし | なし |
| ONNX モデル事前ダウンロード | あり | なし | なし |
| Worker デーモン管理 | あり | なし | なし |
| SSL パッチ自動適用 | あり | なし | なし |
| ボリューム永続化設計 | あり | あり | あり |
| 再利用可能なベースイメージ | あり（GHCR 公開） | あり | あり |

---

## おわりに

claude-mem のコンテナ内ローカル動作は、`npm install -g claude-mem` では終わらない多段階の作業です。

**ホストでの教訓** — local モードの不安定さ、SSL デフォルトの罠、ゾンビプロセス、バージョン互換性 — これらを理解した上で初めて、コンテナ内での安定構成を設計できました。

**コンテナ固有の課題** — 非インタラクティブ環境でのプラグインインストール、ビルド時のモデル事前ダウンロード、SIGHUP によるデーモン終了、ボリュームマウントとの整合性 — これらはホストでは発生しない問題です。

最終的に、**ホストでの 5 つの問題 + コンテナ固有の 8 つの問題 = 計 13 の問題**を解決して、コンテナ起動だけで全てが揃う環境を実現しました。一度セットアップすれば、プロジェクトをまたいだ知識の蓄積、過去のバグ修正の参照、設計判断の記録など、Claude Code の開発体験が大きく向上します。
