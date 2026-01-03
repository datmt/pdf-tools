# JavaFX Application Template

A production-ready JavaFX application template with multi-platform build pipeline (Linux, macOS, Windows) and automatic releases to GitHub and Cloudflare R2.

## Features

- **JavaFX 21** with FXML support
- **Multi-platform builds**: Linux (AppImage), macOS (DMG for x86_64 and ARM), Windows (ZIP)
- **GitHub Actions CI/CD**: Automatic builds on tag push
- **Cloudflare R2 deployment**: Automatic upload of release artifacts
- **jlink + jpackage**: Creates optimized, self-contained native applications
- **Auto-versioning**: Simple script to tag and trigger releases
- **Logging**: Pre-configured with SLF4J and Logback

## Quick Start

### 1. Initialize Your Project

```bash
# Clone or copy this template
git clone <your-template-repo> my-new-app
cd my-new-app

# Run the setup script
./scripts/setup.sh
```

The setup script will ask for:
- **Project name** (e.g., "my-app") - used for artifact names
- **Display name** (e.g., "My Application") - shown in UI
- **Package name** (e.g., "com.example.myapp") - Java package
- **Description** (e.g., "My awesome JavaFX application")
- **Vendor name** (e.g., "Your Name")

### 2. Add Your Icons

Replace the placeholder icons in `src/main/resources/__PACKAGE_PATH__/icons/` with your own:
- `app-icon.png` (256x256 or larger, square)
- `app-icon-32.png` (32x32, optional)
- `app-icon-64.png` (64x64, optional)

### 3. Configure R2 Deployment (Optional)

If you want to deploy to Cloudflare R2, add these secrets to your GitHub repository:
- `R2_ACCESS_KEY_ID` - Your R2 access key
- `R2_SECRET_ACCESS_KEY` - Your R2 secret key
- `R2_ACCOUNT_ID` - Your Cloudflare account ID
- `R2_BUCKET_NAME` - Your R2 bucket name

Also create a GitHub environment named `release`.

### 4. Start Developing

```bash
# Run in development mode
mvn javafx:run

# Build fat JAR
mvn clean package

# Run the JAR
java -jar target/__ARTIFACT_ID__-1.0-SNAPSHOT.jar
```

### 5. Release

```bash
# The tag-n-build.sh script auto-increments version and pushes tag
./tag-n-build.sh

# Or manually:
git tag v1.0.0
git push origin v1.0.0
```

This triggers the GitHub Actions workflow which:
1. Builds native packages for all platforms
2. Creates a GitHub Release with binaries
3. Uploads binaries to Cloudflare R2

## Project Structure

```
.
├── .github/workflows/release.yml    # CI/CD pipeline
├── src/main/
│   ├── java/__PACKAGE_PATH__/
│   │   ├── Launcher.java            # Entry point (for fat JAR)
│   │   ├── MainApplication.java     # JavaFX Application class
│   │   └── ui/
│   │       └── MainController.java  # Main window controller
│   └── resources/__PACKAGE_PATH__/
│       ├── ui/
│       │   └── main.fxml           # Main window layout
│       └── icons/                   # Application icons
├── pom.xml                          # Maven configuration
├── tag-n-build.sh                   # Auto-versioning script
└── scripts/setup.sh                 # Template initialization script
```

## Customization

### Modify the UI

Edit `src/main/resources/__PACKAGE_PATH__/ui/main.fxml` and `MainController.java`

### Add Dependencies

Add to `pom.xml` in the `<dependencies>` section

### Modify Build Settings

- Change Java version: Update `java.version` in `pom.xml`
- Change JavaFX version: Update `javafx.version` in `pom.xml`
- Modify jlink modules: Edit `.github/workflows/release.yml`

## Build Locally

### Linux AppImage
Requires: Linux, Docker (optional), or native tools

```bash
mvn clean package
# Follow the steps in .github/workflows/release.yml (build-linux job)
```

### macOS DMG
Requires: macOS

```bash
mvn clean package
# Follow the steps in .github/workflows/release.yml (build-macos job)
```

### Windows
Requires: Windows

```bash
mvn clean package
# Follow the steps in .github/workflows/release.yml (build-windows job)
```

## Troubleshooting

### "No JavaFX runtime found" when running JAR
The project uses a `Launcher` class to work around this. Make sure your manifest points to `Launcher` not `MainApplication`.

### Icons not showing
Check that icon files exist at `src/main/resources/__PACKAGE_PATH__/icons/` and are valid PNG files.

### GitHub Actions failing
Check that all secrets are configured correctly and the `release` environment exists.

## License

[Your License Here]
