# Chrome 拡張機能開発ガイド（Manifest V3）

## Chrome 拡張機能とは

Chrome 拡張機能は、ブラウザの機能をカスタマイズ・拡張するための小さなプログラムだ。HTML・CSS・JavaScript というWeb技術だけで構築でき、Chrome が提供する専用 API を通じてブラウザの機能に深くアクセスできる。

拡張機能でできることの例:

| カテゴリ | 具体例 |
|---|---|
| ページの変更 | 広告ブロック、ダークモード適用、翻訳の挿入 |
| ブラウザUI拡張 | ツールバーボタン追加、右クリックメニュー拡張 |
| 情報の取得・管理 | タブ管理、ブックマーク操作、履歴検索 |
| 外部サービス連携 | 通知表示、APIとの連携、データの同期 |
| 開発者ツール | DevTools パネル追加、ネットワーク分析 |

現行の拡張機能プラットフォームは **Manifest V3（MV3）** だ。旧仕様の Manifest V2 は段階的に廃止されており、新規開発では必ず MV3 を使用する。

## アーキテクチャ

Chrome 拡張機能は複数のコンポーネントから構成される。各コンポーネントは独立した実行コンテキストを持ち、メッセージパッシングで互いに通信する。

```
┌─────────────────────────────────────────────────────┐
│ Chrome ブラウザ                                       │
│                                                       │
│  ┌─────────────────┐    ┌──────────────────────────┐ │
│  │ Service Worker   │    │ Webページ                 │ │
│  │ (background)     │◄──►│                          │ │
│  │                  │    │  ┌────────────────────┐  │ │
│  │ - イベント駆動    │    │  │ Content Script     │  │ │
│  │ - ブラウザAPI操作 │    │  │                    │  │ │
│  │ - 常駐しない      │    │  │ - DOM操作          │  │ │
│  └────────┬─────────┘    │  │ - ページ情報取得    │  │ │
│           │              │  └────────────────────┘  │ │
│           │              └──────────────────────────┘ │
│  ┌────────▼─────────┐                                 │
│  │ Popup / Options   │                                │
│  │                   │                                │
│  │ - ユーザーUI       │                                │
│  │ - 設定画面         │                                │
│  └───────────────────┘                                │
│                                                       │
│  manifest.json ── 拡張機能全体の定義・設定ファイル       │
└─────────────────────────────────────────────────────┘
```

### 各コンポーネントの役割

| コンポーネント | 実行タイミング | 役割 |
|---|---|---|
| **manifest.json** | — | 拡張機能のメタデータ・権限・構成を定義する設定ファイル |
| **Service Worker** | イベント発生時 | バックグラウンド処理。ブラウザイベントの監視、API呼び出し |
| **Content Script** | ページ読み込み時 | Webページの DOM にアクセスし、内容を読み取り・変更 |
| **Popup** | アイコンクリック時 | ツールバーアイコンをクリックしたときに表示される小さなUI |
| **Options Page** | 設定画面を開いた時 | 拡張機能の設定を変更するための画面 |

## manifest.json

`manifest.json` は拡張機能のルートディレクトリに置く必須ファイルだ。拡張機能の名前・バージョン・権限・各コンポーネントのファイルパスなど、すべての構成情報を定義する。

### 基本構造

```json
{
  "manifest_version": 3,
  "name": "My Extension",
  "version": "1.0",
  "description": "拡張機能の説明（132文字以内）",

  "icons": {
    "16": "images/icon-16.png",
    "32": "images/icon-32.png",
    "48": "images/icon-48.png",
    "128": "images/icon-128.png"
  },

  "background": {
    "service_worker": "service-worker.js",
    "type": "module"
  },

  "action": {
    "default_popup": "popup.html",
    "default_icon": {
      "16": "images/icon-16.png",
      "32": "images/icon-32.png"
    },
    "default_title": "拡張機能を開く"
  },

  "content_scripts": [
    {
      "matches": ["https://*.example.com/*"],
      "js": ["content.js"],
      "css": ["content.css"]
    }
  ],

  "permissions": ["storage", "activeTab"],
  "host_permissions": ["https://*.example.com/*"],

  "options_page": "options.html"
}
```

### 必須フィールド

