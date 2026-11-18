# Build SkyShard Profit online (no local Java or Gradle installation)

This project includes a GitHub Actions workflow. GitHub's hosted runner installs Java 25 and Gradle 9.9.1, builds the mod, and provides the JAR as a downloadable artifact. You do not need to install either tool on your Windows PC.

## Steps

1. Sign in at https://github.com/ (create a free account if needed).
2. Open https://github.com/new and create a repository named `SkyShardProfit`. A Public repository is simplest for a free build.
3. Extract `SkyShardProfit-Fabric-26.1.2.zip` on your PC. Open the extracted `SkyShardProfit` folder.
4. In File Explorer, enable **View → Show → Hidden items** so you can see the `.github` folder.
5. In your new GitHub repository, choose **Add file → Upload files**. Drag all items *inside* the extracted `SkyShardProfit` folder into the upload area. Make sure `.github/workflows/build.yml` is included. Commit the upload to the default branch (`main` or `master`).
6. Open the repository's **Actions** tab. Select **Build SkyShard Profit**. If GitHub asks, enable workflows. The first build should start automatically after the commit. Otherwise choose **Run workflow**.
7. Open the completed run with a green check. Scroll down to **Artifacts** and download `SkyShardProfit-1.0.0`.
8. Extract the downloaded artifact ZIP. It contains `skyshardprofit-1.0.0.jar`.
9. Put that JAR in the `mods` folder for your Minecraft 26.1.2 Fabric instance in Prism Launcher. Also install the matching Fabric API version for your instance.

## If the build fails

Open the failed run, select the **Compile Fabric mod** job, and copy the red error section. Share it so the build configuration can be fixed.

The workflow uses GitHub Actions, `actions/setup-java`, `gradle/actions/setup-gradle`, and `actions/upload-artifact`. Gradle runs on GitHub's hosted computer, not on your PC.
