# HabitTracker

Offline Android habit tracker for up to 10 habits with:

- a monthly tick grid
- daily completion storage
- a lower progress graph that maps each day from `0` to `10`
- a GitHub Actions workflow that builds a signed release APK when signing secrets are configured

## Build APK

1. Open the project in Android Studio.
2. Let Gradle sync and download dependencies.
3. Install the Android SDK if Android Studio asks for it.
4. Run `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

## Build On GitHub

1. Create a GitHub repository and push this project.
2. Open the `Actions` tab in GitHub.
3. Run the `Build Android APK` workflow.
4. Download the artifact from the workflow run.

If release signing is configured, the artifact is `habit-tracker-release-apk`.
If release signing is not configured yet, the workflow falls back to `habit-tracker-debug-apk`.

## Stable App Updates

For clean in-place updates on Android, the app must keep:

- the same `applicationId`
- the same signing key
- a higher version code than the currently installed app

This project now uses the GitHub run number as the CI `versionCode`, so each GitHub build is automatically newer than the last one.

## Release Signing Setup

1. Generate one keystore on your Mac:

```bash
keytool -genkeypair -v \
  -keystore habittracker-release.jks \
  -alias habittracker \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

2. Convert the keystore to base64:

```bash
base64 -i habittracker-release.jks | pbcopy
```

3. In GitHub, open `Settings > Secrets and variables > Actions` and add these repository secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

4. Push a new commit or rerun the workflow.

After that, GitHub will build a signed release APK that can update future installs cleanly.

Important:

- if you already installed an older GitHub-built debug APK, you may need to uninstall it once before installing the first signed release APK
- after switching to the signed release build, future signed APKs can be installed as updates

Note: this repo includes a lightweight `gradlew` launcher so GitHub can download Gradle automatically. If you later install Gradle locally, you can replace it with the official wrapper by running `gradle wrapper`.