| フィールド | 説明 |
|---|---|
| `manifest_version` | 必ず `3` を指定する |
| `name` | 拡張機能の名前。Chrome Web Store とブラウザに表示される |
| `version` | セマンティックバージョニング（例: `"1.0.0"`） |

### 主要な設定フィールド

| フィールド | 説明 |
|---|---|
| `description` | 拡張機能の説明。Chrome Web Store では 132 文字以内 |
| `icons` | 16px / 32px / 48px / 128px のアイコン |
| `background` | Service Worker のスクリプトパスを指定。`"type": "module"` で ES Modules を有効化 |
| `action` | ツールバーアイコンの設定。Popup の HTML やアイコンを定義 |
| `content_scripts` | 自動注入する Content Script のマッチパターンとファイルを定義 |
| `permissions` | 必要な API 権限を宣言 |
| `host_permissions` | アクセスするホストのパターンを宣言 |
| `options_page` | 設定画面の HTML ファイル |
| `web_accessible_resources` | Webページからアクセス可能なリソースを定義 |

## Service Worker（バックグラウンド処理）

Manifest V3 の最大の変更点は、**常駐する Background Page が Service Worker に置き換わった**ことだ。Service Worker はイベント駆動で動作し、必要なときだけ起動して処理が終わると停止する。

### 特徴

- **非永続的**: アイドル状態が約30秒続くと自動停止する
- **イベント駆動**: ブラウザイベント（タブの作成、ページの読み込みなど）に応じて起動
- **DOM アクセス不可**: `document` や `window` オブジェクトは使えない
- **ES Modules 対応**: manifest で `"type": "module"` を指定すれば `import` / `export` が使える

### 基本的な実装パターン

```javascript
// service-worker.js

// インストール時の処理
chrome.runtime.onInstalled.addListener((details) => {
  if (details.reason === 'install') {
    // 初回インストール時の初期化
    chrome.storage.local.set({ enabled: true, count: 0 });
    console.log('拡張機能がインストールされました');
  }
});

// ツールバーアイコンがクリックされたときの処理
chrome.action.onClicked.addListener(async (tab) => {
  // Content Script を動的に注入
  await chrome.scripting.executeScript({
    target: { tabId: tab.id },
    files: ['content.js']
  });
});

// アラーム（定期実行）
chrome.alarms.create('periodic-check', { periodInMinutes: 1 });

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === 'periodic-check') {
    // 定期的な処理
  }
});
```

### 注意: Service Worker のライフサイクル

Service Worker は停止・再起動を繰り返すため、**グローバル変数に状態を保持してはならない**。状態は必ず `chrome.storage` API で永続化する。

```javascript
// 悪い例: Service Worker が停止するとデータが消える
let count = 0;
chrome.action.onClicked.addListener(() => {
  count++; // 再起動後は 0 に戻る
});

// 良い例: storage に永続化する
chrome.action.onClicked.addListener(async () => {
  const { count } = await chrome.storage.local.get({ count: 0 });
  await chrome.storage.local.set({ count: count + 1 });
});
```

また、**イベントリスナーは Service Worker のトップレベルで登録する**必要がある。非同期処理の中で登録すると、再起動時にリスナーが登録される前にイベントが発火し、見逃す可能性がある。

```javascript
// 悪い例: 非同期処理の中でリスナーを登録
chrome.storage.local.get('settings').then((data) => {
  chrome.tabs.onUpdated.addListener(() => { /* ... */ });
});

// 良い例: トップレベルで登録
chrome.tabs.onUpdated.addListener(async (tabId, changeInfo, tab) => {
  const { settings } = await chrome.storage.local.get('settings');
  // settings を使った処理
});
```

## Content Script

Content Script は **Webページのコンテキストで実行される** JavaScript だ。ページの DOM を読み取り・変更できるが、拡張機能のAPIには `chrome.runtime` など一部しかアクセスできない。

### 実行環境の分離（Isolated World）

Content Script はページの JavaScript とは**分離された環境（Isolated World）**で動作する。

