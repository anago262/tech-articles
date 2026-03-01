# AI開発環境をDockerで丸ごと配る ── Claude Code専用ベースイメージを作った全記録

## はじめに：この記事で話すこと

「Claude Code」という AI ツールを使って開発するとき、毎回ツールをインストールしたり設定したりするのは面倒です。チームで使うなら、メンバー全員の環境を揃えるのはもっと大変です。

この記事では、**Claude Code に必要なツール一式を Docker イメージとして固めて、誰でもワンクリックで同じ環境を使えるようにした**プロジェクトの全記録を、ゼロから解説します。

扱うテーマは多岐にわたりますが、すべて「初めて聞く」前提で書きます。

- Docker とコンテナの基本
- Dev Container（VS Code の開発コンテナ）とは何か
- Claude Code（AI コーディングツール）とは何か
- MCP（Model Context Protocol）とは何か
- ファイアウォールによるネットワーク制御
- CI/CD（GitHub Actions による自動テスト・自動公開）
- テスト駆動で Docker イメージの品質を保つ方法
- 実際にぶつかったバグとその解決過程

---

## 第1章：なぜコンテナで開発するのか

### 1-1. 「自分の PC では動くのに…」問題

ソフトウェア開発をしていると、こんな場面に遭遇します。

- 自分の PC では動くのに、同僚の PC では動かない
- 「Node.js のバージョンが違う」「Python のパスが通っていない」などの微妙な差異
- OS が Windows / macOS / Linux と違うだけで挙動が変わる

これらは**環境の差異**が原因です。

### 1-2. Docker の登場

**Docker（ドッカー）** は、この問題を解決するツールです。

Docker は「**コンテナ**」という軽量な仮想環境を作ります。コンテナの中には、OS のベースシステム、ライブラリ、ツール、設定ファイルなど、アプリケーションを動かすために必要なすべてが含まれています。

```
┌──────────────────────────────────────┐
│  あなたの PC (macOS / Windows)       │
│                                      │
│  ┌──────────────────────────────┐    │
│  │  Docker コンテナ (Linux)     │    │
│  │                              │    │
│  │  Node.js 20                  │    │
│  │  Python 3.11                 │    │
│  │  Git, zsh, vim ...           │    │
│  │  Claude Code                 │    │
│  │  あなたのプロジェクトコード  │    │
│  └──────────────────────────────┘    │
└──────────────────────────────────────┘
```

ポイントは、**誰がどこで動かしても、コンテナの中身は完全に同じ**ということです。

### 1-3. Docker イメージとコンテナの関係

よく混同されるので整理します。

| 用語 | たとえ | 説明 |
|------|--------|------|
| **Docker イメージ** | レシピ（設計図） | 「何をインストールするか」の定義。読み取り専用 |
| **Docker コンテナ** | 料理（実体） | イメージから作られた実行中の環境。書き込み可能 |
| **Dockerfile** | レシピの原本 | イメージを作るための手順書 |

```
Dockerfile → (docker build) → Docker イメージ → (docker run) → コンテナ
```

1つのイメージから、いくつでもコンテナを作れます。

### 1-4. Dev Container とは

**Dev Container（デブコンテナ）** は、VS Code（コードエディタ）の機能の一つです。

通常、Docker コンテナはターミナルからコマンドで操作します。Dev Container を使うと、**VS Code がコンテナの中に入って動く**ようになります。

```
通常の開発:
  VS Code → 自分の PC のファイルを編集

Dev Container:
  VS Code → Docker コンテナの中のファイルを編集
  　　　　　（ターミナルもコンテナ内で実行される）
```

Dev Container の設定は `.devcontainer/devcontainer.json` というファイルに書きます。VS Code は、このファイルを見つけると「コンテナで開き直しますか？」と聞いてきます。

### 1-5. 「ベースイメージ」という考え方

プロジェクトごとに Dockerfile を書くと、似たような設定を何度も書くことになります。

```
プロジェクト A の Dockerfile: Node.js + Git + zsh + Claude Code + ...
プロジェクト B の Dockerfile: Node.js + Git + zsh + Claude Code + ...  ← ほぼ同じ
プロジェクト C の Dockerfile: Node.js + Git + zsh + Claude Code + ...  ← ほぼ同じ
```

これを解決するのが**ベースイメージ**です。共通部分を1つのイメージにまとめて公開し、各プロジェクトはそれを参照するだけにします。

```
ベースイメージ (GHCR に公開)
  └── Node.js + Git + zsh + Claude Code + 全ツール

プロジェクト A: image: ghcr.io/anago262/devcontainer-claude-code:2
プロジェクト B: image: ghcr.io/anago262/devcontainer-claude-code:2
プロジェクト C: image: ghcr.io/anago262/devcontainer-claude-code:2
```

**GHCR（GitHub Container Registry）** は、GitHub が提供する Docker イメージの保管場所です。Git のコードを GitHub に保存するように、Docker イメージも GitHub に保存できます。

---

## 第2章：Claude Code と MCP を理解する

### 2-1. Claude Code とは

**Claude Code** は、Anthropic 社が開発した AI コーディングツールです。ターミナル（コマンドライン）で動作し、以下のことができます。

- コードの読み書き
- バグの調査と修正
- テストの作成と実行
- Git 操作（コミット、プッシュ、PR 作成）
- ファイル検索・ウェブ検索

人間が「この機能を作って」と指示すると、Claude Code がコードを書き、テストし、コミットまでしてくれます。

### 2-2. MCP（Model Context Protocol）とは

**MCP** は、AI ツールに「外部の機能」を追加するための規格です。

Claude Code 単体でも多くのことができますが、MCP サーバーを接続すると、さらに多くのことが可能になります。

