# Claude Code を Docker コンテナで安全に自律実行する

## なぜコンテナ化するのか

Claude Code には `--dangerously-skip-permissions` というフラグがある。これを指定すると、ファイル作成・編集・シェルコマンド実行などの許可プロンプトがすべてスキップされ、Claude が完全自律で動作する。

通常モードでは操作のたびに承認が求められるため、長時間の自律タスク（リファクタリング、大規模テスト実行、データ分析パイプライン構築など）には向かない。しかし、ホストマシン上で `--dangerously-skip-permissions` を実行するのは以下のリスクがある:

- **意図しないファイルの削除・上書き** — Claude がプロジェクト外のシステムファイルや設定ファイルを変更するケースが報告されている
- **機密情報の露出** — `.env` やクレデンシャルが作業ディレクトリからアクセス可能になる
- **ネットワーク経由の情報漏洩** — 悪意あるプロンプトインジェクションによって、Claude が外部にデータを送信する可能性がある

**解決策がコンテナ化**だ。Docker コンテナ内で Claude Code を実行することで:

1. **ファイルシステム分離** — マウントしたディレクトリのみアクセス可能
2. **ネットワーク制御** — iptables でホワイトリスト制の通信制限を適用
3. **プロセス分離** — ホスト OS に影響が及ばない
4. **破壊されても再構築可能** — コンテナを壊しても `docker compose up` で復元

Anthropic 公式ドキュメントでも、`--dangerously-skip-permissions` の利用は **devcontainer 内での実行を前提** としている。

## アーキテクチャ概要

```
┌─────────────────────────────────────────────────────┐
│  ホストマシン                                         │
│                                                       │
│  ┌─────────────────────────────────────────────────┐ │
│  │  Docker コンテナ（devcontainer）                   │ │
│  │                                                   │ │
│  │  ┌──────────┐  ┌───────────┐  ┌──────────────┐  │ │
│  │  │ Claude   │  │ iptables  │  │ 開発ツール     │  │ │
│  │  │ Code     │  │ firewall  │  │ git, node,   │  │ │
│  │  │          │  │           │  │ zsh, etc.    │  │ │
│  │  └──────────┘  └───────────┘  └──────────────┘  │ │
│  │                                                   │ │
│  │  /workspace ←── bind mount ──→ ホストの src/     │ │
│  │                                                   │ │
│  │  許可される通信:                                   │ │
│  │    ✅ api.anthropic.com (Claude API)             │ │
│  │    ✅ github.com (git push/pull)                 │ │
│  │    ✅ registry.npmjs.org (npm install)           │ │
│  │    ❌ その他すべての外部通信                      │ │
│  └─────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────┘
```

核心は **Default-Deny ファイアウォール** だ。すべてのアウトバウンド通信をデフォルトで遮断し、必要なドメインのみ明示的に許可する。これにより、プロンプトインジェクション攻撃によるデータ流出リスクを大幅に軽減できる。

## 方法1: 公式 devcontainer（推奨）

Anthropic が公式リポジトリで提供している devcontainer 構成。VS Code の Dev Containers 拡張と連携し、最も手軽に始められる。

### ファイル構成

```
your-project/
├── .devcontainer/
│   ├── devcontainer.json    # コンテナ設定・VS Code 拡張
│   ├── Dockerfile           # イメージ定義
│   └── init-firewall.sh     # iptables ファイアウォール
└── src/
```

### Dockerfile

