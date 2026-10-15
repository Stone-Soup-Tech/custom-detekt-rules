#!/usr/bin/env bash
set -euo pipefail

# Usage: update_version.sh major|minor|patch|NONE true|false [gradle.properties]
level_change=${1:-}
auto_increase=${2:-}
version_file=${3:-gradle.properties}

case "$level_change" in
  major|minor|patch|NONE|none) ;;
  *) echo "Error: use major, minor, patch, or NONE." >&2; exit 1 ;;
esac
case "$auto_increase" in
  true|false) ;;
  *) echo "Error: auto-increase must be true or false." >&2; exit 1 ;;
esac

# Extract exactly one projectVersion property.
version_name=$(awk -F= '
  /^projectVersion=/ { count++; value=substr($0, index($0, "=") + 1) }
  END { if (count != 1) exit 1; print value }
' "$version_file") || {
  echo "Error: $version_file must contain exactly one projectVersion= property." >&2
  exit 1
}

version_pattern='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$'
if [[ ! "$version_name" =~ $version_pattern ]]; then
  echo "Error: invalid version '$version_name'; expected MAJOR.MINOR.PATCH." >&2
  exit 1
fi

echo "Current version: $version_name" >&2
if [[ "$level_change" == "NONE" || "$level_change" == "none" ]]; then
  echo "$version_name"
  exit 0
fi

# Extract the three numeric version components.
IFS=. read -r major minor patch <<< "$version_name"

# Keep values within Bash's signed 64-bit arithmetic range, including increments.
for component in "$major" "$minor" "$patch"; do
  if [[ ${#component} -gt 18 ]]; then
    echo "Error: version components must contain at most 18 digits." >&2
    exit 1
  fi
done

# Increment the requested version component.
if [[ "$level_change" == "major" ]]; then
  major=$((major + 1))
  minor=0
  patch=0
elif [[ "$level_change" == "minor" ]]; then
  minor=$((minor + 1))
  patch=0
elif [[ "$level_change" == "patch" ]]; then
  patch=$((patch + 1))
fi

# Optionally carry patch/minor at 10, matching the release version convention.
if [[ "$auto_increase" == "true" ]]; then
  if [[ "$patch" -gt 9 ]]; then
    patch=0
    minor=$((minor + 1))
  fi
  if [[ "$minor" -gt 9 ]]; then
    minor=0
    major=$((major + 1))
  fi
fi

new_version="$major.$minor.$patch"
echo "New version: $new_version" >&2

# Replace only the version property and preserve all other configuration.
# A temporary file works on both macOS and Linux without sed -i differences.
temporary_file=$(mktemp "${version_file}.XXXXXX")
trap 'rm -f "$temporary_file"' EXIT
awk -v version="$new_version" '
  /^projectVersion=/ { print "projectVersion=" version; next }
  { print }
' "$version_file" > "$temporary_file"
mv "$temporary_file" "$version_file"

# Keep stdout machine-readable for the GitHub Actions output.
echo "$new_version"
