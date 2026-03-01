# VS Code Dev Containers 完全ガイド

## Dev Containers とは

VS Code の **Dev Containers** 拡張機能は、Docker コンテナを完全な開発環境に変える。プロジェクトのフォルダをコンテナ内にマウントし、IntelliSense・デバッグ・コードナビゲーションなど VS Code のフル機能をコンテナ内で利用できる。

「自分のマシンでは動くのに」問題を根本的に解決する仕組みであり、チームメンバー全員が同一の開発環境を即座に再現できる。

## アーキテクチャ

```
┌──────────────────────────────────┐
│ ローカルマシン                     │
│                                    │
│  ┌────────────┐                   │
│  │ VS Code    │                   │
│  │ (UI/テーマ)│                   │
│  └──────┬─────┘                   │
│         │                          │
│  ┌──────▼──────────────────────┐  │
│  │ Docker コンテナ               │  │
│  │                              │  │
│  │  VS Code Server              │  │
│  │  拡張機能（言語サーバー等）    │  │
│  │  開発ツール（git, node 等）   │  │
│  │  ターミナル                   │  │
│  │  デバッガ                     │  │
│  │                              │  │
│  │  /workspace ← マウント       │  │
│  └──────────────────────────────┘  │
└──────────────────────────────────┘
```

核心は **拡張機能の実行場所の分離** にある:

| 種別 | 実行場所 | 例 |
|---|---|---|
| UI 拡張 | ローカル | テーマ、スニペット、キーバインド |
| 開発拡張 | コンテナ内 | 言語サーバー、リンター、デバッガ |

拡張機能がコンテナ内で動作するため、ツールチェーン・ランタイム・依存関係がすべてコンテナに閉じる。ローカルマシンに Node.js や Python をインストールする必要がない。

## システム要件

### Docker

| OS | 要件 |
|---|---|
| **Windows** | Docker Desktop 2.0 以上。Windows 10 Pro/Enterprise、または Home 2004+ (WSL 2 バックエンド) |
| **macOS** | Docker Desktop 2.0 以上 |
| **Linux** | Docker CE/EE 18.06 以上 + Docker Compose 1.21 以上 |

リモートホストの場合は最低 1GB RAM、推奨 2GB RAM + 2コア CPU。

### コンテナ内 OS

| アーキテクチャ | サポート対象 |
|---|---|
| x86_64 / ARMv7l / ARMv8l | Debian 9+、Ubuntu 16.04+、CentOS/RHEL 7+ |
| x86_64 | Alpine Linux 3.9+ |

## セットアップ

### 1. Docker のインストール

各 OS の公式チャネルからインストールする。

**Windows 追加設定:**
- Docker Desktop のタスクバーアイコンを右クリック → Settings → WSL 2 バックエンドを有効化
- Settings → Resources → File Sharing で、ソースコードがあるドライブを共有

**macOS 追加設定:**
- Preferences → Resources → File Sharing でプロジェクトのあるディレクトリを共有

**Linux 追加設定:**
```bash
sudo usermod -aG docker $USER
# ログアウトして再ログイン
```

### 2. VS Code 拡張機能のインストール

```
VS Code を開く → 拡張機能（Ctrl+Shift+X / Cmd+Shift+X）
→「Dev Containers」を検索 → インストール
```

拡張機能 ID: `ms-vscode-remote.remote-containers`

## クイックスタート

Dev Containers を使い始める方法は3つある。

### 方法1: 既存プロジェクトをコンテナ化

最も一般的なパターン。

1. コマンドパレット（`Cmd+Shift+P` / `Ctrl+Shift+P`）を開く
2. **「Dev Containers: Open Folder in Container...」** を選択
3. プロジェクトフォルダを選択
4. ベーステンプレートを選ぶ（Node.js、Python 等）
5. 必要に応じて Features（追加ツール）を選択
6. VS Code がコンテナをビルドし、自動的に接続

ビルドの進捗は通知エリアに表示される。初回はイメージのダウンロードがあるため時間がかかるが、2回目以降はキャッシュが効く。

### 方法2: リポジトリを分離ボリュームにクローン

Windows / macOS ではバインドマウントよりもパフォーマンスが良い方法。

1. **「Dev Containers: Clone Repository in Container Volume...」** を選択
2. リポジトリ URL、GitHub リポジトリ名、またはブランチ/PR を入力
3. `.devcontainer/devcontainer.json` がなければテンプレートを選択
4. Docker ボリューム内にソースコードがクローンされ、コンテナが起動

ホストのファイルシステムを経由しないため、I/O パフォーマンスが大幅に向上する。

### 方法3: サンプルリポジトリで試す

1. **「Dev Containers: Try a Dev Container Sample...」** を選択
2. 言語別のサンプルリポジトリが一覧表示される
3. 選択するとクローン → コンテナ起動 → 開発環境が即座に利用可能

## ベーステンプレートとは

上記のクイックスタートで「ベーステンプレートを選ぶ」と書いたが、これは **Dev Container Templates** のことを指す。

### 概要

Dev Container Templates は、特定の言語やフレームワーク向けに事前構成された `.devcontainer/` ディレクトリ一式のパッケージだ。テンプレートを選択すると、そのスタックに適した `devcontainer.json`、必要に応じて `Dockerfile` や `docker-compose.yml` がプロジェクトに自動生成される。

つまり **テンプレート = 「開発環境の雛形」** であり、ゼロから `devcontainer.json` を書く代わりに、選択するだけで動作する設定一式が手に入る。

### テンプレート選択の流れ

```
コマンドパレット
  → "Dev Containers: Add Dev Container Configuration Files..."
  → テンプレート一覧が表示される（プロジェクトの内容に基づきソート）
  → テンプレートを選択
  → バージョン等のオプションを選択（例: Node 20 or 18）
  → 追加したい Features を選択（任意）
  → .devcontainer/ ディレクトリが自動生成される
```