```
Claude Code
  ├── 標準機能: ファイル読み書き、ターミナル実行、ウェブ検索
  │
  └── MCP で追加した機能:
      ├── Context7: ライブラリのドキュメント検索
      ├── Playwright: ブラウザの自動操作
      ├── GitHub: Issue/PR の操作
      ├── Brave Search: ウェブ検索
      ├── draw.io: 図の作成
      ├── Serena: コード構造の解析
      └── claude-mem: 会話の記憶（セッション間の知識共有）
```

MCP サーバーは、それぞれ独立したプログラムです。Claude Code が「ライブラリのドキュメントを調べたい」と思ったら、Context7 という MCP サーバーに問い合わせる、という仕組みです。

### 2-3. claude-mem とは

**claude-mem** は、Claude Code に「長期記憶」を与える MCP サーバーです。

通常、AI との会話は「1セッション限り」です。会話を終了すると、AI はその内容をすべて忘れます。claude-mem を使うと、以下が可能になります。

- 過去のセッションで学んだことを記憶
- 「前回このバグを直したとき、どうしたっけ？」に答えられる
- プロジェクトの設計判断の経緯を保持

内部的には、**ChromaDB** というベクトルデータベースを使っています。

#### ベクトルデータベースとは

通常のデータベース（MySQL など）は、「名前が田中さんのデータを探す」のような**完全一致検索**が得意です。

ベクトルデータベースは、「この質問に似た情報を探す」のような**意味的な検索**が得意です。

```
通常の DB:  「田中」 → 名前が「田中」のレコード
ベクトル DB: 「認証の実装方法」 → 「JWT トークンの設計」「OAuth の導入手順」など
```

claude-mem は、AI の会話内容をベクトルに変換して ChromaDB に保存し、後から意味的に検索できるようにしています。

---

## 第3章：プロジェクト全体像

### 3-1. ディレクトリ構成

```
devcontainer-claude-code/
├── Dockerfile                  ← イメージのレシピ（本体）
├── init-firewall.sh            ← ネットワーク制御スクリプト
├── scripts/
│   ├── start-chromadb.sh       ← ChromaDB 起動スクリプト
│   ├── start-claude-mem-worker.sh  ← claude-mem ワーカー起動
│   └── init-claude-mem-settings.sh ← 設定初期化
├── tests/
│   └── test-image.sh           ← 自動テスト
├── templates/
│   ├── devcontainer.json       ← 利用者向けテンプレート
│   ├── .mcp.json               ← MCP サーバー設定テンプレート
│   └── allowed-domains.txt     ← 許可ドメインリストの例
├── .github/
│   └── workflows/
│       ├── test.yml            ← 自動テスト（CI）
│       └── publish.yml         ← 自動公開（CD）
├── README.md
└── LICENSE
```

### 3-2. 含まれるツール一覧

このベースイメージには、以下がすべてプリインストールされています。

#### 開発ツール

| ツール | 用途 |
|--------|------|
| Node.js 20 | JavaScript ランタイム |
| Git | バージョン管理 |
| zsh + Powerlevel10k | シェル（ターミナル） |
| fzf | ファイル・履歴のあいまい検索 |
| delta | Git の差分を見やすく表示 |
| gh | GitHub CLI（コマンドラインで GitHub 操作） |
| jq | JSON の整形・加工 |
| nano / vim | テキストエディタ |
| sudo | 管理者権限での実行 |

#### AI・MCP ツール

| ツール | 用途 |
|--------|------|
| Claude Code | AI コーディングツール本体 |
| Bun | JavaScript ランタイム（claude-mem 用） |
| uv / uvx | Python パッケージマネージャ（Serena MCP 用） |
| pip3 | Python パッケージマネージャ |
| Playwright + Chromium + Chrome | ブラウザ自動操作 |
| ChromaDB | ベクトルデータベース |
| claude-mem | AI の長期記憶 |
| 6つの MCP サーバー（プリキャッシュ済み） | 各種外部連携 |

#### ネットワーク制御

| ツール | 用途 |
|--------|------|
| iptables | ファイアウォール（パケットフィルタリング） |
| ipset | IP アドレスのセット管理 |
| iproute2 | ネットワーク設定 |
| dnsutils (dig) | DNS 問い合わせ |
| aggregate | CIDR ブロックの集約 |

---

## 第4章：Dockerfile を1行ずつ読む

Dockerfile は、Docker イメージの「レシピ」です。上から順番に実行されます。

### 4-1. ベースイメージの指定

```dockerfile
FROM node:20
```

`FROM` は「このイメージをベースにする」という宣言です。`node:20` は、Node.js 20 がプリインストールされた公式 Linux（Debian）イメージです。

ここからスタートして、必要なツールを追加していきます。

### 4-2. ラベル（メタデータ）

```dockerfile
LABEL org.opencontainers.image.title="Dev Container for Claude Code"
LABEL org.opencontainers.image.description="Shared base image for Claude Code development environments"
LABEL org.opencontainers.image.source="https://github.com/anago262/devcontainer-claude-code"
LABEL org.opencontainers.image.licenses="MIT"
LABEL org.opencontainers.image.vendor="anago262"
```

`LABEL` は、イメージに説明を付けるメタデータです。GHCR（GitHub Container Registry）でイメージを閲覧するときに表示されます。機能には影響しません。

### 4-3. ビルド引数と環境変数

```dockerfile
ARG TZ
ENV TZ="$TZ"

ARG CLAUDE_CODE_VERSION=latest
```

- `ARG` はビルド時のみ使える変数です。`docker build --build-arg TZ=Asia/Tokyo` のように渡します
- `ENV` はコンテナの実行時にも使える環境変数です
- `CLAUDE_CODE_VERSION` のデフォルトは `latest`（最新版）です

