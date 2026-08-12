FROM eclipse-temurin:21-jdk AS java
FROM golang:1.25-bookworm AS go

FROM opencode-base:1

USER root

ARG ANDROID_CMDLINE_TOOLS_VERSION=15859902
ARG ANDROID_NDK_VERSION=27.2.12479018

ENV JAVA_HOME=/opt/java/openjdk
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_NDK_HOME=/opt/android-sdk/ndk/${ANDROID_NDK_VERSION}

ENV PATH="${JAVA_HOME}/bin:${PATH}:/usr/local/go/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin"

COPY --from=java /opt/java/openjdk /opt/java/openjdk
COPY --from=go /usr/local/go /usr/local/go

RUN mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && curl -fsSL \
       "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CMDLINE_TOOLS_VERSION}_latest.zip" \
       -o /tmp/android-commandlinetools.zip \
    && unzip -q /tmp/android-commandlinetools.zip \
       -d /tmp/android-commandlinetools \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools/latest" \
    && mv /tmp/android-commandlinetools/cmdline-tools/* \
       "${ANDROID_HOME}/cmdline-tools/latest/" \
    && rm -rf \
       /tmp/android-commandlinetools \
       /tmp/android-commandlinetools.zip \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager \
       "platform-tools" \
       "platforms;android-36" \
       "build-tools;36.0.0" \
       "ndk;${ANDROID_NDK_VERSION}" \
    && test -x \
       "${ANDROID_NDK_HOME}/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" \
    && chown -R opencode:opencode "${ANDROID_HOME}"

USER opencode

WORKDIR /workspace

CMD ["opencode"]