```dockerfile
FROM node:20

ARG CLAUDE_CODE_VERSION=latest

# 開発ツールと iptables をインストール
RUN apt-get update && apt-get install -y --no-install-recommends \
  less git procps sudo fzf zsh man-db unzip gnupg2 gh \
  iptables ipset iproute2 dnsutils aggregate jq nano vim \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

# npm グローバルディレクトリの権限設定
RUN mkdir -p /usr/local/share/npm-global && \
  chown -R node:node /usr/local/share

# コマンド履歴の永続化
ARG USERNAME=node
RUN SNIPPET="export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
  && mkdir /commandhistory \
  && touch /commandhistory/.bash_history \
  && chown -R $USERNAME /commandhistory

ENV DEVCONTAINER=true

# ワークスペースと設定ディレクトリ
RUN mkdir -p /workspace /home/node/.claude && \
  chown -R node:node /workspace /home/node/.claude

WORKDIR /workspace

# 非 root ユーザーに切り替え
USER node

ENV NPM_CONFIG_PREFIX=/usr/local/share/npm-global
ENV PATH=$PATH:/usr/local/share/npm-global/bin
ENV SHELL=/bin/zsh

# Claude Code をグローバルインストール
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}

# ファイアウォールスクリプトを配置
COPY init-firewall.sh /usr/local/bin/
USER root
RUN chmod +x /usr/local/bin/init-firewall.sh && \
  echo "node ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh" \
    > /etc/sudoers.d/node-firewall && \
  chmod 0440 /etc/sudoers.d/node-firewall
USER node
```

**ポイント:**

| 設計判断 | 理由 |
|---|---|
| `node:20` ベースイメージ | Claude Code は Node.js で動作するため |
| 非 root ユーザー (`node`) で実行 | 最小権限の原則。root 権限は firewall 初期化のみ |
| `iptables` + `ipset` をインストール | コンテナ内でファイアウォールを構成するため |
| `aggregate` コマンド | GitHub の大量 IP レンジを CIDR 集約するため |
| `DEVCONTAINER=true` 環境変数 | Claude Code がコンテナ内と認識するためのフラグ |

### devcontainer.json

```jsonc
{
  "name": "Claude Code Sandbox",
  "build": {
    "dockerfile": "Dockerfile",
    "args": {
      "CLAUDE_CODE_VERSION": "latest"
    }
  },
  // iptables の操作に NET_ADMIN / NET_RAW 権限が必要
  "runArgs": [
    "--cap-add=NET_ADMIN",
    "--cap-add=NET_RAW"
  ],
  "customizations": {
    "vscode": {
      "extensions": [
        "anthropic.claude-code"
      ]
    }
  },
  "remoteUser": "node",
  "mounts": [
    // コマンド履歴をボリュームに永続化
    "source=claude-bashhistory-${devcontainerId},target=/commandhistory,type=volume",
    // Claude の設定・認証情報をボリュームに永続化
    "source=claude-config-${devcontainerId},target=/home/node/.claude,type=volume"
  ],
  "containerEnv": {
    "NODE_OPTIONS": "--max-old-space-size=4096",
    "CLAUDE_CONFIG_DIR": "/home/node/.claude"
  },
  // ホストのプロジェクトディレクトリをマウント
  "workspaceMount": "source=${localWorkspaceFolder},target=/workspace,type=bind,consistency=delegated",
  "workspaceFolder": "/workspace",
  // コンテナ起動時にファイアウォールを初期化
  "postStartCommand": "sudo /usr/local/bin/init-firewall.sh",
  "waitFor": "postStartCommand"
}
```

**重要な設定:**

- **`--cap-add=NET_ADMIN,NET_RAW`** — iptables を操作するために必須。これがないとファイアウォールを設定できない
- **`postStartCommand`** — コンテナ起動のたびに iptables ルールを適用。ルールはコンテナ再起動で消えるため毎回必要
- **`waitFor: postStartCommand`** — ファイアウォール設定完了まで Claude の起動を待機

### init-firewall.sh（ファイアウォール）

ファイアウォールスクリプトは **Default-Deny** ポリシーで動作する。全体の流れ:

```
1. 既存ルールをフラッシュ（Docker DNS ルールは保持）
2. DNS（53番ポート）とローカルホストを許可
3. GitHub API から IP レンジを取得 → ipset に追加
4. 各ドメインを DNS 解決 → ipset に追加
5. デフォルトポリシーを DROP に設定
6. ipset に含まれる宛先のみ ACCEPT
7. 検証: example.com に接続不可、api.github.com に接続可能を確認
```

ホワイトリストに含まれるドメイン:

