# GachaUdon

Vault で料金を徴収してガチャを回せる Paper/Spigot プラグインです。

## 特徴
- `plugins/GachaUdon/Machine/*.yml` に複数のガチャマシンを配置可能。
- 景品はアイテムID・個数・当選確率（小数パーセント表記）に加え、displayName/lore/enchants/customModelData/flags などの ItemMeta も指定可能。
- Vault 経済連携。価格が 0 のマシンは Vault なしでも動作。
- 英語/日本語メッセージ同梱。`plugins/GachaUdon/lang/` で上書き可能。
- `/gacha` の追加エイリアスを `config.yml` で設定可能。

## コマンド
- `/gacha list` - マシン一覧を表示。
- `/gacha info <machine>` - 価格と排出率を表示。
- `/gacha roll <machine> [回数]` - 支払い後にガチャを回す。
- `/gacha reload` - 設定・言語・マシン定義を再読み込み。

## 看板トリガー
右クリックだけでガチャ/情報を実行できます。看板に次の内容を書いてください:
1. `[gachaudon]`
2. マシン名
3. 回す回数 または `info`（空欄なら roll 1回）
4. メモ（機能的には無視されます）

## 権限
- `gachaudon.use` - `/gacha` コマンド利用（デフォルト: true）
- `gachaudon.admin` - reload/エイリアス設定（デフォルト: op）

## 設定
`plugins/GachaUdon/config.yml`
- `commandAliases` - `/gacha` の追加エイリアス。
- `maxRollsPerCommand` - 1 度に回せる最大回数（既定値 10）。
- `machineFolder` - マシン YAML を置くフォルダ名（既定: `Machine`）。
- `defaultLocale` - サーバー/JVM ロケールを上書きする場合に指定。
- `discord.*` - EssentialsX Discord で結果を送信する設定。

### EssentialsX Discord 設定例
`plugins/GachaUdon/config.yml`:
```yaml
discord:
  sendRollResults: true
  messageType: "gachaudon"
  allowMentions: false
```
`plugins/EssentialsDiscord/config.yml`（あなたの環境に合わせて channel を設定してください）:
```yaml
message-types:
  gachaudon: primary
```

## マシン YAML 例
`plugins/GachaUdon/Machine/` 配下に配置します。

```yaml
id: starter
displayName: "&bスターターガチャ"
price: 250.0
items:
  - item: "minecraft:diamond"
    amount: 1
    chance: 0.5   # 小数パーセント
    displayName: "&bピカピカのダイヤ"
    lore: ["&7キラリと光る宝石"]
  - item: "minecraft:enchanted_book"
    amount: 1
    chance: 3.0
    displayName: "&dシャープネスの書"
    lore:
      - "&7Sharpness V が付与されたエンチャ本"
    enchants:
      sharpness: 5
  - item: "minecraft:iron_ingot"
    amount: 8
    chance: 12.5
```

マシンが見つからない場合、`sample.yml` が自動で生成されます。
