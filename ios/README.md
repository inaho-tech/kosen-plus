# 高専プラス iOS

Android版と同じサービスをSwiftUIで提供するiOS移植です。Android版のソースには変更を加えません。

## 未署名IPAの作成

1. このリポジトリをGitHubへpushする。
2. GitHubの `Actions` から `Build iOS IPA` を開く。
3. `Run workflow` を実行する。
4. 完了後、Artifactsの `PlusKosen-unsigned-4.5.2-31` をダウンロードする。
5. IPAへ手元の証明書とプロビジョニングプロファイルで署名する。

ローカルのmacOSでは、XcodeGenをインストールして `cd ios && xcodegen generate` を実行するとXcodeプロジェクトを生成できます。