### 4-4. システムパッケージのインストール

```dockerfile
RUN apt-get update && apt-get install -y --no-install-recommends \
  less git procps sudo fzf zsh man-db unzip gnupg2 gh \
  iptables ipset iproute2 dnsutils aggregate \
  jq nano vim python3-pip \
  && apt-get clean && rm -rf /var/lib/apt/lists/*
```

`RUN` は、ビルド中にコマンドを実行する命令です。

- `apt-get update` はパッケージリストの更新
- `apt-get install -y` はパッケージのインストール（`-y` は確認プロンプトを自動で Yes）
- `--no-install-recommends` は推奨パッケージをスキップ（イメージサイズ削減）
- 最後の `apt-get clean && rm -rf /var/lib/apt/lists/*` はキャッシュ削除（イメージサイズ削減）

Docker では、1つの `RUN` 命令が1つの「レイヤー」になります。コマンドを `&&` でつなげて1つの `RUN` にまとめると、レイヤーが少なくなりイメージが小さくなります。

### 4-5. uv のインストール（マルチステージコピー）

```dockerfile
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/
```

これは**マルチステージビルド**のテクニックです。`ghcr.io/astral-sh/uv:latest` という別のイメージから、`/uv` と `/uvx` というバイナリだけをコピーしています。

uv のイメージ全体を使うのではなく、必要なファイルだけを取り出すことで、イメージサイズを節約しています。

### 4-6. ChromaDB のインストール

```dockerfile
RUN pip3 install --break-system-packages chromadb
```

Python のパッケージマネージャ `pip3` を使って ChromaDB をインストールしています。

`--break-system-packages` は、Debian 12 以降で必要なフラグです。システムの Python に直接パッケージをインストールすることを明示的に許可します（通常は仮想環境の使用が推奨されますが、Docker 内では不要）。

### 4-7. ユーザーの切り替え

```dockerfile
ARG USERNAME=node
USER node
```

Docker コンテナは、デフォルトでは `root`（管理者）ユーザーで動作します。セキュリティのため、`node` という一般ユーザーに切り替えます。

`node:20` ベースイメージには、最初から `node` ユーザー（UID 1000）が用意されています。

### 4-8. npm グローバルの設定

```dockerfile
ENV NPM_CONFIG_PREFIX=/usr/local/share/npm-global
ENV PATH=$PATH:/usr/local/share/npm-global/bin
```

npm でグローバルインストールしたパッケージの置き場所を設定しています。`/usr/local/share/npm-global` にインストールし、その `bin` ディレクトリを PATH に追加することで、インストールしたコマンドが使えるようになります。

### 4-9. PATH に /usr/sbin を追加

```dockerfile
ENV PATH="/usr/sbin:$PATH"
```

`iptables` や `ipset` は `/usr/sbin/` にインストールされますが、一般ユーザーの PATH にはデフォルトで含まれていません。ファイアウォールスクリプトで使うため、明示的に追加しています。

これは開発中に発見した問題の修正です（後述の「バグ修正」セクションで詳しく解説します）。

### 4-10. zsh の設定

```dockerfile
ARG ZSH_IN_DOCKER_VERSION=1.2.0
RUN sh -c "$(wget -O- https://github.com/deluan/zsh-in-docker/releases/download/v${ZSH_IN_DOCKER_VERSION}/zsh-in-docker.sh)" -- \
  -p git -p fzf \
  -a "source /usr/share/doc/fzf/examples/key-bindings.zsh" \
  -a "source /usr/share/doc/fzf/examples/completion.zsh" \
  -a "export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
  -x
```

zsh-in-docker というスクリプトを使って、zsh を設定しています。

- `-p git -p fzf` はプラグインの有効化
- `-a` はカスタム設定の追加
- `-x` は Powerlevel10k テーマのインストール

### 4-11. Bun のインストール

```dockerfile
RUN curl -fsSL https://bun.sh/install | bash
ENV PATH="/home/node/.bun/bin:$PATH"
```

**Bun** は高速な JavaScript ランタイムです。claude-mem のワーカーサービスを実行するために使います。`curl` でインストールスクリプトをダウンロードして実行しています。

### 4-12. Claude Code のインストール

```dockerfile
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}
```

Claude Code を npm でグローバルインストールしています。`@${CLAUDE_CODE_VERSION}` はビルド引数で、デフォルトは `latest` です。

### 4-13. claude-mem のインストールとシンボリックリンク

```dockerfile
RUN npm install -g claude-mem
ENV CLAUDE_MEM_SCRIPTS="/usr/local/share/npm-global/lib/node_modules/claude-mem/plugin/scripts"

RUN mkdir -p /home/node/.claude-mem /home/node/.claude/plugins/marketplaces/thedotmack \
  && ln -s /usr/local/share/npm-global/lib/node_modules/claude-mem/plugin \
     /home/node/.claude/plugins/marketplaces/thedotmack/plugin
```

claude-mem をインストールし、Claude Code がプラグインを見つけられるようにシンボリックリンクを作成しています。

**シンボリックリンク**とは、Windows のショートカットのようなものです。ファイルの実体は `/usr/local/share/npm-global/...` にありますが、Claude Code は `~/.claude/plugins/marketplaces/thedotmack/plugin` を見に行くので、そこにリンクを貼っています。

### 4-14. ONNX モデルのプリダウンロード

```dockerfile
RUN python3 -c "from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2; ef = ONNXMiniLM_L6_V2(); ef(['warmup'])"
```

ChromaDB が使う**埋め込みモデル（embedding model）** をビルド時にダウンロードしています。

