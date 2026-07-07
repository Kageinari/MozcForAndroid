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
CI で artifact 取得に失敗する場合は、リポジトリ Secrets に `MOZC_ARTIFACT_TOKEN`（`repo` 権限の PAT）を設定してください。