```
┌─────────────────────────────────┐
│ Webページ                        │
│                                  │
│  ┌──────────────┐ ┌───────────┐ │
│  │ ページの JS   │ │ Content   │ │
│  │              │ │ Script    │ │
│  │ 変数・関数を  │ │           │ │
│  │ 共有しない    │ │ 独立した   │ │
│  │              │ │ JS環境     │ │
│  └──────┬───────┘ └─────┬─────┘ │
│         │               │       │
│         └───────┬───────┘       │
│                 │               │
│          ┌──────▼──────┐        │
│          │   共有 DOM   │        │
│          └─────────────┘        │
│                                  │
└─────────────────────────────────┘
```

- **DOM は共有**: 両者とも同じ DOM を操作できる
- **JavaScript 環境は分離**: ページの変数や関数に直接アクセスできない。逆も同様
- **セキュリティ**: ページ側の悪意あるスクリプトから Content Script が保護される

### 宣言的な注入（manifest で指定）

`manifest.json` の `content_scripts` フィールドで、どのページに自動注入するかを指定する。

```json
{
  "content_scripts": [
    {
      "matches": ["https://*.example.com/*"],
      "exclude_matches": ["https://admin.example.com/*"],
      "js": ["content.js"],
      "css": ["content.css"],
      "run_at": "document_idle"
    }
  ]
}
```

| プロパティ | 説明 |
|---|---|
| `matches` | 注入対象の URL パターン（必須） |
| `exclude_matches` | 除外する URL パターン |
| `js` | 注入する JavaScript ファイル |
| `css` | 注入する CSS ファイル |
| `run_at` | 注入タイミング。`document_start` / `document_end` / `document_idle`（デフォルト） |

### プログラム的な注入（動的に実行）

`chrome.scripting` API を使って、必要なタイミングで動的にスクリプトを注入することもできる。この方法には `scripting` 権限と `activeTab` または `host_permissions` が必要だ。

```javascript
// Service Worker から Content Script を注入
chrome.action.onClicked.addListener(async (tab) => {
  await chrome.scripting.executeScript({
    target: { tabId: tab.id },
    files: ['content.js']
  });
});

// インラインの関数を直接注入することも可能
chrome.action.onClicked.addListener(async (tab) => {
  await chrome.scripting.executeScript({
    target: { tabId: tab.id },
    func: () => {
      document.body.style.backgroundColor = 'red';
    }
  });
});
```

### Content Script の実装例

```javascript
// content.js — ページ内のすべてのリンクにターゲット属性を追加する例

const links = document.querySelectorAll('a[href^="http"]');
links.forEach((link) => {
  // 外部リンクを新しいタブで開くようにする
  if (!link.hostname.includes(window.location.hostname)) {
    link.setAttribute('target', '_blank');
    link.setAttribute('rel', 'noopener noreferrer');
  }
});
```

## Popup（ツールバーUI）

Popup はツールバーのアイコンをクリックしたときに表示される小さな HTML ページだ。通常の HTML/CSS/JavaScript で構築する。

### ファイル構成

```
popup.html   ← UI の HTML
popup.css    ← スタイル
popup.js     ← ロジック
```

### 実装例

```html
<!-- popup.html -->
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <link rel="stylesheet" href="popup.css">
</head>
<body>
  <h1>My Extension</h1>
  <label>
    <input type="checkbox" id="enabled"> 有効にする
  </label>
  <p>カウント: <span id="count">0</span></p>
  <button id="reset">リセット</button>
  <script src="popup.js"></script>
</body>
</html>
```

```css
/* popup.css */
body {
  width: 240px;
  padding: 12px;
  font-family: sans-serif;
}

h1 {
  font-size: 16px;
  margin: 0 0 12px;
}

button {
  margin-top: 8px;
  padding: 4px 12px;
  cursor: pointer;
}
```

```javascript
// popup.js
document.addEventListener('DOMContentLoaded', async () => {
  const { enabled, count } = await chrome.storage.local.get({
    enabled: true,
    count: 0
  });

  const checkbox = document.getElementById('enabled');
  const countSpan = document.getElementById('count');
  const resetButton = document.getElementById('reset');

  checkbox.checked = enabled;
  countSpan.textContent = count;

  checkbox.addEventListener('change', () => {
    chrome.storage.local.set({ enabled: checkbox.checked });
  });

  resetButton.addEventListener('click', async () => {
    await chrome.storage.local.set({ count: 0 });
    countSpan.textContent = '0';
  });
});
```