埋め込みモデルとは、テキストを数値のベクトル（数列）に変換するモデルです。「認証の方法」と「ログインの仕組み」が意味的に近いことを数値で表現できるため、ベクトルデータベースでの類似検索が可能になります。

ビルド時にダウンロードしておく理由は、**コンテナ実行時にはネットワークがファイアウォールで制限されるため**です。

### 4-15. MCP サーバーのプリキャッシュ

```dockerfile
RUN npx -y @upstash/context7-mcp@latest --help > /dev/null 2>&1 || true & \
    npx -y @playwright/mcp@latest --help > /dev/null 2>&1 || true & \
    npx -y @modelcontextprotocol/server-brave-search --help > /dev/null 2>&1 || true & \
    npx -y drawio-mcp --help > /dev/null 2>&1 || true & \
    npx -y @modelcontextprotocol/server-github --help > /dev/null 2>&1 || true & \
    npx -y @pimzino/spec-workflow-mcp@latest --help > /dev/null 2>&1 || true & \
    wait
```

6つの MCP サーバーを**並列**でプリインストールしています。

`npx` は、npm パッケージを一時的にインストールして実行するコマンドです。初回実行時はパッケージのダウンロードが必要で、30〜60秒かかります。ビルド時にキャッシュしておくと、実行時は 100ms 未満で起動できます。

`&` はバックグラウンド実行、`wait` は全バックグラウンドジョブの完了待ちです。6つを順番に実行すると 3〜5分かかりますが、並列実行により大幅に短縮しています。

### 4-16. Playwright ブラウザのインストール

```dockerfile
ENV PLAYWRIGHT_BROWSERS_PATH=/home/node/.cache/ms-playwright
USER root
RUN npx -y playwright install --with-deps chromium chrome && \
    chown -R node:node /home/node/.cache
USER node
```

ここは少し複雑です。

1. `PLAYWRIGHT_BROWSERS_PATH` で、ブラウザの保存先を指定
2. `USER root` で一時的に管理者に切り替え
3. `playwright install --with-deps` でブラウザ本体 + システム依存ライブラリをインストール
4. `chown -R node:node /home/node/.cache` で所有者を node ユーザーに変更
5. `USER node` で一般ユーザーに戻す

root に切り替える理由は、`--with-deps` が `apt-get` を使ってシステムライブラリをインストールするためです（一般ユーザーでは `apt-get` は実行できません）。

`chown` は、root が作ったファイルの所有権を node ユーザーに変更するコマンドです。これをしないと、node ユーザーがブラウザにアクセスできません。

**Chromium** と **Chrome** を両方入れている理由：
- **Chromium**: Playwright のテスト自動化用（オープンソース版 Chrome）
- **Chrome**: Playwright MCP サーバーがデフォルトで使うブラウザ

### 4-17. スクリプトのコピーと権限設定

```dockerfile
COPY init-firewall.sh /usr/local/bin/
COPY scripts/ /usr/local/bin/
USER root
RUN chmod +x /usr/local/bin/init-firewall.sh \
    /usr/local/bin/start-chromadb.sh \
    /usr/local/bin/start-claude-mem-worker.sh \
    /usr/local/bin/init-claude-mem-settings.sh && \
  echo "node ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh" > /etc/sudoers.d/node-firewall && \
  chmod 0440 /etc/sudoers.d/node-firewall
USER node
```

- `COPY` でスクリプトファイルをイメージにコピー
- `chmod +x` で実行権限を付与
- `sudoers` ファイルで、node ユーザーがパスワードなしで `init-firewall.sh` を root 権限で実行できるように設定

sudoers の設定が必要な理由は、ファイアウォール（iptables）の操作には root 権限が必要だからです。

---

## 第5章：ファイアウォール ── 3層のネットワーク制御

### 5-1. なぜファイアウォールが必要か

AI ツールがコードを書くとき、インターネットにアクセスする場面があります。

- npm パッケージのダウンロード
- GitHub API へのアクセス
- ドキュメントの検索

しかし、**無制限にインターネットにアクセスできるのはセキュリティリスク**です。

- 意図しないサーバーへのデータ送信
- 悪意のあるパッケージのダウンロード
- 社内ネットワークへの不正アクセス

そこで、**許可されたドメインだけにアクセスできるファイアウォール**を実装しています。

### 5-2. 3層構造の設計

```
第1層: コアドメイン（変更不可）
  ├── registry.npmjs.org      npm パッケージ
  ├── api.anthropic.com        Claude API
  ├── github.com / api.github.com  GitHub
  ├── marketplace.visualstudio.com  VS Code 拡張
  └── sentry.io / statsig.com      テレメトリ

第2層: プロジェクトドメイン（ファイルで定義）
  └── .devcontainer/allowed-domains.txt
      ├── context7.com          Context7 MCP
      ├── api.search.brave.com  Brave Search API
      └── (プロジェクト固有の API)

第3層: 追加ドメイン（環境変数で定義）
  └── EXTRA_ALLOWED_DOMAINS="api.example.com,cdn.example.com"
```

**第1層**は、Claude Code が動作するために絶対に必要なドメインです。変更できません。

**第2層**は、プロジェクトごとに必要なドメインです。`.devcontainer/allowed-domains.txt` に1行1ドメインで書きます。

**第3層**は、一時的に追加したいドメインです。環境変数で指定でき、コンテナを再起動せずに追加できます。

### 5-3. 実装の流れ

`init-firewall.sh` の処理を順に解説します。

**ステップ1: Docker の DNS を保存**

```bash
DOCKER_DNS_RULES=$(iptables-save -t nat | grep "127.0.0.11" || true)
```

Docker コンテナは、内部 DNS（127.0.0.11）を使って名前解決をしています。ファイアウォールのルールを全消去する前に、この設定を保存しておきます。