| ドメイン | 用途 |
|---|---|
| `api.anthropic.com` | Claude API との通信 |
| `statsig.anthropic.com` / `statsig.com` | Claude のテレメトリ |
| `sentry.io` | エラーレポート |
| `github.com` (全 IP レンジ) | git 操作 |
| `registry.npmjs.org` | npm パッケージのインストール |
| `marketplace.visualstudio.com` | VS Code 拡張 |

**検証ロジック**が組み込まれている点が秀逸だ:

```bash
# ブロックされるべき通信が実際にブロックされるか確認
if curl --connect-timeout 5 https://example.com >/dev/null 2>&1; then
    echo "ERROR: Firewall verification failed"
    exit 1
fi

# 許可された通信が通るか確認
if ! curl --connect-timeout 5 https://api.github.com/zen >/dev/null 2>&1; then
    echo "ERROR: Unable to reach GitHub API"
    exit 1
fi
```

### セットアップ手順

```bash
# 1. VS Code に Dev Containers 拡張をインストール
code --install-extension ms-vscode-remote.remote-containers

# 2. プロジェクトに .devcontainer/ を配置
cd your-project
mkdir .devcontainer
# Dockerfile, devcontainer.json, init-firewall.sh を配置

# 3. VS Code でプロジェクトを開く
code .

# 4. コマンドパレットから「Reopen in Container」を選択
# Cmd+Shift+P → "Remote-Containers: Reopen in Container"

# 5. コンテナ内のターミナルで Claude Code を起動
claude --dangerously-skip-permissions
```

## 方法2: Docker Compose による手動構成

VS Code を使わない場合や、DB などの追加サービスと組み合わせたい場合の構成。

### docker-compose.yml

```yaml
version: "3.8"
services:
  claude-dev:
    build:
      context: .devcontainer
      dockerfile: Dockerfile
    container_name: claude-dev
    cap_add:
      - NET_ADMIN   # iptables 用
      - NET_RAW
    volumes:
      - ./src:/workspace                    # ソースコードをマウント
      - claude-config:/home/node/.claude    # Claude 設定を永続化
      - claude-history:/commandhistory      # コマンド履歴を永続化
    ports:
      - "3000:3000"   # 開発サーバー用（必要に応じて）
    stdin_open: true
    tty: true
    command: >
      bash -c "sudo /usr/local/bin/init-firewall.sh && zsh"

  # 例: データベースと組み合わせる場合
  db:
    image: postgres:16
    environment:
      POSTGRES_USER: dev
      POSTGRES_PASSWORD: dev
      POSTGRES_DB: app_dev
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"

volumes:
  claude-config:
  claude-history:
  pgdata:
```

### 使い方

```bash
# コンテナを起動
docker compose up -d

# Claude Code コンテナに入る
docker compose exec claude-dev zsh

# コンテナ内で Claude を自律実行
claude --dangerously-skip-permissions -p "src/ ディレクトリの全テストを実行して、失敗するテストを修正してください"
```

## 方法3: セキュリティ強化版（tintinweb/claude-code-container）

コミュニティ製のよりセキュリティに特化した構成。入力を読み取り専用でマウントし、出力を別ディレクトリに分離する。

### 特徴

```
ホストのソース → /workspace/input  (読み取り専用)
分析結果       → /workspace/output (書き込み可能)
```

### セキュリティ強化項目

| 対策 | 設定 |
|---|---|
| 非 root ユーザー | UID 1001 で実行 |
| Linux Capabilities 制限 | 不要な権限を削除 |
| PID 制限 | 最大100プロセス |
| no-new-privileges | 権限昇格を防止 |
| ネットワーク分離 | bridge ネットワーク |
| 読み取り専用入力 | ソースの意図しない変更を防止 |

```bash
git clone https://github.com/tintinweb/claude-code-container.git
cd claude-code-container/claude-standalone
./build.sh
CLAUDE_CODE_OAUTH_TOKEN=sk-... ./run_claude.sh
```

**用途:** コードレビューや静的解析など、ソースを変更する必要のないタスクに適している。

## 方法4: 最小構成（手軽に始める場合）

