# Testing — partial v3

Not run: Gradle build, automated tests, Paper integration, resource-pack visual validation, and Minecraft client rendering. The GitHub Actions workflow runs `./gradlew clean build` with Temurin 25 and uploads build/libs/*.jar only on success. Run on a connected GitHub repository; inspect workflow logs before deploying. Specifically test 24 and 32 resolutions and legacy database migrations.
