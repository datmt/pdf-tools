# Quick Start

Get your JavaFX app running in 3 steps:

## 1. Run Setup

```bash
./scripts/setup.sh
```

You'll be prompted for:
- **Display Name**: "My Application"
- **Artifact ID**: "my-app" (lowercase, hyphens)
- **Package Name**: "com.example.myapp"
- **Group ID**: "com.example" (auto-filled)
- **Description**: "My awesome app"
- **Vendor**: Your name

## 2. Run the App

```bash
mvn javafx:run
```

## 3. Make Your First Release

```bash
# Initialize git (if needed)
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/yourusername/your-repo.git
git push -u origin main

# Create release (auto-increments version)
./tag-n-build.sh
```

That's it! Your app will be built for Linux, macOS, and Windows automatically via GitHub Actions.

## Optional: Add Custom Icons

Replace these files with your own PNG icons:
```
src/main/resources/[your-package-path]/icons/app-icon.png      (256x256+)
src/main/resources/[your-package-path]/icons/app-icon-64.png   (64x64)
src/main/resources/[your-package-path]/icons/app-icon-32.png   (32x32)
```

## Optional: Enable R2 Deployment

Add these secrets to your GitHub repo settings:
- `R2_ACCESS_KEY_ID`
- `R2_SECRET_ACCESS_KEY`
- `R2_ACCOUNT_ID`
- `R2_BUCKET_NAME`

Create a `release` environment in GitHub.

---

For detailed documentation, see:
- `README.md` - Project overview and features
- `TEMPLATE_USAGE.md` - Complete setup and customization guide