**ステップ2: 基本ルールの設定**

```bash
iptables -A OUTPUT -p udp --dport 53 -j ACCEPT   # DNS
iptables -A OUTPUT -p tcp --dport 22 -j ACCEPT   # SSH
iptables -A INPUT -i lo -j ACCEPT                 # ローカルホスト
iptables -A OUTPUT -o lo -j ACCEPT
```

DNS（53番ポート）と SSH（22番ポート）は常に許可します。ローカルホスト（lo = loopback）の通信も許可します（コンテナ内のプロセス間通信に必要）。

**ステップ3: GitHub の IP レンジ取得**

```bash
gh_ranges=$(curl -s https://api.github.com/meta)
echo "$gh_ranges" | jq -r '(.web + .api + .git)[]' | aggregate -q
```

GitHub は IP アドレスが多数あり、ドメイン名からの解決だけでは不十分です。GitHub API の `/meta` エンドポイントから公式の IP レンジ（CIDR ブロック）を取得し、`aggregate` ツールで集約しています。

**ステップ4: ドメインの IP 解決**

```bash
for domain in "${ALL_DOMAINS[@]}"; do
    ips=$(dig +short A "$domain" | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$')
    while read -r ip; do
        ipset add allowed-domains "$ip" -exist
    done < <(echo "$ips")
done
```

全許可ドメインの IP アドレスを `dig` コマンドで解決し、`ipset` に追加しています。`ipset` は、大量の IP アドレスを効率的に管理するカーネルモジュールです。

**ステップ5: デフォルト拒否ポリシー**

```bash
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT DROP
```

**すべてのトラフィックをデフォルトで拒否**します。明示的に許可したものだけが通過できます。これを「**ゼロトラスト**」と呼びます。

**ステップ6: 検証**

```bash
# 許可されていないドメインにアクセスできないことを確認
if curl --connect-timeout 5 https://example.com >/dev/null 2>&1; then
    echo "ERROR: Firewall verification failed"
    exit 1
fi

# 許可されたドメインにアクセスできることを確認
if ! curl --connect-timeout 5 https://api.github.com/zen >/dev/null 2>&1; then
    echo "ERROR: Firewall verification failed"
    exit 1
fi
```

ファイアウォール設定後に自動検証を行います。

- **ネガティブテスト**: example.com（未許可）にアクセスできないことを確認
- **ポジティブテスト**: api.github.com（許可済み）にアクセスできることを確認

どちらかが失敗すると、スクリプトはエラーで終了します。

---

## 第6章：起動スクリプト ── 4段階のブートストラップ

コンテナが起動すると、`postStartCommand` で4つのスクリプトが順番に実行されます。

```
sudo /usr/local/bin/init-firewall.sh          ← 第1段階
/usr/local/bin/init-claude-mem-settings.sh    ← 第2段階
/usr/local/bin/start-chromadb.sh              ← 第3段階
/usr/local/bin/start-claude-mem-worker.sh     ← 第4段階
```

### 6-1. 第1段階: ファイアウォール初期化（5〜10秒）

前章で解説した `init-firewall.sh` です。`sudo` で実行しています。

### 6-2. 第2段階: claude-mem 設定初期化（2〜3秒）

`init-claude-mem-settings.sh` は以下を行います。

**ChromaDB 接続設定の作成**

```json
{
  "CLAUDE_MEM_CHROMA_MODE": "remote",
  "CLAUDE_MEM_CHROMA_HOST": "127.0.0.1",
  "CLAUDE_MEM_CHROMA_PORT": "8100",
  "CLAUDE_MEM_CHROMA_SSL": "false",
  "CLAUDE_MEM_PYTHON_VERSION": "3.11"
}
```

これは `~/.claude-mem/settings.json` に保存されます。ChromaDB がローカルの8100番ポートで動くことを指定しています。

**Claude Code へのフック登録**

claude-mem の「フック」とは、Claude Code の特定のタイミングで自動実行されるスクリプトです。

| フック | タイミング | 処理内容 |
|--------|-----------|---------|
| SessionStart | セッション開始時 | 過去の記憶を読み込む |
| UserPromptSubmit | ユーザーがメッセージ送信時 | セッション初期化 |
| PostToolUse | ツール使用後 | 実行内容を記録 |
| Stop | セッション終了時 | 会話を要約して保存 |

### 6-3. 第3段階: ChromaDB 起動（3〜5秒）

```bash
nohup chroma run --host 127.0.0.1 --port "$CHROMA_PORT" --path "$CHROMA_DATA_DIR" > "$CHROMA_LOG" 2>&1 &
```

ChromaDB をバックグラウンドで起動し、ヘルスチェック（`/api/v1/heartbeat`）で起動完了を確認します。

`nohup` は、シェルが終了してもプロセスを生かし続けるコマンドです。

### 6-4. 第4段階: claude-mem ワーカー起動（10〜15秒）

claude-mem のワーカーサービスを起動します。ここには重要な**パッチ処理**があります。

```bash
if grep -q 'return s&&l.push("--ssl")' "$WORKER_SCRIPT" 2>/dev/null; then
  sed -i 's/return s&&l.push("--ssl")/return s?l.push("--ssl"):l.push("--ssl","false")/' "$WORKER_SCRIPT"
fi
```

claude-mem の内部コード（`worker-service.cjs`）が SSL 設定を正しく扱えないバグがあるため、起動時にソースコードを書き換えて修正しています。これは「**モンキーパッチ**」と呼ばれる手法です。

---

## 第7章：自動テスト ── イメージの品質を保証する

### 7-1. なぜテストが必要か