既存の Dockerfile を持つプロジェクトに最小限の変更で Claude Code を追加する構成。

### Dockerfile.claude

```dockerfile
FROM node:20-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
  git curl jq iptables iproute2 dnsutils sudo \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

RUN useradd -m -s /bin/bash claude && \
  echo "claude ALL=(root) NOPASSWD: ALL" > /etc/sudoers.d/claude

USER claude
RUN npm install -g @anthropic-ai/claude-code@latest

WORKDIR /workspace
```

### 起動スクリプト

```bash
#!/bin/bash
# run-claude.sh
docker build -f Dockerfile.claude -t claude-sandbox .
docker run -it --rm \
  --cap-add=NET_ADMIN \
  -v "$(pwd)/src:/workspace" \
  -v claude-config:/home/claude/.claude \
  claude-sandbox \
  bash -c "claude --dangerously-skip-permissions"
```

**注意:** この構成にはファイアウォールが含まれないため、ネットワーク分離が不要な場合や、信頼できるリポジトリでのみ使うこと。

## 認証情報の引き継ぎ

コンテナ内で Claude Code を使うには認証が必要。毎回ログインし直すのを避ける方法:

### 方法A: ボリュームマウント（推奨）

```jsonc
// devcontainer.json
"mounts": [
  "source=claude-config,target=/home/node/.claude,type=volume"
]
```

初回のみコンテナ内で `claude /login` を実行。以降はボリュームに認証情報が永続化される。

### 方法B: ホストの設定をバインドマウント

```yaml
volumes:
  - ~/.claude:/home/node/.claude:ro   # 読み取り専用で安全に
  - ~/.claude.json:/home/node/.claude.json:ro
```

**セキュリティ上の注意:** バインドマウントの場合、コンテナ内から認証トークンにアクセスできる。悪意あるプロンプトインジェクションによってトークンが漏洩するリスクがあるため、信頼できるリポジトリでのみ使用すること。

### 方法C: 環境変数で渡す

```bash
docker run -e CLAUDE_CODE_OAUTH_TOKEN=sk-... claude-sandbox
```

CI/CD パイプラインなど、インタラクティブなログインができない環境で有用。

## ファイアウォールのカスタマイズ

### ドメインを追加する

Python パッケージを使うプロジェクトなら PyPI を追加:

```bash
# init-firewall.sh に追記
for domain in \
    "pypi.org" \
    "files.pythonhosted.org"; do
    ips=$(dig +noall +answer A "$domain" | awk '$4 == "A" {print $5}')
    while read -r ip; do
        ipset add allowed-domains "$ip"
    done < <(echo "$ips")
done
```

### よく使うドメインの一覧

| 用途 | ドメイン |
|---|---|
| PyPI (Python) | `pypi.org`, `files.pythonhosted.org` |
| crates.io (Rust) | `crates.io`, `static.crates.io` |
| Maven (Java) | `repo1.maven.org` |
| Docker Hub | `registry-1.docker.io`, `auth.docker.io` |
| Google Fonts | `fonts.googleapis.com`, `fonts.gstatic.com` |

### ドメインの追加方法を体系化する

プロジェクトごとにホワイトリストが異なる場合、設定ファイルで管理する:

```bash
# allowed-domains.conf
# 1行1ドメイン。# でコメント
api.anthropic.com
github.com
registry.npmjs.org
# プロジェクト固有
pypi.org
files.pythonhosted.org
```

```bash
# init-firewall.sh 内で読み込み
while IFS= read -r domain; do
    [[ "$domain" =~ ^#.*$ || -z "$domain" ]] && continue
    ips=$(dig +noall +answer A "$domain" | awk '$4 == "A" {print $5}')
    while read -r ip; do
        ipset add allowed-domains "$ip"
    done < <(echo "$ips")
done < /workspace/.devcontainer/allowed-domains.conf
```

## `--dangerously-skip-permissions` なしでの安全な運用

コンテナ化しても、フラグを使わず **許可リスト (`settings.json`)** で細かく制御する方法もある:

