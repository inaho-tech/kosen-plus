<div align="center">
  <img src="ios/PlusKosen/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png" width="132" alt="Kosen+ app icon">

  # Kosen+

  **高専生活に必要な情報と道具を、ひとつのアプリに。**

  WebClass、シラバス、時刻表、学習ツールを、見やすく素早く使える学生向けクライアントです。

  ![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
  ![iOS](https://img.shields.io/badge/iOS-SwiftUI-000000?logo=apple&logoColor=white)
  ![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?logo=kotlin&logoColor=white)
  ![Status](https://img.shields.io/badge/status-development-5865F2)

  <br><br>
  <a href="https://github.com/duckcat-chan/kosen-plus/releases">
    <img src="https://raw.githubusercontent.com/rubenpgrady/get-it-on-github/refs/heads/main/get-it-on-github.png" width="210" alt="Get it on GitHub">
  </a>
</div>

---

## Kosen+ について

Kosen+ は、日々の授業で行き来する複数のサービスを一か所にまとめた、Android / iOS 向けアプリです。Webページを単に表示するだけでなく、コース、教材、成績、シラバスなどをモバイル向けの一覧として整理します。

授業前に時刻表を確認する、教材を開く、提出状況を見る、空き時間に学習ツールを使う——その一連の操作を、アプリ内で自然につなげることを目指しています。

## 主な機能

### WebClassを見やすく

- 年度・学期・曜日で整理されたコース一覧
- コース検索、お気に入り、課題の絞り込み
- 教材の詳細、利用履歴、結果履歴
- 成績とレポートの確認
- WebClassのログイン状態とCookieを利用したセッション復帰
- PDF・動画・音声などの教材をアプリ内で表示

### シラバスをすばやく検索

- 学科、学年、科目名による検索と絞り込み
- 科目に応じて変わるアイコン
- 授業概要、到達目標、評価方法、授業計画を読みやすく表示
- iPhone、iPad、Androidタブレットに合わせたレスポンシブ表示

### 通学の時刻表をひとつに

- 小湊バスと電車の時刻表
- 次の便が分かる現在時刻ベースの表示
- 電車駅の検索と複数のお気に入り
- 上り・下りの切り替え
- 運行情報の確認

### 学校生活に役立つツール

- 無音カメラとアプリ内ギャラリー
- 電子出席簿、図書館蔵書検索
- ポモドーロタイマー、ストップウォッチ、ToDo、単位変換
- カルノー図
- 反射神経テスト、オセロ、五目並べなどのミニゲーム

## デザイン

Android版は Jetpack Compose / Material 3、iOS版は SwiftUI を採用しています。各OSの操作感を尊重しながら、情報の優先順位、コースの分類、検索・フィルター、タブレット向けレイアウトを共通化しています。

- ライト / ダークテーマ
- 画面幅に応じたグリッドと一覧
- セーフエリアを考慮したナビゲーション
- iOSでのスワイプによるタブ移動
- 動きを抑える設定とフォーカスモード

## プロジェクト構成

```text
.
├── app/                 Androidアプリ（Kotlin / Jetpack Compose）
├── ios/                 iOSアプリ（Swift / SwiftUI）
├── .github/workflows/   iOSのビルドとIPA生成
└── gradle/              Androidビルド設定
```

## ビルド

### Android

必要なもの:

- Android Studio
- JDK 17
- Android SDK 35

```powershell
.\gradlew.bat assembleDebug
```

生成されたAPKは `app/build/outputs/apk/debug/` にあります。

### iOS

必要なもの:

- macOS / Xcode
- XcodeGen

```bash
cd ios
xcodegen generate
open PlusKosen.xcodeproj
```

GitHub Actions の **Build iOS IPA** から、署名前のIPAとSimulatorプレビューも生成できます。端末へ導入する場合は、利用者自身の証明書とプロビジョニングプロファイルで署名してください。詳しくは [iOS向けREADME](ios/README.md) を参照してください。

## 使用しているサービス

Kosen+ は、次のサービスから利用者が閲覧できる情報を取得・表示します。

- [WebClass](https://webclass.kosen-k.go.jp/webclass/)
- [高専Webシラバス](https://syllabus.kosen-k.go.jp/)
- [どこトレ](https://doko-train.jp/sp/)（アプリ内表記: 電車）

## 注意事項

このプロジェクトは各サービスの公式アプリではありません。各サービスの利用規約、学校の規則、公開範囲に従って利用してください。サービス側の仕様変更により、一部機能が利用できなくなる場合があります。

認証は各サービスのWeb画面を通して行い、MicrosoftアカウントのパスワードをKosen+独自の保存領域へ記録することはありません。

## 開発状況

Android版とiOS版は継続して機能とUIを調整しています。不具合や改善案は、このリポジトリのIssuesから共有してください。
