# Addax ETL engine — downloads pre-built release (pure Java, platform-independent).
# No platform-specific directives — runs natively on ARM64 or AMD64.

# Compile with an LTS JDK while targeting Java 17 bytecode; runtime remains Java 17.
FROM eclipse-temurin:21-jdk AS addax-builder

LABEL maintainer="DTS Build"

ARG ADDAX_VERSION=6.0.8
ARG ADDAX_RELEASE_URL=https://github.com/wgzhao/Addax/releases/download/${ADDAX_VERSION}/addax-${ADDAX_VERSION}.tar.gz
ARG ADDAX_SHA256=52b0c34fd2b0bdc544bbc83e147e731b6b3cccd4befe4784d7ee16805dd54775

ADD ${ADDAX_RELEASE_URL} /tmp/addax.tar.gz

RUN echo "${ADDAX_SHA256}  /tmp/addax.tar.gz" | sha256sum -c - \
 && tar xzf /tmp/addax.tar.gz -C /opt/ --no-same-permissions --no-same-owner \
 && mv /opt/addax-${ADDAX_VERSION} /opt/addax \
 && rm /tmp/addax.tar.gz \
 && chmod +x /opt/addax/bin/*.sh \
 && sed -i 's/if \[ ! -r "${JOB_FILE}" \]/if false/' /opt/addax/bin/addax.sh \
 && sed -i 's/if \[ ! -w "${LOG_DIR}" \]/if false/' /opt/addax/bin/addax.sh \
 && mkdir -p /opt/addax/log && chmod 777 /opt/addax/log

COPY patches/main/com/wgzhao/addax/plugin/reader/excelreader/ExcelHelper.java /tmp/addax-patch/main/com/wgzhao/addax/plugin/reader/excelreader/ExcelHelper.java
COPY patches/test/ExcelHelperSparseCellTest.java /tmp/addax-patch/test/ExcelHelperSparseCellTest.java

RUN mkdir -p /tmp/addax-patch/classes /tmp/addax-patch/test-classes \
 && javac -proc:none --release 17 -encoding UTF-8 \
      -cp "/opt/addax/lib/*:/opt/addax/shared/*:/opt/addax/plugin/reader/excelreader/excelreader-${ADDAX_VERSION}.jar" \
      -d /tmp/addax-patch/classes \
      /tmp/addax-patch/main/com/wgzhao/addax/plugin/reader/excelreader/ExcelHelper.java \
 && javac -proc:none --release 17 -encoding UTF-8 \
      -cp "/tmp/addax-patch/classes:/opt/addax/lib/*:/opt/addax/shared/*:/opt/addax/plugin/reader/excelreader/excelreader-${ADDAX_VERSION}.jar" \
      -d /tmp/addax-patch/test-classes \
      /tmp/addax-patch/test/ExcelHelperSparseCellTest.java \
 && jar --update \
      --file "/opt/addax/plugin/reader/excelreader/excelreader-${ADDAX_VERSION}.jar" \
      -C /tmp/addax-patch/classes \
      com/wgzhao/addax/plugin/reader/excelreader/ExcelHelper.class \
 && java \
      -cp "/tmp/addax-patch/test-classes:/opt/addax/lib/*:/opt/addax/shared/*:/opt/addax/plugin/reader/excelreader/excelreader-${ADDAX_VERSION}.jar" \
      com.yuzhi.dts.addax.patch.ExcelHelperSparseCellTest

FROM eclipse-temurin:17-jre-jammy

ARG ADDAX_RUNNER_JAR=addax-env-runner.jar

COPY --from=addax-builder /opt/addax /opt/addax
COPY ${ADDAX_RUNNER_JAR} /opt/addax/addax-env-runner.jar

WORKDIR /opt/addax

ENTRYPOINT ["/opt/addax/bin/addax.sh"]