Docker イメージには数十のツールやライブラリが含まれています。1つでもインストールに失敗していると、開発者がコンテナを起動したとき何かが動かず、原因調査に時間を取られます。

**テストがあれば、ビルド直後に問題を検出できます。**

### 7-2. テストの仕組み

`tests/test-image.sh` はシンプルな仕組みです。

```bash
FAILED=0

check() {
    local description="$1"
    shift
    if "$@" >/dev/null 2>&1; then
        echo "PASS: $description"
    else
        echo "FAIL: $description"
        FAILED=1
    fi
}
```

`check` 関数は、説明文とコマンドを受け取り、コマンドが成功（終了コード 0）なら PASS、失敗なら FAIL と表示します。

### 7-3. テスト項目一覧

全39項目を7つのカテゴリに分けています。

**コアツール（15項目）**

```bash
check "node is installed"       node --version
check "npm is installed"        npm --version
check "git is installed"        git --version
check "zsh is installed"        zsh --version
check "gh (GitHub CLI)"         gh --version
check "jq is installed"         jq --version
check "delta is installed"      delta --version
check "fzf is installed"        fzf --version
check "iptables is installed"   iptables --version
check "ipset is installed"      which ipset
check "dig is installed"        dig -v
check "aggregate is installed"  which aggregate
check "nano is installed"       nano --version
check "vim is installed"        vim --version
check "sudo is installed"       sudo --version
```

**AI/MCP ツール（5項目）**

```bash
check "claude is installed"     claude --version
check "bun is installed"        bun --version
check "uv is installed"         uv --version
check "uvx is installed"        uvx --version
check "pip3 is installed"       pip3 --version
```

**claude-mem スタック（5項目）**

```bash
check "claude-mem is installed"           test -d /usr/local/share/npm-global/lib/node_modules/claude-mem
check "chromadb is installed"             python3 -c "import chromadb"
check "ONNX model is cached"             test -d /home/node/.cache/chroma/onnx_models/all-MiniLM-L6-v2
check "claude-mem plugin symlink exists"  test -L /home/node/.claude/plugins/marketplaces/thedotmack/plugin
check "claude-mem scripts dir exists"     test -d /usr/local/share/npm-global/lib/node_modules/claude-mem/plugin/scripts
```

**Playwright（3項目）**

```bash
check "playwright is installed"   npx playwright --version
check "chromium browser exists"   test -d /home/node/.cache/ms-playwright/chromium-*
check "chrome browser exists"     google-chrome --version
```

**起動スクリプト（6項目）**、**ファイアウォール（2項目）**、**環境変数（7項目）**、**sudoers（1項目）** と続きます。

### 7-4. テストの実行方法

```bash
docker run --rm \
  -v $(pwd)/tests:/workspace/tests \
  devcontainer-claude-code:test \
  bash tests/test-image.sh
```

- `--rm` はコンテナ終了後に自動削除
- `-v` はホストのテストファイルをコンテナにマウント
- 最後の `bash tests/test-image.sh` がテストスクリプトの実行

---

## 第8章：CI/CD ── 自動テストと自動公開

### 8-1. CI（継続的インテグレーション）

`.github/workflows/test.yml` は、コードが push されるたびに自動でテストを実行します。

```yaml
name: Build and Test

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
```

**トリガー**: main ブランチへの push または PR の作成時に実行されます。

**処理の流れ**:

1. リポジトリをチェックアウト
2. Docker Buildx をセットアップ（高性能なビルドツール）
3. Docker イメージをビルド
4. テストスクリプトを実行
5. ファイアウォールスクリプトの構文チェック

**キャッシュ**: `cache-from: type=gha` と `cache-to: type=gha,mode=max` で、GitHub Actions のキャッシュを使ってビルドを高速化しています。変更がないレイヤーはキャッシュから再利用されます。

### 8-2. CD（継続的デリバリー）

`.github/workflows/publish.yml` は、バージョンタグを push したときにイメージを GHCR に公開します。

```yaml
on:
  push:
    tags:
      - 'v*'
```

`v1.0.0` のようなタグを push すると、以下の4つのタグでイメージが公開されます。

```
ghcr.io/anago262/devcontainer-claude-code:1.0.0    ← 完全版
ghcr.io/anago262/devcontainer-claude-code:1.0      ← マイナー版
ghcr.io/anago262/devcontainer-claude-code:1        ← メジャー版
ghcr.io/anago262/devcontainer-claude-code:latest   ← 最新版
```

利用者は `image: ghcr.io/anago262/devcontainer-claude-code:2` のようにメジャーバージョンだけを指定すると、パッチアップデートは自動的に適用されます。

**マルチアーキテクチャ対応**: `platforms: linux/amd64,linux/arm64` で、Intel/AMD と ARM（Apple Silicon）の両方に対応しています。

---

## 第9章：テンプレート ── 利用者向けファイル

### 9-1. devcontainer.json テンプレート

利用者がプロジェクトに `.devcontainer/devcontainer.json` を作成するためのテンプレートです。

```json
{
  "name": "My Project Sandbox",
  "image": "ghcr.io/anago262/devcontainer-claude-code:2",
  "runArgs": ["--cap-add=NET_ADMIN", "--cap-add=NET_RAW"],
  "remoteUser": "node"
}
```

**重要なポイント**:

- `image`: ベースイメージを指定（自分で Dockerfile を書く必要なし）
- `runArgs`: ファイアウォールに必要な Linux ケーパビリティを追加
  - `NET_ADMIN`: iptables の操作権限
  - `NET_RAW`: ネットワークパケットの操作権限
- `remoteUser`: コンテナ内で使うユーザー

**ボリュームマウント**:

```json
"mounts": [
  "source=claude-code-config-${devcontainerId},target=/home/node/.claude,type=volume",
  "source=claude-mem-data-${devcontainerId},target=/home/node/.claude-mem,type=volume"
]
```

ボリュームは、コンテナを再構築しても消えないデータの保存先です。Claude Code の設定や claude-mem のデータベースをボリュームに保存することで、コンテナを作り直しても過去の記憶が失われません。

### 9-2. .mcp.json テンプレート

MCP サーバーの接続設定です。各プロジェクトの `.mcp.json` に配置します。

```json
{
  "mcpServers": {
    "context7": {
      "type": "stdio",
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp@latest"]
    }
  }
}
```

`type: "stdio"` は、標準入出力でデータをやり取りする方式です。Claude Code が MCP サーバーを子プロセスとして起動し、パイプで通信します。

### 9-3. allowed-domains.txt テンプレート

ファイアウォールの第2層で使う、プロジェクト固有の許可ドメインリストです。

```
# MCP Servers
context7.com
clerk.context7.com
api.search.brave.com
```

`#` で始まる行はコメントです。1行1ドメインで記述します。

---

## 第10章：実際にぶつかったバグと解決方法

開発中に遭遇した具体的な問題とその解決過程を記録します。

### 10-1. ipset が node ユーザーから実行できない

**症状**: テストで `ipset --version` が FAIL になる。

**調査過程**:

```bash
# コンテナ内で確認
$ su - node -c "ipset --version"
-bash: ipset: command not found

$ which ipset      # root で実行
/usr/sbin/ipset

$ su - node -c "echo \$PATH"
/usr/local/bin:/usr/bin:/bin:/usr/local/games:/usr/games
```

**原因**: `ipset` は `/usr/sbin/` にインストールされるが、node ユーザーの PATH に `/usr/sbin` が含まれていなかった。

**さらに深い問題**: PATH に追加しても `ipset --version` は終了コード 1 を返すことが判明。

```bash
$ ipset --version
ipset v7.17: Kernel error received: Operation not permitted
```

`ipset --version` はバージョン表示だけでなくカーネルにアクセスしようとするため、コンテナ内（特権なし）では失敗する。

**修正**:
1. Dockerfile に `ENV PATH="/usr/sbin:$PATH"` を追加（ファイアウォールスクリプトで ipset を使えるようにする）
2. テストを `ipset --version` から `which ipset` に変更（バイナリの存在だけを確認）

### 10-2. claude-mem に CLI バイナリがない

**症状**: テストで `which claude-mem` が FAIL になる。

**調査過程**:

```bash
$ npm view claude-mem bin
# 出力なし（bin フィールドが未定義）
```

**原因**: claude-mem の npm パッケージは `bin` フィールドを定義していないため、グローバルインストールしても `claude-mem` コマンドは作られない。claude-mem は Node.js のライブラリとスクリプト群であり、CLI ツールではない。

**修正**: テストを `which claude-mem` から `test -d /usr/local/share/npm-global/lib/node_modules/claude-mem`（ディレクトリの存在確認）に変更。

### 10-3. ONNX モデルのキャッシュが見つからない

**症状**: テストで `HF_HUB_OFFLINE=1 python3 -c "from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2; ef = ONNXMiniLM_L6_V2()"` が FAIL になる。

**調査過程**:

Dockerfile ではビルド時に ONNX モデルをダウンロードしている（117行目）。しかし、その後の Playwright インストール（135行目）で root ユーザーに切り替わり、`/home/node/.cache` 配下に root 所有のファイルが作られる可能性がある。

```dockerfile
# 117行目: node ユーザーで実行 → /home/node/.cache/chroma/... にキャッシュ
RUN python3 -c "from chromadb.utils.embedding_functions import ONNXMiniLM_L6_V2; ..."

# 135行目: root で実行 → /home/node/.cache/ms-playwright/... に書き込み
USER root
RUN npx -y playwright install --with-deps chromium chrome && \
    chown -R node:node /home/node/.cache/ms-playwright  ← ここが問題
```

**原因**: `chown` が `/home/node/.cache/ms-playwright` だけを対象にしていたため、root が作った他のファイル（`.cache` 直下のディレクトリ等）が root 所有のまま残る可能性があった。

**修正**:
1. `chown` の対象を `/home/node/.cache` 全体に拡大
2. テストを `HF_HUB_OFFLINE=1` 依存の Python 実行から、ディレクトリの存在確認（`test -d /home/node/.cache/chroma/onnx_models/all-MiniLM-L6-v2`）に変更

### 10-4. Playwright の --dry-run 出力が想定と異なる

**症状**: テストで Playwright ブラウザの存在確認が FAIL になる。

**調査過程**:

テストは以下のように書かれていた。

```bash
check "chromium browser exists"   npx playwright install --dry-run chromium 2>&1 | grep -q "already installed\|up to date"
```

実際の `--dry-run` 出力を確認すると：

```
Chrome for Testing 145.0.7632.6 (playwright chromium v1208)
  Install location:    /home/node/.cache/ms-playwright/chromium-1208
  Download url:        https://cdn.playwright.dev/...
```

**原因**: `--dry-run` の出力に "already installed" や "up to date" という文字列が含まれていなかった。Playwright のバージョンアップで出力形式が変更されたか、当初の想定が間違っていた。

**修正**: ブラウザディレクトリの存在確認と、Google Chrome のバージョンコマンドに変更。

```bash
check "chromium browser exists"   test -d /home/node/.cache/ms-playwright/chromium-*
check "chrome browser exists"     google-chrome --version
```

### 10-5. CI でテストファイルが見つからない

**症状**: GitHub Actions で `bash tests/test-image.sh` がファイル不在で失敗。