Popup は**アイコンからフォーカスが外れると閉じる**。閉じると JavaScript の状態は破棄されるため、状態の保持には `chrome.storage` を使う。

## メッセージパッシング

各コンポーネントは独立した実行コンテキストで動作するため、コンポーネント間の通信にはメッセージパッシングを使う。

### 単発メッセージ（One-time Message）

もっとも基本的な通信方法。リクエストを送り、レスポンスを受け取る。

```javascript
// Content Script → Service Worker にメッセージを送信
chrome.runtime.sendMessage({ type: 'get-user-data' }, (response) => {
  console.log('受信:', response);
});
```

```javascript
// Service Worker でメッセージを受信
chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (message.type === 'get-user-data') {
    // 同期的にレスポンスを返す
    sendResponse({ username: 'demo-user' });
  }
});
```

### Service Worker → Content Script への送信

Service Worker から特定のタブの Content Script にメッセージを送るには `chrome.tabs.sendMessage` を使う。

```javascript
// Service Worker → 特定タブの Content Script にメッセージを送信
async function sendMessageToActiveTab(message) {
  const [tab] = await chrome.tabs.query({
    active: true,
    lastFocusedWindow: true
  });
  const response = await chrome.tabs.sendMessage(tab.id, message);
  return response;
}
```

### 長期接続（Long-lived Connection）

頻繁にメッセージをやり取りする場合は、ポートを使った持続的な接続を確立できる。

```javascript
// Content Script 側: 接続を確立
const port = chrome.runtime.connect({ name: 'data-stream' });

port.postMessage({ type: 'subscribe', topic: 'updates' });

port.onMessage.addListener((message) => {
  console.log('受信:', message);
});
```

```javascript
// Service Worker 側: 接続を受け付け
chrome.runtime.onConnect.addListener((port) => {
  if (port.name === 'data-stream') {
    port.onMessage.addListener((message) => {
      if (message.type === 'subscribe') {
        // 定期的にデータを送信
        port.postMessage({ data: 'new update' });
      }
    });
  }
});
```

## 主要 API

Chrome 拡張機能が利用できる主要な API を紹介する。各 API の利用には `manifest.json` の `permissions` での宣言が必要なものがある。

### chrome.storage — データの永続化

拡張機能専用のストレージ。`localStorage` と異なり、Service Worker からもアクセスでき、デバイス間の同期にも対応する。

```javascript
// データの保存
await chrome.storage.local.set({ key: 'value', count: 42 });

// データの取得（デフォルト値付き）
const { key, count } = await chrome.storage.local.get({
  key: 'default',
  count: 0
});

// データの削除
await chrome.storage.local.remove('key');

// 変更の監視
chrome.storage.onChanged.addListener((changes, areaName) => {
  for (const [key, { oldValue, newValue }] of Object.entries(changes)) {
    console.log(`${key}: ${oldValue} → ${newValue}`);
  }
});
```

| ストレージ領域 | 容量 | 同期 | 用途 |
|---|---|---|---|
| `storage.local` | 10MB | なし | ローカルのみのデータ |
| `storage.sync` | 100KB | Chrome アカウント間で同期 | ユーザー設定 |
| `storage.session` | 10MB | なし（セッション限定） | 一時的なデータ |

**権限**: `"storage"`

### chrome.tabs — タブの操作

タブの作成・更新・クエリなどを行う。

```javascript
// アクティブなタブを取得
const [tab] = await chrome.tabs.query({
  active: true,
  currentWindow: true
});

// 新しいタブを作成
const newTab = await chrome.tabs.create({
  url: 'https://example.com'
});

// タブの更新を監視
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete') {
    console.log(`タブ ${tabId} の読み込みが完了`);
  }
});
```

**権限**: タブの URL やタイトルへのアクセスには `"tabs"` が必要。

### chrome.alarms — 定期実行・遅延実行

Service Worker は非永続的なため、`setTimeout` / `setInterval` は信頼できない。代わりに `chrome.alarms` を使う。

```javascript
// 1分ごとに繰り返すアラーム
chrome.alarms.create('check-updates', {
  periodInMinutes: 1
});

// 5分後に1回だけ発火するアラーム
chrome.alarms.create('delayed-task', {
  delayInMinutes: 5
});

// アラーム発火時の処理
chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name === 'check-updates') {
    // 定期チェック処理
  }
});
```

