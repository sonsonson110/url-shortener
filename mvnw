#!/bin/sh
# Maven wrapper - downloads Maven if not present
MAVEN_VERSION=3.9.9
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
MAVEN_ZIP="apache-maven-${MAVEN_VERSION}-bin.zip"
MAVEN_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/${MAVEN_ZIP}"

if [ ! -f "$MAVEN_HOME/bin/mvn" ]; then
  mkdir -p "$MAVEN_HOME"
  echo "Downloading Maven ${MAVEN_VERSION}..."
  curl -fsSL "$MAVEN_URL" -o "/tmp/${MAVEN_ZIP}"
  unzip -q "/tmp/${MAVEN_ZIP}" -d "$HOME/.m2/wrapper/dists/"
fi

export JAVA_HOME=/usr/local/android-studio/jbr
exec "$MAVEN_HOME/bin/mvn" "$@"
