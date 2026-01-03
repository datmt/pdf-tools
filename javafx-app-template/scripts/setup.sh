#!/bin/bash

set -e

echo "======================================"
echo "  JavaFX Application Template Setup  "
echo "======================================"
echo ""

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to prompt for input with a default value
prompt() {
    local prompt_text="$1"
    local default_value="$2"
    local user_input

    if [ -n "$default_value" ]; then
        read -p "$prompt_text [$default_value]: " user_input
        echo "${user_input:-$default_value}"
    else
        read -p "$prompt_text: " user_input
        echo "$user_input"
    fi
}

# Get project details from user
echo "Please provide the following information:"
echo ""

PROJECT_NAME=$(prompt "Display Name (e.g., 'My Application')" "My Application")
ARTIFACT_ID=$(prompt "Artifact ID (lowercase, hyphens, e.g., 'my-app')" "my-app")
PACKAGE_NAME=$(prompt "Package Name (e.g., 'com.example.myapp')" "com.example.myapp")
GROUP_ID=$(prompt "Group ID (e.g., 'com.example')" "${PACKAGE_NAME%.*}")
DESCRIPTION=$(prompt "Description" "A JavaFX desktop application")
VENDOR=$(prompt "Vendor Name" "$(whoami)")

echo ""
echo -e "${YELLOW}=== Configuration ===${NC}"
echo "Display Name:  $PROJECT_NAME"
echo "Artifact ID:   $ARTIFACT_ID"
echo "Package Name:  $PACKAGE_NAME"
echo "Group ID:      $GROUP_ID"
echo "Description:   $DESCRIPTION"
echo "Vendor:        $VENDOR"
echo ""

read -p "Continue with these settings? (y/n) " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Setup cancelled."
    exit 1
fi

echo ""
echo -e "${GREEN}Starting template setup...${NC}"
echo ""

# Convert package name to path (e.g., com.example.myapp -> com/example/myapp)
PACKAGE_PATH="${PACKAGE_NAME//./\/}"

# Step 1: Replace placeholders in all files
echo "[1/5] Replacing placeholders in files..."
find . -type f \( -name "*.xml" -o -name "*.yml" -o -name "*.java" -o -name "*.fxml" -o -name "*.md" -o -name "*.sh" \) -not -path "*/scripts/*" -exec sed -i.bak \
    -e "s|__PROJECT_NAME__|$PROJECT_NAME|g" \
    -e "s|__ARTIFACT_ID__|$ARTIFACT_ID|g" \
    -e "s|__PACKAGE_NAME__|$PACKAGE_NAME|g" \
    -e "s|__GROUP_ID__|$GROUP_ID|g" \
    -e "s|__DESCRIPTION__|$DESCRIPTION|g" \
    -e "s|__VENDOR__|$VENDOR|g" \
    -e "s|__PACKAGE_PATH__|$PACKAGE_PATH|g" \
    {} \;

# Remove backup files created by sed
find . -name "*.bak" -delete

# Step 2: Rename package directories
echo "[2/5] Creating package directory structure..."

# Create new package structure
mkdir -p "src/main/java/$PACKAGE_PATH/ui"
mkdir -p "src/main/resources/$PACKAGE_PATH/ui"
mkdir -p "src/main/resources/$PACKAGE_PATH/icons"