**権限**: `"alarms"`。最小間隔は30秒。

### chrome.notifications — デスクトップ通知

```javascript
chrome.notifications.create('my-notification', {
  type: 'basic',
  iconUrl: 'images/icon-128.png',
  title: '通知タイトル',
  message: '通知の本文テキスト'
});
```

**権限**: `"notifications"`

### chrome.contextMenus — 右クリックメニュー

```javascript
// Service Worker で登録（インストール時に1回だけ）
chrome.runtime.onInstalled.addListener(() => {
  chrome.contextMenus.create({
    id: 'search-selection',
    title: '"%s" を検索',
    contexts: ['selection']
  });
});

chrome.contextMenus.onClicked.addListener((info, tab) => {
  if (info.menuItemId === 'search-selection') {
    const query = encodeURIComponent(info.selectionText);
    chrome.tabs.create({
      url: `https://www.google.com/search?q=${query}`
    });
  }
});
```

**権限**: `"contextMenus"`

## 権限（Permissions）

Chrome 拡張機能は、利用する機能に応じて権限を宣言する必要がある。ユーザーの信頼を得るため、**必要最小限の権限のみを要求する**のが原則だ。

### 権限の種類

| 種類 | manifest のキー | 説明 |
|---|---|---|
| **API 権限** | `permissions` | Chrome API を使うための権限。インストール時に付与される |
| **ホスト権限** | `host_permissions` | 特定のサイトへのアクセス権。インストール時にユーザーに警告が表示される |
| **オプショナル権限** | `optional_permissions` | 必要になった時点でユーザーに許可を求める。初回インストール時の警告を減らせる |

### よく使う権限の一覧

| 権限 | 用途 |
|---|---|
| `activeTab` | ユーザーが明示的にアイコンをクリックしたときだけ、現在のタブにアクセス |
| `storage` | `chrome.storage` API の利用 |
| `tabs` | タブの URL・タイトルへのアクセス |
| `alarms` | `chrome.alarms` による定期実行 |
| `notifications` | デスクトップ通知の表示 |
| `contextMenus` | 右クリックメニューの追加 |
| `scripting` | `chrome.scripting` による動的スクリプト注入 |
| `webRequest` | ネットワークリクエストの監視 |

### activeTab の推奨

`host_permissions` で `<all_urls>` を指定するとすべてのサイトへのアクセスを要求することになり、ユーザーに強い警告が表示される。代わりに **`activeTab`** を使えば、ユーザーがアイコンをクリックした時だけ現在のタブにアクセスでき、警告も表示されない。

```json
{
  "permissions": ["activeTab", "scripting"]
}
```

### オプショナル権限のリクエスト

```javascript
// 必要になったタイミングで権限をリクエスト
const granted = await chrome.permissions.request({
  permissions: ['bookmarks'],
  origins: ['https://api.example.com/*']
});

if (granted) {
  // 権限が付与された
}
```

## 開発・デバッグ方法

### プロジェクトのディレクトリ構成

```
my-extension/
├── manifest.json
├── service-worker.js
├── content.js
├── popup.html
├── popup.css
├── popup.js
├── options.html
├── options.js
└── images/
    ├── icon-16.png
    ├── icon-32.png
    ├── icon-48.png
    └── icon-128.png
```

### ローカルでの読み込み手順

1. Chrome で `chrome://extensions` を開く
2. 右上の **「デベロッパー モード」** をオンにする
3. **「パッケージ化されていない拡張機能を読み込む」** をクリック
4. 拡張機能のディレクトリを選択

コードを変更した場合は、`chrome://extensions` で拡張機能の **リロードボタン（丸い矢印）** をクリックすると反映される。

### デバッグ方法

| 対象 | 方法 |
|---|---|
| **Service Worker** | `chrome://extensions` の拡張機能カードにある「Service Worker」リンクをクリック → DevTools が開く |
| **Popup** | Popup を表示した状態で右クリック →「検証」を選択 → DevTools が開く |
| **Content Script** | 対象ページで DevTools を開き、Sources パネルの Content Scripts セクションで確認 |
| **エラー確認** | `chrome://extensions` の「エラー」ボタンでエラーログを確認 |

