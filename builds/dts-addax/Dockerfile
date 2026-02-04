# Addax ETL engine — downloads pre-built release (pure Java, platform-independent).
# No platform-specific directives — runs natively on ARM64 or AMD64.

FROM eclipse-temurin:17-jre-jammy

LABEL maintainer="DTS Build"

ARG ADDAX_VERSION=6.0.8
ARG ADDAX_RELEASE_URL=https://github.com/wgzhao/Addax/releases/download/${ADDAX_VERSION}/addax-${ADDAX_VERSION}.tar.gz

ADD ${ADDAX_RELEASE_URL} /tmp/addax.tar.gz

RUN tar xzf /tmp/addax.tar.gz -C /opt/ --no-same-permissions --no-same-owner \
 && mv /opt/addax-${ADDAX_VERSION} /opt/addax \
 && rm /tmp/addax.tar.gz \
 && chmod +x /opt/addax/bin/*.sh

WORKDIR /opt/addax

ENTRYPOINT ["sh", "-c"]
CMD ["/opt/addax/bin/addax.sh"]
