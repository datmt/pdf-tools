#!/bin/bash

set -e

# Get the latest version tag
LATEST_TAG=$(git describe --tags --abbrev=0 2>/dev/null || echo "v0.0.0")
echo "Latest tag: $LATEST_TAG"

# Remove 'v' prefix and split into parts
VERSION=${LATEST_TAG#v}
IFS='.' read -r MAJOR MINOR PATCH <<< "$VERSION"

# Increment patch version
NEW_PATCH=$((PATCH + 1))
NEW_TAG="v${MAJOR}.${MINOR}.${NEW_PATCH}"

echo "New tag: $NEW_TAG"

# Create and push the new tag
git tag "$NEW_TAG"
git push origin "$NEW_TAG"

echo "Successfully tagged and pushed $NEW_TAG"