```jsonc
// .claude/settings.json
{
  "permissions": {
    "allow": [
      "Bash(git:*)",
      "Bash(npm:*)",
      "Bash(node:*)",
      "Bash(npx:*)",
      "Bash(ls:*)",
      "Bash(mkdir:*)",
      "Bash(mv:*)",
      "Bash(head:*)",
      "Bash(tail:*)",
      "Write(*)",
      "Read(*)"
    ],
    "deny": [
      "Bash(rm:-rf *)",
      "Bash(curl:*)",
      "Bash(wget:*)"
    ]
  }
}
```

この方法なら `--dangerously-skip-permissions` なしでも大半の操作が自動承認され、破壊的コマンドのみブロックできる。**コンテナ化と組み合わせるとより安全。**

## セキュリティ上の注意点

### コンテナでも防げないリスク

| リスク | 説明 | 対策 |
|---|---|---|
| 認証トークンの漏洩 | コンテナ内にマウントされた `.claude` の認証情報にアクセスされる | 信頼できるリポジトリのみで使用 |
| GitHub への破壊的操作 | git 認証が有効ならリポジトリへの force push 等が可能 | `git config --global receive.denyNonFastForwards true` やブランチ保護ルール |
| マウントされたソースの破壊 | bind mount したディレクトリは書き込み可能 | 重要なファイルは別ブランチやバックアップで保護 |
| プロンプトインジェクション | 悪意あるリポジトリに埋め込まれた指示を Claude が実行 | ファイアウォールで通信先を制限（最重要） |

### 推奨プラクティス

1. **信頼できるリポジトリでのみ使用** — 公式ドキュメントでも明記されている最重要ルール
2. **ファイアウォールは必ず有効化** — ネットワーク分離なしの `--dangerously-skip-permissions` は避ける
3. **マウントは最小限に** — プロジェクトのサブディレクトリのみマウントし、ホームディレクトリ全体を公開しない
4. **定期的にコンテナを再構築** — 長期間使い続けたコンテナは状態が汚染される可能性がある
5. **git の状態を確認してから起動** — クリーンな状態から始めることで、意図しない変更を diff で検出できる

## 各方式の比較

| 方式 | 難易度 | ファイアウォール | VS Code 連携 | 向いている用途 |
|---|---|---|---|---|
| 公式 devcontainer | 低 | あり | あり | 日常の開発作業 |
| Docker Compose | 中 | あり | なし | DB 等と組み合わせる場合 |
| tintinweb 構成 | 中 | あり | なし | 読み取り専用の分析・レビュー |
| 最小構成 | 低 | なし | なし | 信頼できるリポジトリでの素早い実行 |

## まとめ

`--dangerously-skip-permissions` は Claude Code の真価を発揮するフラグだが、名前の通り「危険」なので、コンテナによる分離が事実上の必須条件となっている。

最も手軽なのは **公式 devcontainer をそのまま使う方法** だ。VS Code で「Reopen in Container」するだけで、ファイアウォール付きのサンドボックスが手に入る。独自のサービス構成が必要なら Docker Compose、セキュリティを最大化するなら読み取り専用マウントの構成を選ぶ。

## 参考リンク

- [Anthropic 公式: Development containers](https://code.claude.com/docs/en/devcontainer)
- [公式 .devcontainer ディレクトリ (GitHub)](https://github.com/anthropics/claude-code/tree/main/.devcontainer)
- [tintinweb/claude-code-container](https://github.com/tintinweb/claude-code-container)
- [textcortex/claude-code-sandbox](https://github.com/textcortex/claude-code-sandbox)
- [Sandboxing AI Coding Agents (mfyz.com)](https://mfyz.com/ai-coding-agent-sandbox-container/)
- [Running Claude Code Safely in Devcontainers (solberg.is)](https://www.solberg.is/claude-devcontainer)
- [Claude Code --dangerously-skip-permissions 利用ガイド (ksred.com)](https://www.ksred.com/claude-code-dangerously-skip-permissions-when-to-use-it-and-when-you-absolutely-shouldnt/)
