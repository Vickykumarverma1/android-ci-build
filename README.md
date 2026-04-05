# HabitTracker

Offline Android habit tracker for up to 10 habits with:

- a monthly tick grid
- daily completion storage
- a lower progress graph that maps each day from `0` to `10`
- a GitHub Actions workflow that can build a debug APK

## Build APK

1. Open the project in Android Studio.
2. Let Gradle sync and download dependencies.
3. Install the Android SDK if Android Studio asks for it.
4. Run `Build > Build Bundle(s) / APK(s) > Build APK(s)`.

## Build On GitHub

1. Create a GitHub repository and push this project.
2. Open the `Actions` tab in GitHub.
3. Run the `Build Android APK` workflow.
4. Download the `habit-tracker-apk` artifact from the workflow run.

Note: this repo includes a lightweight `gradlew` launcher so GitHub can download Gradle automatically. If you later install Gradle locally, you can replace it with the official wrapper by running `gradle wrapper`.
