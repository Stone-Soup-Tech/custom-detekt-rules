#!/usr/bin/env bash
set -euo pipefail

script_directory=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
script="$script_directory/update_version.sh"
test_directory=$(mktemp -d)
trap 'rm -rf "$test_directory"' EXIT
version_file="$test_directory/gradle.properties"
checks=0

assert_bump() {
  local current=$1 level=$2 auto=$3 expected=$4 actual
  printf '# comment\nkotlin.code.style=official\nprojectVersion=%s\nother=value\n' "$current" > "$version_file"
  actual=$(bash "$script" "$level" "$auto" "$version_file" 2> "$test_directory/stderr")
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: $current $level $auto: expected $expected, got $actual" >&2
    exit 1
  fi
  printf '# comment\nkotlin.code.style=official\nprojectVersion=%s\nother=value\n' "$expected" > "$test_directory/expected"
  diff -u "$test_directory/expected" "$version_file"
  checks=$((checks + 1))
}

assert_bump 0.1.0 patch true 0.1.1
assert_bump 1.0.9 patch true 1.1.0
assert_bump 1.9.9 patch true 2.0.0
assert_bump 1.9.3 minor true 2.0.0
assert_bump 1.2.9 minor true 1.3.0
assert_bump 1.9.9 major true 2.0.0
assert_bump 1.9.9 patch false 1.9.10
assert_bump 1.9.9 minor false 1.10.0
assert_bump 1.9.9 major false 2.0.0
assert_bump 1.0.19 patch true 1.1.0
assert_bump 0.0.0 patch false 0.0.1
assert_bump 1.2.3 NONE true 1.2.3
assert_bump 1.2.3 none false 1.2.3

# NONE must leave even a file without a trailing newline untouched.
printf 'projectVersion=0.1.0' > "$version_file"
cp "$version_file" "$test_directory/before"
bash "$script" NONE true "$version_file" > /dev/null 2>&1
cmp "$version_file" "$test_directory/before"
checks=$((checks + 1))

for invalid in '1.2' 'v1.2.3' '01.2.3' '1.2.3-beta' '1.2.3+abc' '1.2.3+4' '9999999999999999999.0.0'; do
  printf 'projectVersion=%s\n' "$invalid" > "$version_file"
  cp "$version_file" "$test_directory/before"
  if bash "$script" patch true "$version_file" > /dev/null 2>&1; then
    echo "FAIL: accepted invalid version $invalid" >&2
    exit 1
  fi
  cmp "$version_file" "$test_directory/before"
  checks=$((checks + 1))
done

for content in 'other=value' $'projectVersion=1.2.3\nprojectVersion=2.0.0'; do
  printf '%s\n' "$content" > "$version_file"
  cp "$version_file" "$test_directory/before"
  if bash "$script" patch true "$version_file" > /dev/null 2>&1; then
    echo "FAIL: accepted missing or duplicate version property" >&2
    exit 1
  fi
  cmp "$version_file" "$test_directory/before"
  checks=$((checks + 1))
done

printf 'projectVersion=1.2.3\n' > "$version_file"
cp "$version_file" "$test_directory/before"
for args in 'unknown true' 'build true' 'patch invalid' 'patch'; do
  read -r -a arguments <<< "$args"
  if bash "$script" "${arguments[@]}" "$version_file" > /dev/null 2>&1; then
    echo "FAIL: accepted invalid arguments $args" >&2
    exit 1
  fi
  cmp "$version_file" "$test_directory/before"
  checks=$((checks + 1))
done

echo "$checks version checks passed."