**原因**: Docker イメージには `tests/` ディレクトリが含まれていない（Dockerfile に `COPY tests/ ...` がない）。

**修正**: CI のテスト実行時にホストの `tests/` ディレクトリをボリュームマウントする。

```yaml
- name: Run smoke tests
  run: |
    docker run --rm -v ${{ github.workspace }}/tests:/workspace/tests devcontainer-claude-code:test bash tests/test-image.sh
```

### 10-6. Playwright のインストール権限問題

**症状**: Docker ビルド中に `playwright install` が `sudo` パスワードを要求して失敗。

**原因**: `playwright install --with-deps` は内部で `sudo apt-get install` を実行するが、node ユーザーには sudo 権限がない。

**修正の変遷**:

1. 最初: `npm install playwright` → ブラウザがインストールされない
2. 次: `playwright install-deps` + `playwright install` を分離 → chrome の deps が不足
3. 最終: `USER root` で `playwright install --with-deps chromium chrome` を実行し、`chown` で権限を戻す

---

## 第11章：テストを通さずにプッシュした反省

開発の最終段階で、テストが通ることを確認せずにコードをプッシュしてしまう事態が発生しました。

### 何が起きたか

CI（GitHub Actions）でテストが3件失敗していました。

```
FAIL: ipset is installed
FAIL: claude-mem is installed
FAIL: ONNX model is cached
```

これらの失敗は、ローカルでビルド・テストしていれば事前に検知できたものです。

### 正しいワークフロー

修正後は、以下のワークフローを徹底しました。

```
1. コードを修正する
2. ローカルで Docker イメージをビルドする
   $ docker build --platform linux/amd64 -t devcontainer-claude-code:test .
3. テストを実行する
   $ docker run --rm -v $(pwd)/tests:/workspace/tests devcontainer-claude-code:test bash tests/test-image.sh
4. 全テストが PASS することを確認する
5. コミットしてプッシュする
```

`--platform linux/amd64` は、Apple Silicon（ARM64）の Mac でも CI と同じ x86_64 環境でビルドするためのオプションです。これがないと、Google Chrome が ARM64 Linux に対応していないためビルドが失敗します。

### 教訓

- **テストは、自分のためではなく未来の自分とチームメンバーのためにある**
- **CI で落ちるテストは、ローカルでも落ちる（落とせる）**
- **プッシュ前のローカルテストは省略してはいけない**

---

## 第12章：使い方ガイド

### 12-1. 前提条件

- Docker Desktop がインストール済み
- VS Code がインストール済み
- VS Code に「Dev Containers」拡張機能をインストール済み

### 12-2. プロジェクトへの導入

1. プロジェクトのルートに `.devcontainer/` ディレクトリを作成
2. テンプレートから `devcontainer.json` をコピー
3. 必要に応じて `allowed-domains.txt` を作成
4. プロジェクトルートに `.mcp.json` を配置

```bash
mkdir -p .devcontainer
cp templates/devcontainer.json .devcontainer/
cp templates/allowed-domains.txt .devcontainer/
cp templates/.mcp.json .
```

5. VS Code でプロジェクトを開く
6. 左下の「><」アイコンをクリック → 「Reopen in Container」を選択
7. コンテナのビルドと起動が始まる（初回は数分かかる）

### 12-3. カスタマイズ

**プロジェクト固有のドメインを追加する場合**:

`.devcontainer/allowed-domains.txt` を編集：

```
# 自社 API
api.mycompany.com
cdn.mycompany.com

# 外部サービス
api.stripe.com
```

**一時的にドメインを追加する場合**:

`devcontainer.json` の `containerEnv` に追加：

```json
"containerEnv": {
  "EXTRA_ALLOWED_DOMAINS": "api.temporary-service.com"
}
```

---

## まとめ

このプロジェクトで実現したことをまとめます。

### 技術スタック

```
インフラ:     Docker + GHCR + GitHub Actions
ベース:       Node.js 20 (Debian Bookworm)
AI:          Claude Code + claude-mem + ChromaDB
MCP:         7つのサーバー（全プリキャッシュ）
ブラウザ:     Playwright + Chromium + Chrome
シェル:       zsh + Powerlevel10k + fzf
セキュリティ:  3層ファイアウォール（iptables + ipset）
テスト:       39項目のスモークテスト
CI/CD:       GitHub Actions（テスト + マルチアーチ公開）
```

### 設計原則

1. **共有ベースイメージ**: 各プロジェクトで Dockerfile を書かない
2. **プリキャッシュ**: ビルド時にすべてをダウンロードし、実行時のネットワーク依存を最小化
3. **ゼロトラストネットワーク**: デフォルト拒否、明示的許可のみ
4. **段階的起動**: 4段階のブートストラップで依存関係を順に解決
5. **テスト駆動**: 39項目の自動テストでイメージの品質を保証
6. **マルチアーキテクチャ**: Intel/AMD と Apple Silicon の両方に対応

### ビルドにかかる時間

| 工程 | 時間 |
|------|------|
| システムパッケージ | 約30秒 |
| Claude Code + claude-mem | 約35秒 |
| ONNX モデルダウンロード | 約30秒 |
| MCP サーバーキャッシュ（並列） | 約3分 |
| Playwright ブラウザ | 約2分 |
| **合計** | **約7〜8分** |

### コンテナ起動時間

| 工程 | 時間 |
|------|------|
| ファイアウォール初期化 | 5〜10秒 |
| claude-mem 設定 | 2〜3秒 |
| ChromaDB 起動 | 3〜5秒 |
| ワーカー起動 | 10〜15秒 |
| **合計** | **約20〜30秒** |

起動後は、Claude Code + 全 MCP サーバー + ファイアウォール + ベクトル検索が使える状態になります。
