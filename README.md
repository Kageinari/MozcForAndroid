# Mozc for Android
Mozc for Android バージョン 2.23.2815.103-arm64 をベースにしてキーボードを追加しています。

辞書の再構成やpicファイルの追加に関しては
[Mozc for Android用mozcのWiki](https://github.com/kachaya/mozc/wiki)
を参照してください。

<p>
<img src="Screenshot_STROKE.png" width="240px" />
<img src="Screenshot_JISKANA.png" width="240px" />
</p>
<p>
<img src="Screenshot_QWERTY.png" width="240px" />
<img src="Screenshot_50ON.png" width="240px" />
</p>
<img src="Screenshot_TABLET.png" width="480px" />

ストローク入力での操作
* 日英切り替えは「左下→右上」のストローク
* 絵文字等のシンボル入力は「左上→右下」のストローク
* オプションメニュー表示は「左下→右上→左下」のストローク

## ビルド

`libmozc.so` はリポジトリに含めません（[Kageinari/mozc](https://github.com/Kageinari/mozc) の CI 成果物 `native_libs.zip` から取得）。

```bash
# GitHub API (curl) で artifact 取得（推奨）
export GH_TOKEN=...   # mozc リポジトリの Actions artifact 読み取り権限
scripts/fetch_native_libs.sh install --artifact

# 手元に native_libs.zip がある場合
scripts/fetch_native_libs.sh install --zip /path/to/native_libs.zip

./gradlew assembleRelease -x lint
```

署名付き APK は [GitHub Actions](https://github.com/Kageinari/MozcForAndroid/actions) の `MozcForAndroid-apk` アーティファクトから取得してください。

### CI: `MOZC_ARTIFACT_TOKEN` の設定

[build-apk.yaml](.github/workflows/build-apk.yaml) は `Kageinari/mozc` の Actions artifact（`native_libs.zip`）を curl で取得します。デフォルトの `GITHUB_TOKEN` は **このリポジトリ専用** のため、別リポジトリの artifact には使えません。初回セットアップ時に次の Secret を登録してください。

1. **PAT を作成**（[GitHub → Settings → Developer settings → Personal access tokens](https://github.com/settings/tokens)）
   - **Classic PAT** の場合: `repo` スコープにチェック（公開リポジトリの artifact 取得に必要）
   - **Fine-grained PAT** の場合: リポジトリに `Kageinari/mozc` を指定し、**Actions: Read-only** を付与
2. **Secret を登録**（[MozcForAndroid → Settings → Secrets and variables → Actions](https://github.com/Kageinari/MozcForAndroid/settings/secrets/actions)）
   - **New repository secret**
   - Name: `MOZC_ARTIFACT_TOKEN`
   - Secret: 手順 1 で作成した PAT
3. **CI を再実行** — 失敗した workflow run を **Re-run all jobs** するか、`main` に push して確認

ローカルで `scripts/fetch_native_libs.sh install --artifact` を使う場合も、同じ PAT を `export GH_TOKEN=...` で渡してください。