# Move files to new package structure
if [ -d "src/main/java/__PACKAGE_PATH__" ]; then
    mv src/main/java/__PACKAGE_PATH__/*.java "src/main/java/$PACKAGE_PATH/" 2>/dev/null || true
    mv src/main/java/__PACKAGE_PATH__/ui/*.java "src/main/java/$PACKAGE_PATH/ui/" 2>/dev/null || true
    rm -rf src/main/java/__PACKAGE_PATH__
fi

if [ -d "src/main/resources/__PACKAGE_PATH__" ]; then
    mv src/main/resources/__PACKAGE_PATH__/ui/*.fxml "src/main/resources/$PACKAGE_PATH/ui/" 2>/dev/null || true
    rm -rf src/main/resources/__PACKAGE_PATH__
fi

# Step 3: Create placeholder icons
echo "[3/5] Creating placeholder icons..."

# Check if ImageMagick is available
if command -v convert &> /dev/null; then
    # Create a simple placeholder icon
    convert -size 256x256 xc:'#4A90E2' -fill white -gravity center \
            -pointsize 72 -annotate 0 "${PROJECT_NAME:0:3}" \
            "src/main/resources/$PACKAGE_PATH/icons/app-icon.png" 2>/dev/null || \
    echo -e "${YELLOW}Warning: Could not create icon with ImageMagick${NC}"

    convert -size 64x64 xc:'#4A90E2' -fill white -gravity center \
            -pointsize 24 -annotate 0 "${PROJECT_NAME:0:3}" \
            "src/main/resources/$PACKAGE_PATH/icons/app-icon-64.png" 2>/dev/null || true

    convert -size 32x32 xc:'#4A90E2' -fill white -gravity center \
            -pointsize 12 -annotate 0 "${PROJECT_NAME:0:3}" \
            "src/main/resources/$PACKAGE_PATH/icons/app-icon-32.png" 2>/dev/null || true
else
    echo -e "${YELLOW}ImageMagick not found. Please add your own icons to:${NC}"
    echo "  src/main/resources/$PACKAGE_PATH/icons/app-icon.png"
    echo "  src/main/resources/$PACKAGE_PATH/icons/app-icon-64.png (optional)"
    echo "  src/main/resources/$PACKAGE_PATH/icons/app-icon-32.png (optional)"
fi

# Step 4: Create logback configuration
echo "[4/5] Creating logback configuration..."
mkdir -p src/main/resources
cat > src/main/resources/logback.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/application.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/application-%d{yyyy-MM-dd}-%i.log</fileNamePattern>
            <timeBasedFileNamingAndTriggeringPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP">
                <maxFileSize>10MB</maxFileSize>
            </timeBasedFileNamingAndTriggeringPolicy>
            <maxHistory>7</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
EOF

# Step 5: Create .gitignore
echo "[5/5] Creating .gitignore..."
cat > .gitignore << 'EOF'
# Maven
target/
pom.xml.tag
pom.xml.releaseBackup
pom.xml.versionsBackup
pom.xml.next
release.properties
dependency-reduced-pom.xml
buildNumber.properties
.mvn/timing.properties

# IntelliJ IDEA
.idea/
*.iml
*.iws
*.ipr
out/

# Eclipse
.classpath
.project
.settings/
bin/

# NetBeans
nbproject/
nbbuild/
dist/
nbdist/
.nb-gradle/

# VS Code
.vscode/

# macOS
.DS_Store

# Logs
logs/
*.log

# Temporary files
*.tmp
*.bak
*~
EOF

echo ""
echo -e "${GREEN}=== Setup Complete! ===${NC}"
echo ""
echo "Next steps:"
echo ""
echo "1. Review the generated files"
echo "2. Replace placeholder icons in: src/main/resources/$PACKAGE_PATH/icons/"
echo "3. Build and test locally:"
echo "   ${YELLOW}mvn clean package${NC}"
echo "   ${YELLOW}mvn javafx:run${NC}"
echo ""
echo "4. Initialize git repository (if not already done):"
echo "   ${YELLOW}git init${NC}"
echo "   ${YELLOW}git add .${NC}"
echo "   ${YELLOW}git commit -m 'Initial commit from template'${NC}"
echo ""
echo "5. Configure GitHub secrets for R2 deployment (optional):"
echo "   - R2_ACCESS_KEY_ID"
echo "   - R2_SECRET_ACCESS_KEY"
echo "   - R2_ACCOUNT_ID"
echo "   - R2_BUCKET_NAME"
echo "   - Create 'release' environment in GitHub"
echo ""
echo "6. Create your first release:"
echo "   ${YELLOW}./tag-n-build.sh${NC}"
echo ""
echo -e "${GREEN}Happy coding!${NC}"
echo ""

# Remove the setup script itself
echo "Removing setup script..."
rm -f scripts/setup.sh