### よくあるトラブルと対処

| 症状 | 原因と対処 |
|---|---|
| 拡張機能が読み込めない | `manifest.json` の JSON 構文エラー。エラーメッセージを確認 |
| Content Script が動かない | `matches` パターンが対象 URL と一致していない。パターンを確認 |
| Service Worker がすぐ停止する | 正常な動作。状態を `chrome.storage` に保存しているか確認 |
| `chrome.xxx is undefined` | 必要な `permissions` が `manifest.json` に宣言されていない |
| 変更が反映されない | `chrome://extensions` でリロードしたか確認。Content Script はページの再読み込みも必要 |

## Chrome Web Store への公開

### 1. 開発者アカウントの登録

- [Chrome Web Store Developer Dashboard](https://chrome.google.com/webstore/devconsole) にアクセス
- Google アカウントでログイン
- **一回限りの登録料 $5** を支払う
- 2段階認証の有効化が必須

### 2. パッケージの準備

拡張機能のディレクトリを ZIP ファイルにまとめる。不要なファイル（`.git`、`node_modules`、開発用設定ファイルなど）は除外する。

```bash
# 例: 必要なファイルだけを ZIP にまとめる
zip -r my-extension.zip manifest.json *.js *.html *.css images/
```

`manifest.json` に以下のフィールドが正しく設定されていることを確認する:

- `name` — 拡張機能の名前
- `version` — バージョン番号
- `description` — 132文字以内の説明
- `icons` — 128px のアイコンは必須

### 3. ストアへのアップロード

1. Developer Dashboard で「新しいアイテム」をクリック
2. ZIP ファイルをアップロード
3. ストア掲載情報を入力:
   - **説明文**: 拡張機能の機能と価値を明確に記述
   - **スクリーンショット**: 1280x800 または 640x400 のスクリーンショットを最低1枚
   - **カテゴリ**: 適切なカテゴリを選択
   - **言語**: 対応言語を設定
4. **プライバシーへの取り組み**: どのデータを収集・使用するかを正直に申告

### 4. レビューと公開

- 提出すると Google によるレビューが行われる
- レビュー期間は通常 **1〜3営業日**
- 違反が見つからなければ承認され、公開される
- 違反があった場合はリジェクトされ、理由が通知される

### レビューで注意するポイント

- **最小権限の原則**: 不要な権限を要求していないか
- **プライバシーポリシー**: ユーザーデータを扱う場合は必須
- **機能の明確さ**: 拡張機能の目的と動作が明確であること
- **リモートコードの禁止**: Manifest V3 では外部サーバーからのコード実行は禁止されている

## まとめ

Chrome 拡張機能開発の要点を整理する:

| 項目 | ポイント |
|---|---|
| プラットフォーム | **Manifest V3** を使用する（V2 は廃止） |
| 中心ファイル | `manifest.json` にすべての構成を定義 |
| バックグラウンド | **Service Worker** はイベント駆動・非永続。状態は `chrome.storage` に保存 |
| ページ操作 | **Content Script** で DOM にアクセス。Isolated World で安全に動作 |
| UI | **Popup** / **Options Page** で HTML ベースのインターフェースを提供 |
| コンポーネント間通信 | **メッセージパッシング**（`chrome.runtime.sendMessage` / `chrome.tabs.sendMessage`） |
| 権限 | 最小限の権限を宣言。可能なら `activeTab` を使う |
| 公開 | Chrome Web Store に $5 で開発者登録。レビューは 1〜3営業日 |

## 参考リンク

- [Chrome Extensions ドキュメント（公式）](https://developer.chrome.com/docs/extensions)
- [Getting Started Guide](https://developer.chrome.com/docs/extensions/get-started)
- [Manifest V3 の概要](https://developer.chrome.com/docs/extensions/develop/migrate/what-is-mv3)
- [Chrome Extensions API リファレンス](https://developer.chrome.com/docs/extensions/reference/api)
- [Chrome Web Store への公開](https://developer.chrome.com/docs/webstore/publish)
- [Chrome Extensions サンプル集（GitHub）](https://github.com/GoogleChrome/chrome-extensions-samples)
