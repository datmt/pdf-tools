# Template Usage Guide

This is a JavaFX application template with a complete CI/CD pipeline for multi-platform releases.

## Quick Start

### Option 1: Use the Setup Script (Recommended)

```bash
# 1. Copy this entire template directory to your new project location
cp -r javafx-app-template /path/to/my-new-project
cd /path/to/my-new-project

# 2. Run the setup script
./scripts/setup.sh

# 3. Start developing!
mvn javafx:run
```

The setup script will:
- Ask for your project details (name, package, description, etc.)
- Replace all placeholders throughout the template
- Create the correct package directory structure
- Generate placeholder icons
- Create logback configuration
- Set up .gitignore
- Self-destruct (remove itself after setup)

### Option 2: Manual Setup

If you prefer to set things up manually:

1. **Search and replace** the following placeholders in all files:
   - `__PROJECT_NAME__` → Your display name (e.g., "My Application")
   - `__ARTIFACT_ID__` → Maven artifact ID (e.g., "my-app")
   - `__PACKAGE_NAME__` → Java package (e.g., "com.example.myapp")
   - `__PACKAGE_PATH__` → Package as path (e.g., "com/example/myapp")
   - `__GROUP_ID__` → Maven group ID (e.g., "com.example")
   - `__DESCRIPTION__` → Project description
   - `__VENDOR__` → Your name/company

2. **Rename directories**:
   ```bash
   # In src/main/java/ and src/main/resources/
   mv __PACKAGE_PATH__ com/example/myapp  # Use your actual package path
   ```

3. **Add your icons** to `src/main/resources/[your-package-path]/icons/`

## What's Included

### Files and Structure

```
javafx-app-template/
├── .github/workflows/
│   └── release.yml              # Multi-platform build pipeline
├── src/main/
│   ├── java/__PACKAGE_PATH__/
│   │   ├── Launcher.java        # Entry point for fat JAR
│   │   ├── MainApplication.java # JavaFX Application class
│   │   └── ui/
│   │       └── MainController.java
│   └── resources/__PACKAGE_PATH__/
│       ├── ui/
│       │   └── main.fxml        # Main window layout
│       └── icons/               # Application icons
├── scripts/
│   └── setup.sh                 # Template initialization script
├── pom.xml                      # Maven configuration
├── tag-n-build.sh              # Auto-versioning script
├── .gitignore                   # Git ignore patterns
├── README.md                    # Project documentation
└── TEMPLATE_USAGE.md           # This file
```

### The Pipeline

The GitHub Actions workflow (`.github/workflows/release.yml`) automatically:

1. **Triggers on tag push** (e.g., `v1.0.0`)
2. **Builds for 4 platforms**:
   - Linux: AppImage (x86_64)
   - macOS: DMG (x86_64 and ARM64)
   - Windows: ZIP (x86_64)
3. **Creates optimized binaries** using jlink + jpackage
4. **Publishes to GitHub Releases**
5. **Uploads to Cloudflare R2** (optional)

### The Auto-Versioning Script

`tag-n-build.sh` simplifies releases:

```bash
./tag-n-build.sh
```

It automatically:
- Gets the latest tag
- Increments the patch version
- Creates and pushes the new tag
- Triggers the build pipeline

## After Setup

### 1. Test Locally

```bash
# Run in development mode
mvn javafx:run

# Build fat JAR
mvn clean package

# Run the fat JAR
java -jar target/your-app-1.0-SNAPSHOT.jar
```

### 2. Customize Your Application

- Edit `src/main/resources/[package]/ui/main.fxml` for UI layout
- Edit `src/main/java/[package]/ui/MainController.java` for logic
- Add dependencies to `pom.xml`
- Add more FXML views and controllers as needed

### 3. Replace Icons

Add your own PNG icons to `src/main/resources/[package]/icons/`:
- `app-icon.png` (256x256 or larger, required)
- `app-icon-64.png` (64x64, optional)
- `app-icon-32.png` (32x32, optional)

### 4. Set Up Git and GitHub

```bash
# Initialize git
git init
git add .
git commit -m "Initial commit from JavaFX template"

# Create GitHub repository and push
git remote add origin https://github.com/yourusername/your-repo.git
git branch -M main
git push -u origin main
```

### 5. Configure R2 (Optional)

If you want automatic deployment to Cloudflare R2:

1. Create a Cloudflare R2 bucket
2. Generate R2 API tokens
3. Add GitHub secrets:
   - `R2_ACCESS_KEY_ID`
   - `R2_SECRET_ACCESS_KEY`
   - `R2_ACCOUNT_ID`
   - `R2_BUCKET_NAME`
4. Create a GitHub environment named `release`

### 6. Create Your First Release

```bash
# Option 1: Use the auto-versioning script
./tag-n-build.sh

# Option 2: Manual tagging
git tag v1.0.0
git push origin v1.0.0
```

Watch the GitHub Actions tab to see your multi-platform build in action!

## Tips and Best Practices

### Version Management

- Use semantic versioning: `vMAJOR.MINOR.PATCH`
- The auto-versioning script increments PATCH
- For major/minor bumps, tag manually:
  ```bash
  git tag v2.0.0
  git push origin v2.0.0
  ```

### Pre-releases

Tag with alpha/beta/rc suffixes for pre-releases:
```bash
git tag v1.0.0-beta.1
git push origin v1.0.0-beta.1
```

The pipeline automatically marks these as pre-releases.

### Icon Guidelines

- Use PNG format
- Main icon should be at least 256x256 (higher is better)
- Use transparent background for best results
- Square aspect ratio

### Adding Dependencies

When adding new dependencies to `pom.xml`:

1. Standard Java libraries work out of the box
2. For libraries with native dependencies, you may need to adjust the jlink modules in `.github/workflows/release.yml`
3. Test the fat JAR locally before releasing

### Troubleshooting

**"No JavaFX runtime found" error:**
- The `Launcher` class should handle this
- Make sure `Launcher` is set as the main class in pom.xml

**Build fails on GitHub Actions:**
- Check that all placeholders have been replaced
- Verify the main class path is correct
- Review the workflow logs for specific errors

**Icons not appearing:**
- Check file paths match your package structure
- Ensure icons are valid PNG files
- Verify resource paths in `MainApplication.java`

## Removing This Template

After setup is complete:

1. Delete `TEMPLATE_USAGE.md` (this file)
2. Update `README.md` with your project-specific information
3. The setup script removes itself automatically

## Contributing to the Template

If you make improvements to this template that could benefit others, consider:
- Creating a pull request to the template repository
- Sharing your enhancements with the community

---

Happy coding! 🚀