VS Code はプロジェクト内のファイル（`package.json`、`requirements.txt` 等）を検出し、関連性の高いテンプレートを上位に表示する。

### テンプレートの種類

公式リポジトリ（[devcontainers/templates](https://github.com/devcontainers/templates)）とコミュニティから **130 以上** のテンプレートが提供されている。

#### 単一コンテナ型（イメージベース）

言語ランタイムだけのシンプルな構成。`devcontainer.json` のみが生成される。

| テンプレート | 生成されるファイル | ベースイメージ |
|---|---|---|
| Node.js | `devcontainer.json` | `mcr.microsoft.com/devcontainers/javascript-node` |
| Python 3 | `devcontainer.json` | `mcr.microsoft.com/devcontainers/python` |
| Go | `devcontainer.json` | `mcr.microsoft.com/devcontainers/go` |
| Rust | `devcontainer.json` | `mcr.microsoft.com/devcontainers/rust` |
| Java | `devcontainer.json` | `mcr.microsoft.com/devcontainers/java` |
| C++ | `devcontainer.json` | `mcr.microsoft.com/devcontainers/cpp` |
| Ruby | `devcontainer.json` | `mcr.microsoft.com/devcontainers/ruby` |
| PHP | `devcontainer.json` | `mcr.microsoft.com/devcontainers/php` |
| .NET | `devcontainer.json` | `mcr.microsoft.com/devcontainers/dotnet` |
| Ubuntu / Debian / Alpine | `devcontainer.json` | 汎用 OS イメージ |

例えば **Node.js** テンプレートを選ぶと、以下の `devcontainer.json` が生成される:

```jsonc
{
  "name": "Node.js",
  "image": "mcr.microsoft.com/devcontainers/javascript-node:4-22"

  // "features": {},
  // "forwardPorts": [],
  // "postCreateCommand": "yarn install",
  // "customizations": {},
  // "remoteUser": "root"
}
```

コメントアウトされた行はよく使うオプションのヒントで、必要に応じてアンコメントして使う。

#### マルチコンテナ型（Docker Compose ベース）

言語 + DB のように複数サービスが必要な構成。`devcontainer.json` に加えて `Dockerfile` と `docker-compose.yml` が生成される。

| テンプレート | 含まれるサービス |
|---|---|
| Node.js & PostgreSQL | Node.js アプリ + PostgreSQL |
| Node.js & MongoDB | Node.js アプリ + MongoDB |
| Python & PostgreSQL | Python アプリ + PostgreSQL |
| Java & PostgreSQL | Java アプリ + PostgreSQL |
| Ruby on Rails & PostgreSQL | Rails アプリ + PostgreSQL |
| .NET & PostgreSQL | .NET アプリ + PostgreSQL |
| C++ & MariaDB | C++ アプリ + MariaDB |

例えば **Node.js & PostgreSQL** テンプレートを選ぶと、3ファイルが生成される:

**devcontainer.json** — Docker Compose を参照:
```jsonc
{
  "name": "Node.js & PostgreSQL",
  "dockerComposeFile": "docker-compose.yml",
  "service": "app",
  "workspaceFolder": "/workspaces/${localWorkspaceFolderBasename}"
}
```

**docker-compose.yml** — アプリと DB の2サービス:
```yaml
volumes:
  postgres-data:

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    volumes:
      - ../..:/workspaces:cached
    command: sleep infinity
    depends_on:
      db:
        condition: service_healthy
    networks:
      - app-network

  db:
    image: postgres:latest
    restart: unless-stopped
    networks:
      - app-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
    volumes:
      - postgres-data:/var/lib/postgresql
    environment:
      POSTGRES_PASSWORD: postgres
      POSTGRES_USER: postgres
      POSTGRES_DB: postgres

networks:
  app-network:
    driver: bridge
```

**Dockerfile** — カスタマイズ用の薄い Dockerfile:
```dockerfile
FROM mcr.microsoft.com/devcontainers/javascript-node:4-22

# [Optional] 追加のシステムパッケージ
# RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
#     && apt-get -y install --no-install-recommends <packages>

# [Optional] グローバル npm モジュールの追加
# RUN su node -c "npm install -g <packages>"
```

#### 特殊用途テンプレート

| カテゴリ | テンプレート例 |
|---|---|
| インフラ | Kubernetes + Helm、Docker-in-Docker、Docker-outside-of-Docker |
| ドキュメント | Markdown、Jekyll |
| データサイエンス | Anaconda、Miniconda（+ PostgreSQL） |
| その他 | Universal（多言語対応の万能テンプレート）、PowerShell |

**Universal** テンプレートは Python / Node.js / Java / .NET / PHP / Ruby / Go / Rust / C++ を含む「全部入り」で、何を使うか決まっていない段階で便利だが、イメージサイズが大きい。

### テンプレートと Features の違い

| | テンプレート (Templates) | フィーチャー (Features) |
|---|---|---|
| **役割** | 開発環境全体の雛形 | 既存環境への追加ツール |
| **生成物** | `.devcontainer/` ディレクトリ一式 | `devcontainer.json` の `features` プロパティに追記 |
| **適用タイミング** | プロジェクト初期設定時 | テンプレート選択後、または既存環境に後から追加 |
| **粒度** | 大きい（OS + ランタイム + ツール一式） | 小さい（個別ツール単位） |
| **例** | 「Node.js & PostgreSQL」環境 | 「GitHub CLI を追加」「Docker CLI を追加」 |

テンプレートは「土台」、Features は「トッピング」と考えるとわかりやすい。実際の選択フローでも、まずテンプレートを選び、次に Features を追加する順序になっている。

```
テンプレート選択（土台を決める）
  "Node.js & PostgreSQL" を選択
     ↓
Features 選択（トッピングを追加）
  ✅ GitHub CLI
  ✅ AWS CLI
  ☐  Docker-in-Docker
     ↓
.devcontainer/ が生成される
```

### コミュニティテンプレート

公式テンプレート以外にもコミュニティが公開しているものがある:

- **[containers.dev/templates](https://containers.dev/templates)** — 公式・コミュニティのテンプレートを横断検索
- OCI レジストリ（GitHub Container Registry 等）経由で配布される
- 独自テンプレートを作成して公開することも可能

### テンプレートを使わない場合

テンプレートはあくまで「手早く始めるための雛形」であり、必須ではない。以下の場合はテンプレートなしで `devcontainer.json` を手書きする:

- 既存の Dockerfile / Docker Compose をそのまま利用したい
- テンプレートにない構成が必要
- 最小限の設定から始めたい

テンプレートで生成した後にファイルを自由にカスタマイズすることもできるため、まずテンプレートで雛形を作り、そこから調整するのが効率的だ。

## devcontainer.json の構成

`devcontainer.json` はコンテナの動作を定義する設定ファイル。配置場所は:

- `.devcontainer/devcontainer.json`（推奨）
- `.devcontainer.json`（プロジェクトルート直下）

### 最小構成

```jsonc
{
  // 既成のイメージを使用
  "image": "mcr.microsoft.com/devcontainers/typescript-node:0-18"
}
```

これだけで Node.js + TypeScript 環境のコンテナが起動する。

### 実用的な構成例

```jsonc
{
  "name": "My Project Dev",

  // ベースイメージ
  "image": "mcr.microsoft.com/devcontainers/typescript-node",

  // ポートフォワーディング
  "forwardPorts": [3000, 5432],

  // VS Code 拡張機能
  "customizations": {
    "vscode": {
      "extensions": [
        "dbaeumer.vscode-eslint",
        "esbenp.prettier-vscode",
        "streetsidesoftware.code-spell-checker"
      ],
      "settings": {
        "editor.formatOnSave": true,
        "editor.defaultFormatter": "esbenp.prettier-vscode"
      }
    }
  },

  // コンテナ作成後にパッケージをインストール
  "postCreateCommand": "npm install",

  // コンテナ起動時に毎回実行
  "postStartCommand": "npm run dev",

  // 環境変数
  "containerEnv": {
    "NODE_ENV": "development"
  },

  // 非 root ユーザーで操作
  "remoteUser": "node"
}
```

## 3つの構成パターン

Dev Containers は3つのベース構成をサポートする。

### パターン1: イメージベース

既成の Dev Container イメージを指定するだけで開発環境が立ち上がる、最もシンプルなパターン。

```jsonc
{
  "image": "mcr.microsoft.com/devcontainers/python:3.11",
  "postCreateCommand": "pip install -r requirements.txt"
}
```

しかしこの1行の `image` には一体何が入っているのか。

#### Dev Container イメージの中身

Microsoft が提供する Dev Container イメージ（`mcr.microsoft.com/devcontainers/...`）には、開発に必要な一式があらかじめ組み込まれている:

```
mcr.microsoft.com/devcontainers/python:3.11 の中身
┌──────────────────────────────────────────┐
│ Debian / Ubuntu（OS）                     │
│                                           │
│ 言語ランタイム                             │
│   Python 3.11 + pip                       │
│                                           │
│ 基本ツール                                 │
│   git, curl, wget, ca-certificates, ...   │
│                                           │
│ シェル                                     │
│   zsh + Oh My Zsh（デフォルトシェル）       │
│   bash                                    │
│                                           │
│ ユーザー設定                               │
│   vscode ユーザー（UID 1000、sudo 可能）   │
│                                           │
│ その他                                     │
│   GPG, SSH クライアント, procps, ...       │
└──────────────────────────────────────────┘
```

つまり「OS + 言語 + 開発者がいつも使うツール + 非 root ユーザー」がパッケージされている。コンテナを起動したらすぐに `git clone` も `pip install` もできる状態だ。

言語によって入っているものが変わる:

| イメージ | 言語・ツール | 追加で入っているもの |
|---|---|---|
| `devcontainers/python` | Python + pip | pylint 等のリンター基盤 |
| `devcontainers/javascript-node` | Node.js + npm + yarn | nvm（バージョン切り替え） |
| `devcontainers/java` | JDK + Maven + Gradle | SDKMAN!（バージョン管理） |
| `devcontainers/go` | Go コンパイラ | GOPATH 設定済み |
| `devcontainers/rust` | Rust + Cargo | rustup（ツールチェーン管理） |
| `devcontainers/typescript-node` | Node.js + npm + TypeScript | tsc がグローバルインストール済み |
| `devcontainers/base:ubuntu` | なし（OS のみ） | 言語は Features で後から追加する用 |

#### イメージの命名規則

```
mcr.microsoft.com/devcontainers/<言語>:<イメージバージョン>-<言語バージョン>
```

例:
| イメージ指定 | 意味 |
|---|---|
| `devcontainers/python:3.11` | Python 3.11 |
| `devcontainers/python:1-3.11` | イメージ v1 系 + Python 3.11（より厳密） |
| `devcontainers/javascript-node:22` | Node.js 22 |
| `devcontainers/java:21` | JDK 21 |
| `devcontainers/java:17-bookworm` | JDK 17 + Debian Bookworm ベース |

バージョンのタグを省略すると最新が使われるが、チームで使う場合はバージョンを明示するのが安全だ。

#### 実用的な設定例

イメージベースでも `devcontainer.json` のプロパティを組み合わせれば、十分に実用的な環境を作れる。

**Python Web 開発の例:**

```jsonc
{
  "name": "Python API Server",

  // ベースイメージ — Python 3.11 + 開発ツール一式
  "image": "mcr.microsoft.com/devcontainers/python:3.11",

  // 追加ツールを Features で入れる（Dockerfile 不要）
  "features": {
    "ghcr.io/devcontainers/features/docker-in-docker:2": {},
    "ghcr.io/devcontainers/features/github-cli:1": {}
  },

  // コンテナ作成後に依存パッケージをインストール
  "postCreateCommand": "pip install -r requirements.txt",

  // コンテナ起動時に毎回開発サーバーを立ち上げる
  "postStartCommand": "python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000",

  // ポート転送
  "forwardPorts": [8000],
  "portsAttributes": {
    "8000": {
      "label": "API Server",
      "onAutoForward": "notify"
    }
  },

  // VS Code の拡張機能と設定
  "customizations": {
    "vscode": {
      "extensions": [
        "ms-python.python",
        "ms-python.vscode-pylance",
        "charliermarsh.ruff"
      ],
      "settings": {
        "python.defaultInterpreterPath": "/usr/local/bin/python",
        "editor.formatOnSave": true,
        "[python]": {
          "editor.defaultFormatter": "charliermarsh.ruff"
        }
      }
    }
  },

  // 環境変数
  "containerEnv": {
    "PYTHONDONTWRITEBYTECODE": "1",
    "DATABASE_URL": "sqlite:///./dev.db"
  },

  // 非 root ユーザーで操作
  "remoteUser": "vscode"
}
```

**Node.js + TypeScript の例:**

```jsonc
{
  "name": "TypeScript App",
  "image": "mcr.microsoft.com/devcontainers/typescript-node:22",

  "postCreateCommand": "npm install",
  "postStartCommand": "npm run dev",

  "forwardPorts": [3000],

  "customizations": {
    "vscode": {
      "extensions": [
        "dbaeumer.vscode-eslint",
        "esbenp.prettier-vscode"
      ],
      "settings": {
        "editor.formatOnSave": true,
        "editor.defaultFormatter": "esbenp.prettier-vscode",
        "editor.codeActionsOnSave": {
          "source.fixAll.eslint": "explicit"
        }
      }
    }
  },

  "remoteUser": "node"
}
```

#### イメージベースの限界

イメージベースは手軽だが、以下の場合は Dockerfile ベースに移行する:

| やりたいこと | イメージベースで可能か |
|---|---|
| Features で追加ツールを入れる | 可能 |
| `postCreateCommand` でパッケージをインストール | 可能（ただし毎回実行される） |
| OS レベルのパッケージ（`apt-get`）を追加 | **不可** → Dockerfile が必要 |
| 複雑なビルド手順や設定ファイルの配置 | **不可** → Dockerfile が必要 |
| ベースイメージ自体を変えたい | **不可** → Dockerfile が必要 |

`postCreateCommand` でも `apt-get install` は書けるが、コンテナを作り直すたびに実行されるため遅い。頻繁に使うパッケージは Dockerfile に書いて Docker のレイヤーキャッシュを活かすほうが効率的だ。

**向いている場面:** 標準的な言語環境 + Features の追加で十分な場合。多くのプロジェクトはここから始めて、必要になったら Dockerfile に移行する。

### パターン2: Dockerfile ベース

カスタムイメージをビルドする場合。

```jsonc
{
  "build": {
    "dockerfile": "Dockerfile",
    "context": "..",
    "args": {
      "VARIANT": "3.11"
    }
  },
  "customizations": {
    "vscode": {
      "extensions": ["ms-python.python"]
    }
  }
}
```

```dockerfile
# .devcontainer/Dockerfile
FROM mcr.microsoft.com/devcontainers/python:${VARIANT}

# システムパッケージの追加
RUN apt-get update && export DEBIAN_FRONTEND=noninteractive \
    && apt-get -y install --no-install-recommends git curl

# Python パッケージ
COPY requirements.txt /tmp/
RUN pip install -r /tmp/requirements.txt
```

**向いている場面:** 追加のシステムパッケージやカスタマイズが必要な場合

**利点:** Docker のレイヤーキャッシュが効くため、`postCreateCommand` でインストールするより再ビルドが速い。

### パターン3: Docker Compose ベース

DB やキャッシュなど複数サービスが必要な場合。

```jsonc
// .devcontainer/devcontainer.json
{
  "name": "Full Stack App",
  "dockerComposeFile": ["../docker-compose.yml", "docker-compose.extend.yml"],
  "service": "app",
  "workspaceFolder": "/workspace",
  "shutdownAction": "stopCompose"
}
```

```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    build: .
    volumes:
      - .:/workspace:cached
    ports:
      - "3000:3000"

  db:
    image: postgres:16
    environment:
      POSTGRES_PASSWORD: dev
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

```yaml
# .devcontainer/docker-compose.extend.yml
# 開発環境専用のオーバーレイ
version: '3.8'
services:
  app:
    volumes:
      - .:/workspace:cached
    # デバッガに必要な権限
    cap_add:
      - SYS_PTRACE
    security_opt:
      - seccomp:unconfined
    # コンテナを維持するためのコマンド
    command: /bin/sh -c "while sleep 1000; do :; done"
```

`dockerComposeFile` は配列で複数ファイルを指定でき、後のファイルが前のファイルをオーバーライドする。これにより本番用の `docker-compose.yml` を変更せずに開発設定を追加できる。

**向いている場面:** マイクロサービスや DB 付きアプリの開発

## Dev Container Features

Features は再利用可能なインストールスクリプトのパッケージ。Dockerfile を書かなくてもツールを追加できる。

```jsonc
{
  "image": "mcr.microsoft.com/devcontainers/base:ubuntu",
  "features": {
    "ghcr.io/devcontainers/features/node:1": {
      "version": "20"
    },
    "ghcr.io/devcontainers/features/github-cli:1": {
      "version": "latest"
    },
    "ghcr.io/devcontainers/features/docker-in-docker:2": {}
  }
}
```

### デフォルト Features

すべてのコンテナに自動インストールする Features を設定できる:

```jsonc
// VS Code ユーザー設定
{
  "dev.containers.defaultFeatures": {
    "ghcr.io/devcontainers/features/github-cli:1": {}
  }
}
```

### Features の探し方

- [containers.dev/features](https://containers.dev/features) で公開 Features を検索
- コンテナ作成時の UI でも検索・選択可能

## 複数言語の共存とバージョン管理

> 「Node.js や Java をインストールするとは何をしているのか」「ビルドツールとランタイムの違い」「JDK と JRE の関係」「開発コンテナと本番コンテナの使い分け」については、別記事 **[言語ランタイムとビルドの仕組み](./language-runtime-build-guide.md)** で詳しく解説している。

### フロントエンド + バックエンドで言語が違う場合

実際のプロジェクトでは「フロントエンドは Node.js、バックエンドは Java」のような構成が珍しくない。Dev Containers ではいくつかのアプローチがある。

#### アプローチ1: Features で言語を追加（モノレポ向き）

1つのコンテナに複数の言語ランタイムを入れる方法。もっともシンプル。

```jsonc
{
  "name": "Full Stack (Node + Java)",
  // Java をベースにする
  "image": "mcr.microsoft.com/devcontainers/java:17",
  "features": {
    // Node.js を Features で追加
    "ghcr.io/devcontainers/features/node:1": {
      "version": "20"
    }
  },
  "forwardPorts": [3000, 8080],
  "postCreateCommand": "cd frontend && npm install && cd ../backend && mvn install"
}
```

**向いている場面:** モノレポでフロントとバックを同時に編集する場合。1つのターミナルで両方のプロセスを起動できる。

#### アプローチ2: Docker Compose でサービスを分離（マイクロサービス向き）

フロントとバックを別コンテナにする方法。

```jsonc
// .devcontainer/devcontainer.json
{
  "name": "Microservices",
  "dockerComposeFile": "docker-compose.yml",
  "service": "backend",   // VS Code が接続するサービスを指定
  "workspaceFolder": "/workspaces/${localWorkspaceFolderBasename}"
}
```

```yaml
# .devcontainer/docker-compose.yml
services:
  backend:
    image: mcr.microsoft.com/devcontainers/java:17
    volumes:
      - ../..:/workspaces:cached
    command: sleep infinity

  frontend:
    image: mcr.microsoft.com/devcontainers/javascript-node:22
    volumes:
      - ../..:/workspaces:cached
    ports:
      - "3000:3000"
    command: sleep infinity

  db:
    image: postgres:16
    environment:
      POSTGRES_PASSWORD: dev
```

VS Code は `service` で指定した1つのコンテナに接続する。他のコンテナはバックグラウンドで動き、ネットワーク越しに通信する。フロントのコンテナでコマンドを実行したい場合は `docker exec` を使う。

**向いている場面:** 各サービスの依存関係を完全に分離したい場合。本番と同じコンテナ構成で開発できる。

#### アプローチ3: Universal テンプレート（何でも入り）

何を使うか決まっていない段階や、多くの言語を頻繁に切り替える場合:

```jsonc
{
  "image": "mcr.microsoft.com/devcontainers/universal:2",
  "forwardPorts": [3000, 8080]
}
```

Python / Node.js / Java / .NET / PHP / Ruby / Go / Rust / C++ が全部入っている。ただしイメージサイズが大きい（数 GB）ため、ディスクとダウンロード時間に注意。

#### どれを選ぶか

| 観点 | Features 追加 | Docker Compose | Universal |
|---|---|---|---|
| セットアップの手軽さ | 簡単 | やや複雑 | 最も簡単 |
| イメージサイズ | 小さい | サービスごとに最小 | 非常に大きい |
| 環境の分離度 | 低い（同居） | 高い（別コンテナ） | 低い（同居） |
| 本番との近さ | 低い | 高い | 低い |
| VS Code 体験 | 快適 | メインサービスのみ快適 | 快適 |

多くの場合は **Features で追加** が最もバランスが良い。「フロントとバックの依存関係が干渉する」「本番と同じ構成で開発したい」場合に Docker Compose を使う。

### ほしいバージョンがない場合

テンプレートや Features の選択肢にほしいバージョンが表示されないことがある。例えば「Node.js 21 を使いたいが選択肢に出ない」「Python 3.13 がまだ対応されていない」といったケース。

#### 方法1: Features のバージョン指定

Features はテンプレートの選択 UI に出ないバージョンでも、直接指定すれば使えることが多い:

```jsonc
{
  "image": "mcr.microsoft.com/devcontainers/base:ubuntu",
  "features": {
    "ghcr.io/devcontainers/features/node:1": {
      "version": "21"   // UI に出なくてもバージョン文字列を直接指定
    },
    "ghcr.io/devcontainers/features/java:1": {
      "version": "21",
      "installGradle": "true",
      "installMaven": "true"
    }
  }
}
```

各 Feature がどのバージョンに対応しているかは、Feature のリポジトリ（`devcontainer-feature.json`）で確認できる。

#### 方法2: Dockerfile で自由にインストール

最も柔軟な方法。どんなバージョンでもインストールできる:

```dockerfile
# .devcontainer/Dockerfile
FROM mcr.microsoft.com/devcontainers/base:ubuntu

# Node.js — 任意のバージョンを nvm でインストール
RUN su vscode -c "source /usr/local/share/nvm/nvm.sh && nvm install 21.7.0"

# Java — SDKMAN! で任意のディストリビューション・バージョンを指定
RUN su vscode -c "curl -s https://get.sdkman.io | bash && \
    source ~/.sdkman/bin/sdkman-init.sh && \
    sdk install java 21.0.2-tem"
```

```jsonc
{
  "build": {
    "dockerfile": "Dockerfile"
  }
}
```

**向いている場面:** リリース直後の最新バージョンを試したい場合、特定のマイナーバージョンに固定したい場合、公式テンプレートや Features に対応がない言語・ツールを使いたい場合。

#### 方法3: ベースイメージを直接指定

言語の公式 Docker イメージを直接使う方法:

```jsonc
{
  "image": "node:21-bookworm",
  "features": {
    // Dev Containers の便利機能を Features で追加
    "ghcr.io/devcontainers/features/common-utils:2": {
      "installZsh": true,
      "configureZshAsDefaultShell": true,
      "installOhMyZsh": true,
      "username": "node"
    },
    "ghcr.io/devcontainers/features/git:1": {}
  },
  "remoteUser": "node"
}
```

言語の公式イメージは Microsoft の Dev Container イメージより早く最新バージョンに対応する。ただし、Dev Container 用の便利機能（非 root ユーザー、zsh、git 等）が入っていないため、`common-utils` Feature で補う必要がある。

#### バージョン対応状況の確認方法

| 確認先 | URL | 内容 |
|---|---|---|
| MCR イメージタグ | `mcr.microsoft.com/v2/devcontainers/<言語>/tags/list` | テンプレートの対応バージョン |
| Feature リポジトリ | [github.com/devcontainers/features](https://github.com/devcontainers/features) | 各 Feature の `devcontainer-feature.json` にバージョン一覧 |
| 言語公式 Docker Hub | 各言語のページ | 最新タグの確認 |

## ライフサイクルスクリプト

コンテナの各段階で自動実行されるコマンド群。実行順序が決まっている:

```
initializeCommand     ← ホストマシンで実行（コンテナ起動前）
     ↓
onCreateCommand       ← コンテナ作成直後（初回のみ）
     ↓
updateContentCommand  ← プリビルドの更新時
     ↓
postCreateCommand     ← ユーザー割り当て後の最終セットアップ（初回のみ）
     ↓
postStartCommand      ← コンテナ起動のたびに実行
     ↓
postAttachCommand     ← VS Code がコンテナに接続するたびに実行
```

| コマンド | 実行場所 | 実行タイミング | 典型的な用途 |
|---|---|---|---|
| `initializeCommand` | ホスト | コンテナ起動前 | ホスト側の前提条件チェック |
| `onCreateCommand` | コンテナ | 初回作成時 | 基本的な依存関係のインストール |
| `updateContentCommand` | コンテナ | プリビルド更新時 | キャッシュの更新 |
| `postCreateCommand` | コンテナ | 初回作成時 | `npm install`, `pip install` 等 |
| `postStartCommand` | コンテナ | 毎回起動時 | 開発サーバーの起動、ファイアウォール設定 |
| `postAttachCommand` | コンテナ | VS Code 接続時 | UI に関連する設定 |

### 記法

```jsonc
// 文字列: /bin/sh で実行
"postCreateCommand": "npm install"

// 配列: シェルを経由せずに直接実行
"postCreateCommand": ["npm", "install"]

// オブジェクト: 複数コマンドを並列実行
"postCreateCommand": {
  "install": "npm install",
  "db": "npm run db:migrate"
}
```

### waitFor

どのコマンドの完了まで待ってから VS Code を接続するかを制御する:

```jsonc
{
  "postStartCommand": "sudo /usr/local/bin/init-firewall.sh",
  "waitFor": "postStartCommand"
}
```

デフォルトは `updateContentCommand`。ファイアウォールの初期化など、接続前に完了すべき処理がある場合に `postStartCommand` を指定する。

## ポートフォワーディング

### 永続的なポート転送

```jsonc
{
  "forwardPorts": [3000, 5432],
  "portsAttributes": {
    "3000": {
      "label": "Web App",
      "onAutoForward": "openBrowser"
    },
    "5432": {
      "label": "PostgreSQL",
      "onAutoForward": "silent"
    }
  }
}
```

### ポート属性

| プロパティ | 説明 |
|---|---|
| `label` | ポートの表示名 |
| `protocol` | `http` または `https` |
| `onAutoForward` | 自動転送時の動作: `notify`, `openBrowser`, `openBrowserOnce`, `openPreview`, `silent`, `ignore` |
| `requireLocalPort` | ローカルで同じポート番号を必須にする |
| `elevateIfNeeded` | 低番号ポートの場合に権限昇格を自動実行 |

### 一時的なポート転送

コマンドパレットから **「Forward a Port」** を選択すると、セッション限定でポートを転送できる。`devcontainer.json` の変更は不要。

## 拡張機能の管理

### 自動インストール

`devcontainer.json` に記載した拡張機能はコンテナ起動時に自動インストールされる:

```jsonc
"customizations": {
  "vscode": {
    "extensions": [
      "dbaeumer.vscode-eslint",
      "esbenp.prettier-vscode",
      "eamodio.gitlens"
    ]
  }
}
```

### 拡張機能の追加方法

- 拡張機能ビューで右クリック → **「Add to devcontainer.json」**
- **Local - Installed** カテゴリのクラウドボタンで、ローカルの全拡張機能を一括インストール

### デフォルト拡張機能

すべてのコンテナに自動インストールする拡張機能:

```jsonc
// VS Code ユーザー設定
{
  "dev.containers.defaultExtensions": [
    "eamodio.gitlens",
    "mutantdino.resourcemonitor"
  ]
}
```

### 拡張機能の除外

ベースイメージに含まれる不要な拡張機能を除外する:

```jsonc
"extensions": ["-dbaeumer.vscode-eslint"]
```

ID の先頭に `-` をつける。

## 環境変数

### コンテナ環境変数（`containerEnv`）

コンテナ全体に適用される環境変数:

```jsonc
{
  "containerEnv": {
    "NODE_ENV": "development",
    "DATABASE_URL": "postgres://dev:dev@db:5432/app"
  }
}
```

### リモート環境変数（`remoteEnv`）

VS Code のプロセスとそのサブプロセスにのみ適用される:

```jsonc
{
  "remoteEnv": {
    "PATH": "${containerEnv:PATH}:/custom/bin",
    "API_KEY": "${localEnv:API_KEY}"
  }
}
```

### 変数の参照構文

| 構文 | 説明 |
|---|---|
| `${localEnv:VAR}` | ホストマシンの環境変数 |
| `${localEnv:VAR:default}` | デフォルト値付き |
| `${containerEnv:VAR}` | コンテナの環境変数 |
| `${localWorkspaceFolder}` | ホスト上のワークスペースパス |
| `${containerWorkspaceFolder}` | コンテナ内のワークスペースパス |
| `${localWorkspaceFolderBasename}` | ホスト上のフォルダ名 |
| `${devcontainerId}` | コンテナの一意識別子 |

## マウント

### ボリュームマウント

```jsonc
{
  "mounts": [
    // 名前付きボリューム（永続化）
    "source=mydata,target=/data,type=volume",

    // バインドマウント（ホストのディレクトリ）
    "source=${localWorkspaceFolder}/config,target=/config,type=bind,consistency=cached",

    // 匿名ボリューム（一時的）
    "target=/tmp/scratch,type=volume"
  ]
}
```

### ワークスペースマウントのカスタマイズ

```jsonc
{
  "workspaceMount": "source=${localWorkspaceFolder},target=/workspace,type=bind,consistency=delegated",
  "workspaceFolder": "/workspace"
}
```

`consistency` オプション（macOS で有効）:

| 値 | 説明 |
|---|---|
| `consistent` | 完全同期（デフォルト、最も遅い） |
| `cached` | ホスト → コンテナの遅延を許容（読み取り重視） |
| `delegated` | コンテナ → ホストの遅延を許容（書き込み重視、最も速い） |

### シェル履歴の永続化

コンテナを再ビルドしてもシェル履歴を保持する:

```jsonc
{
  "mounts": [
    "source=shell-history,target=/commandhistory,type=volume"
  ]
}
```

## セキュリティ関連の設定

### 非 root ユーザー

```jsonc
{
  "remoteUser": "node",
  "containerUser": "node"
}
```

| プロパティ | 説明 |
|---|---|
| `remoteUser` | VS Code のツール操作時のユーザー |
| `containerUser` | コンテナ全体の実行ユーザー |
| `updateRemoteUserUID` | Linux でホストの UID/GID に合わせる（デフォルト `true`） |

### 権限の追加

```jsonc
{
  // Docker CLI 引数（イメージ/Dockerfile ベースの場合）
  "runArgs": [
    "--cap-add=NET_ADMIN",
    "--cap-add=NET_RAW"
  ],

  // devcontainer.json プロパティ（全パターン共通）
  "capAdd": ["SYS_PTRACE"],
  "securityOpt": ["seccomp=unconfined"],

  // 特権モード（非推奨、必要な場合のみ）
  "privileged": true
}
```

### Workspace Trust

Dev Containers は VS Code の Workspace Trust 機能と連携する:

- フォルダを開く際にローカルソースの信頼を確認
- コンテナへのアタッチ時にコンテナの信頼を確認
- ボリュームクローン時にリポジトリの信頼を確認
- 信頼されていない場合は **Restricted Mode** で起動

## Dotfiles によるパーソナライゼーション

GitHub 上の dotfiles リポジトリを指定すると、コンテナ作成時に自動で適用される:

```jsonc
// VS Code ユーザー設定
{
  "dotfiles.repository": "your-github-id/dotfiles",
  "dotfiles.targetPath": "~/dotfiles",
  "dotfiles.installCommand": "install.sh"
}
```

コンテナが作成されるたびに dotfiles リポジトリがクローンされ、`installCommand` が実行される。`.bashrc`、`.zshrc`、`.gitconfig` などの個人設定を統一できる。

## プリビルドによる高速化

コンテナのビルドを事前に行い、イメージとしてレジストリに保存しておくことで起動を高速化できる:

```bash
# Dev Container CLI でイメージをビルド
devcontainer build --image-name my-team/dev-env:latest

# レジストリにプッシュ
docker push my-team/dev-env:latest
```

プリビルドイメージには Dev Container メタデータがラベルとして埋め込まれるため、`devcontainer.json` を簡素化できる:

```jsonc
{
  "image": "my-team/dev-env:latest"
  // 他の設定はイメージのメタデータから自動継承
}
```

CI/CD パイプラインで定期的にプリビルドすることで、チーム全員が常に最新の環境を高速に取得できる。

## コンテナのライフサイクル管理

### シャットダウン動作

```jsonc
{
  // VS Code を閉じた後もコンテナを維持
  "shutdownAction": "none"
}
```

| 値 | 動作 |
|---|---|
| `stopContainer` | コンテナを停止（デフォルト: イメージ/Dockerfile） |
| `stopCompose` | Compose サービスを停止（デフォルト: Docker Compose） |
| `none` | コンテナを維持（バックグラウンドで動作し続ける） |

### 再ビルド

`.devcontainer` のファイルを変更した場合:

- コマンドパレット → **「Dev Containers: Rebuild Container」**
- キャッシュを使わず完全に再ビルド: **「Dev Containers: Rebuild Container Without Cache」**

## ターミナルとデバッグ

### ターミナル

コンテナ接続後に開くターミナルはすべてコンテナ内で実行される。`code` コマンドも利用可能:

```bash
# コンテナ内のターミナルで
code file.txt          # VS Code でファイルを開く
code --diff a.txt b.txt  # diff ビューで開く
```

### デバッグ

`launch.json` の設定がそのままコンテナ内で動作する。C++、Go、Rust のデバッグには追加権限が必要:

```jsonc
{
  "capAdd": ["SYS_PTRACE"],
  "securityOpt": ["seccomp=unconfined"]
}
```

## Git と認証情報の共有

### Git 認証情報

ホストで設定済みの Git Credential Manager は自動的にコンテナと共有される。追加設定は不要。

### SSH キー

SSH エージェントが起動していれば、コンテナから自動的に利用できる:

```bash
# ホストで SSH エージェントが動作しているか確認
ssh-add -l
```

**注意:** SSH キーにパスフレーズが設定されている場合、VS Code の pull/sync 機能がハングする可能性がある。対策:

- パスフレーズなしのキーを使用
- HTTPS でクローン
- コマンドラインから `git push` を使用

### Git の改行コード

`.gitattributes` を設定して改行コードの変換問題を防ぐ:

```
* text=auto eol=lf
*.{cmd,[cC][mM][dD]} text eol=crlf
*.{bat,[bB][aA][tT]} text eol=crlf
```

## パフォーマンス最適化

### Windows / macOS でのパフォーマンス改善

バインドマウントは Windows / macOS でパフォーマンスが低下する。対策:

1. **ボリュームクローンを使用**（最も効果的）
   - 「Clone Repository in Container Volume...」でリポジトリを直接ボリュームにクローン
   - ホストのファイルシステムを経由しないため大幅に高速化

2. **`consistency` オプションの指定**
   ```jsonc
   "workspaceMount": "source=${localWorkspaceFolder},target=/workspace,type=bind,consistency=delegated"
   ```

3. **`node_modules` をボリュームに分離**
   ```jsonc
   "mounts": [
     "target=/workspace/node_modules,type=volume"
   ]
   ```

### WSL 2 バックエンド（Windows）

Docker Desktop の WSL 2 バックエンドを有効にすることで、ファイル共有のパフォーマンスが向上し、多くの問題が解消される。Windows 10 2004 以降で利用可能。

### ディスク容量の管理

```bash
# 不要なコンテナ・イメージを削除
docker system prune --all

# Docker Compose のリソースを削除
docker-compose down --volumes
```

Remote Explorer からも不要なコンテナの停止・削除が可能。

## チームでの共有

### リポジトリへのコミット

`.devcontainer/` ディレクトリをリポジトリにコミットすることで、チーム全員が同じ開発環境を利用できる:

```
your-project/
├── .devcontainer/
│   ├── devcontainer.json
│   ├── Dockerfile        # (オプション)
│   └── init-firewall.sh  # (オプション)
├── src/
└── ...
```

チームメンバーがリポジトリを開くと、VS Code が自動的にコンテナでの再オープンを提案する。

### バッジの追加

リポジトリの README に「Open in Dev Containers」バッジを追加して、ワンクリックで環境構築できるようにする。

## 既知の制限事項

| 制限 | 説明 |
|---|---|
| Windows コンテナ | Windows コンテナイメージは非対応 |
| マルチルートワークスペース | すべてのルートが同一コンテナで開かれる |
| Ubuntu Docker snap | snap 版 Docker は非互換 |
| Docker Toolbox | 非互換（Docker Desktop を使用すること） |
| SSH パスフレーズ | パスフレーズ付き SSH キーで pull/sync がハングする場合がある |
| ローカルプロキシ | ホストのプロキシ設定はコンテナに自動転送されない |

## devcontainer.json 全プロパティリファレンス

### 共通プロパティ

| プロパティ | 型 | 説明 |
|---|---|---|
| `name` | string | コンテナの表示名 |
| `forwardPorts` | array | 転送するポート |
| `portsAttributes` | object | ポートごとの設定 |
| `containerEnv` | object | コンテナ環境変数 |
| `remoteEnv` | object | ツール用環境変数 |
| `remoteUser` | string | ツール操作時のユーザー |
| `containerUser` | string | コンテナ実行ユーザー |
| `updateRemoteUserUID` | boolean | UID/GID をホストに合わせる |
| `overrideCommand` | boolean | キープアライブコマンドの上書き |
| `shutdownAction` | enum | シャットダウン時の動作 |
| `init` | boolean | tini init プロセスの使用 |
| `privileged` | boolean | 特権モード |
| `capAdd` | array | 追加する Linux Capabilities |
| `securityOpt` | array | セキュリティオプション |
| `mounts` | array | 追加マウント |
| `features` | object | Dev Container Features |
| `customizations` | object | ツール固有の設定 |

### イメージ / Dockerfile 固有

| プロパティ | 型 | 説明 |
|---|---|---|
| `image` | string | ベースイメージ |
| `build.dockerfile` | string | Dockerfile のパス |
| `build.context` | string | ビルドコンテキスト |
| `build.args` | object | ビルド引数 |
| `build.target` | string | マルチステージビルドのターゲット |
| `build.cacheFrom` | array | キャッシュイメージ |
| `runArgs` | array | `docker run` の追加引数 |
| `workspaceMount` | string | ワークスペースのマウント設定 |
| `workspaceFolder` | string | コンテナ内のワークスペースパス |

### Docker Compose 固有

| プロパティ | 型 | 説明 |
|---|---|---|
| `dockerComposeFile` | string/array | Compose ファイルのパス |
| `service` | string | 接続するサービス名 |
| `runServices` | array | 起動するサービス |
| `workspaceFolder` | string | コンテナ内のワークスペースパス |

### ライフサイクルスクリプト

| プロパティ | 型 | 説明 |
|---|---|---|
| `initializeCommand` | string/array/object | ホストで実行（起動前） |
| `onCreateCommand` | string/array/object | コンテナ作成時 |
| `updateContentCommand` | string/array/object | プリビルド更新時 |
| `postCreateCommand` | string/array/object | 作成後の最終セットアップ |
| `postStartCommand` | string/array/object | 毎回起動時 |
| `postAttachCommand` | string/array/object | VS Code 接続時 |
| `waitFor` | enum | 接続前に完了を待つコマンド |

### ホスト要件

| プロパティ | 型 | 説明 |
|---|---|---|
| `hostRequirements.cpus` | integer | 最低 CPU コア数 |
| `hostRequirements.memory` | string | 最低メモリ（`"4gb"` 等） |
| `hostRequirements.storage` | string | 最低ディスク容量 |
| `hostRequirements.gpu` | boolean/string/object | GPU 要件 |

## 参考リンク

- [VS Code Dev Containers ドキュメント](https://code.visualstudio.com/docs/devcontainers/containers)
- [Dev Container の作成](https://code.visualstudio.com/docs/devcontainers/create-dev-container)
- [Tips and Tricks](https://code.visualstudio.com/docs/devcontainers/tips-and-tricks)
- [Dev Containers 仕様](https://containers.dev/)
- [devcontainer.json リファレンス](https://containers.dev/implementors/json_reference/)
- [公開 Features 一覧](https://containers.dev/features)
