#!/usr/bin/env bash
set -euo pipefail

# Enable Spring Boot DevTools via a Maven dev profile in specified modules.
# Only modifies POMs if the dev profile/devtools are not already present.

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

SRC_ROOT="${SRC_ROOT:-"$SCRIPT_DIR/../dts-source"}"
MODULES=(dts-admin dts-platform dts-common)

profile_block_profile_only='    <profile>
      <id>dev</id>
      <activation>
        <activeByDefault>false</activeByDefault>
      </activation>
      <dependencies>
        <dependency>
          <groupId>org.springframework.boot</groupId>
          <artifactId>spring-boot-devtools</artifactId>
          <scope>runtime</scope>
          <optional>true</optional>
        </dependency>
      </dependencies>
    </profile>'

profiles_wrapper_open='  <profiles>'
profiles_wrapper_close='  </profiles>'

for mod in "${MODULES[@]}"; do
  pom="$SRC_ROOT/$mod/pom.xml"
  if [[ ! -f "$pom" ]]; then
    echo "[enable-devtools] WARN: $pom not found; skipping $mod" >&2
    continue
  fi

  if grep -q "spring-boot-devtools" "$pom"; then
    echo "[enable-devtools] $mod: devtools already present; skip"
    continue
  fi

  if grep -q "<id>dev</id>" "$pom"; then
    echo "[enable-devtools] $mod: found dev profile id but devtools not present; injecting dependency into existing <profiles>"
    # Insert devtools dependency into an existing dev profile's <dependencies>
    tmpfile="${pom}.tmp.$$"
    awk '
      BEGIN{in_dev=0; inserted=0}
      {
        if ($0 ~ /<id>dev<\/id>/) { in_dev=1 }
        if (in_dev && $0 ~ /<dependencies>/) {
          print $0
          if (inserted==0) {
            print "        <dependency>"
            print "          <groupId>org.springframework.boot</groupId>"
            print "          <artifactId>spring-boot-devtools</artifactId>"
            print "          <scope>runtime</scope>"
            print "          <optional>true</optional>"
            print "        </dependency>"
            inserted=1
            next
          }
        }
        if (in_dev && $0 ~ /<\/profile>/) {
          if (inserted==0) {
            print "      <dependencies>"
            print "        <dependency>"
            print "          <groupId>org.springframework.boot</groupId>"
            print "          <artifactId>spring-boot-devtools</artifactId>"
            print "          <scope>runtime</scope>"
            print "          <optional>true</optional>"
            print "        </dependency>"
            print "      </dependencies>"
            inserted=1
          }
          in_dev=0
        }
        print $0
      }
    ' "$pom" > "$tmpfile"
    mv "$tmpfile" "$pom"
    echo "[enable-devtools] $mod: injected dependency into existing dev profile"
    continue
  fi

  if grep -q "</profiles>" "$pom"; then
    echo "[enable-devtools] $mod: appending dev profile into existing <profiles>"
    tmpfile="${pom}.tmp.$$"
    awk -v block="$profile_block_profile_only" '
      /<\/profiles>/ && !done { print block; done=1 }
      { print }
    ' "$pom" > "$tmpfile"
    mv "$tmpfile" "$pom"
    echo "[enable-devtools] $mod: appended dev profile"
  else
    echo "[enable-devtools] $mod: creating <profiles> with dev profile"
    tmpfile="${pom}.tmp.$$"
    awk -v open="${profiles_wrapper_open}" -v block="$profile_block_profile_only" -v close="${profiles_wrapper_close}" '
      /<\/project>/ && !done {
        print open
        print block
        print close
        done=1
      }
      { print }
    ' "$pom" > "$tmpfile"
    mv "$tmpfile" "$pom"
    echo "[enable-devtools] $mod: added <profiles> with dev profile"
  fi
done

echo "[enable-devtools] Completed. Activate with Maven profile: -Pdev"
