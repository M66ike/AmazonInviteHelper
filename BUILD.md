# Build Amazon Invite Helper v1.5

This project is ready for the included GitHub Actions workflow.

1. Put the contents of this folder in the root of your GitHub repository.
2. Commit/push to `main` or `master`, or run **Build APK** manually from the Actions tab.
3. Download the `AmazonInviteHelper-debug` artifact.
4. The APK inside is `app-debug.apk`.

The workflow uses Java 17, Android platform 37, Build Tools 36.0.0 and Gradle 9.7.0.

After installing/updating the APK, if Amazon Quick Add or account switching does not react, toggle the **Amazon Invite Helper** Accessibility service off and back on once so Android reloads the service.
