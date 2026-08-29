# Release Engineering / Play Store Readiness

This document describes how to build, sign, and publish the Hearthlane app
through the Google Play Store. It assumes the V1 feature set is complete and
frozen.

## Application identity

- **Application ID:** `org.hearthlane`
- **Namespace (internal source package):** `org.hearthlane`
- **App name:** `Hearthlane`
- **Tagline:** Your private way home.
- **Initial release:** `versionName = "1.0.0"`, `versionCode = 1`

The applicationId is the definitive value under the project's own domain.
Changing applicationId after a release is treated by Android as a new app, so
it must not change again after the first public listing.

## Versioning policy

- `versionCode` is a positive integer and must be strictly greater than the
  previous release. Increment by at least 1 for every upload.
- `versionName` follows semantic versioning:
  - `MAJOR.MINOR.PATCH` (for example `1.0.0`, `1.1.0`, `1.0.1`).
  - PATCH increments for bug-fix/hotfix releases.
  - MINOR increments for new features or notable improvements.
  - MAJOR increments for incompatible changes or a rewritten product.
- The first Play Store upload uses `1.0.0` / `1`.

## Signing setup

The project is configured to read release signing credentials from external
properties or environment variables. No keystore, password, or secret is stored
in the repository.

Create the upload keystore once (do not commit it):

```text
keytool -genkey -v \
  -keystore hearthlane-upload.keystore \
  -alias hearthlane \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <store-password> -keypass <key-password>
```

Provide the values at build time. Only one of the two forms is needed.

**Gradle project properties:**

```text
./gradlew -PRELEASE_STORE_FILE=/path/to/hearthlane-upload.keystore \
  -PRELEASE_STORE_PASSWORD=<store-password> \
  -PRELEASE_KEY_ALIAS=hearthlane \
  -PRELEASE_KEY_PASSWORD=<key-password> \
  :app:bundleRelease
```

**Environment variables:**

```text
export RELEASE_STORE_FILE=/path/to/hearthlane-upload.keystore
export RELEASE_STORE_PASSWORD=<store-password>
export RELEASE_KEY_ALIAS=hearthlane
export RELEASE_KEY_PASSWORD=<key-password>
./gradlew :app:bundleRelease
```

Google Play App Signing is the recommended model:

1. Enable Google Play App Signing for the app in Play Console.
2. The local keystore above becomes the **upload key**.
3. Google strips the upload signature and re-signs the AAB with the
   **app signing key** managed by Google.
4. Keep the upload keystore and passwords in a password manager or team secret
   store; losing it blocks future updates.

If the release properties are missing, Gradle fails with an explicit message
pointing back to this document; debug builds continue to work normally.

## Release build commands

Build the debug APK (development / device validation):

```text
./gradlew :app:assembleDebug
```

Run the standard quality gates:

```text
./gradlew test lint
```

Build the release AAB for Play Store:

```text
./gradlew :app:bundleRelease
```

Build a release APK for local installation:

```text
./gradlew :app:assembleRelease
```

Full pre-release gate:

```text
./gradlew clean test lint :app:assembleDebug :app:bundleRelease
```

Expected outputs:

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release.apk`
- Release AAB: `app/build/outputs/bundle/release/app-release.aab`

### Local installation of the release build

An AAB cannot be installed directly with `adb install`. Use one of:

1. **Release APK** (simplest): `./gradlew :app:assembleRelease` produces a
   signed universal APK that can be installed with:

   ```text
   adb install app/build/outputs/apk/release/app-release.apk
   ```

2. **bundletool** (matches Play Store delivery): convert the AAB to a set of
   APKs and install the matching ones:

   ```text
   bundletool build-apks \
     --bundle=app/build/outputs/bundle/release/app-release.aab \
     --output=hearthlane.apks \
     --ks=/path/to/upload.keystore \
     --ks-key-alias=hearthlane
   bundletool install-apks --apks=hearthlane.apks
   ```

The release APK path is the recommended local validation flow because it does
not require an extra tool.

## Native packaging

The embedded Tailscale component is built by gomobile and packaged as
`libgojni.so` for:

- `arm64-v8a`
- `x86_64`

Both ABIs are kept in the AAB. The Play Store serves only the ABI required by
each device. Do not remove an ABI without a concrete device-compatibility
justification.

### 16 KB page size

Modern Android devices (Android 15+) require native libraries to be aligned to
16 KB pages. The gomobile build script passes external-linker flags that force
16 KB ELF segment alignment:

```text
-ldflags="-linkmode=external -extldflags=-Wl,-z,max-page-size=16384"
```

After building the release AAB, verify alignment with:

```text
unzip -p app/build/outputs/bundle/release/app-release.aab base/lib/arm64-v8a/libgojni.so > /tmp/libgojni.so
readelf -l /tmp/libgojni.so | grep LOAD
```

The `Align` column for every `LOAD` segment must be `0x4000` (16 KB). The
current build meets this requirement for both `arm64-v8a` and `x86_64`.

## Release configuration summary

| Setting | Value | Rationale |
|---|---|---|
| `isDebuggable` | `false` | Required for Play Store |
| `isMinifyEnabled` | `false` | R8 not validated against gomobile JNI/reflection |
| `isShrinkResources` | `false` | Requires minifyEnabled |
| Signing | External upload key | Play App Signing model |
| `usesCleartextTraffic` | `true` | V1 uses plain HTTP on LAN and tailnet; documented debt |
| Backup / data extraction | Fully excluded | Tailscale node state must not leave the device |

## Manifest and security audit

Reviewed items for V1:

- `MainActivity` is exported with `MAIN`/`LAUNCHER`; no other components are
  exported.
- Only `INTERNET` permission is declared.
- `usesCleartextTraffic` remains `true` because both the LAN and tailnet
  Frigate origins are plain HTTP in V1. Moving to HTTPS/TLS is tracked as
  technical debt.
- Cloud backup and device transfer are fully excluded via
  `android:allowBackup="false"`, `fullBackupContent`, and
  `dataExtractionRules`. Tailscale state in `filesDir/tailscale` is never
  backed up.
- No long-lived Tailscale auth key, OAuth secret, Frigate password, or camera
  credential is embedded in the APK.
- Release logs no longer print HLS session URLs, enrollment URLs, tokens, or
  credentials. The Go bridge error messages also omit request URLs.

## Play Console checklist

Create or use a Google Play developer account, then create the app.

- [ ] Google Play Developer account active and paid
- [ ] App created in Play Console with application ID `org.hearthlane`
- [ ] Google Play App Signing enabled
- [ ] Upload key (local keystore) registered with Play Console
- [ ] First internal testing release uploaded (AAB)
- [ ] Internal testers added and app accepted
- [ ] Closed testing track configured (if required before production)
- [ ] Production release prepared and submitted

Items that must be filled directly in Play Console at publication time:

- [ ] App name (should match `Hearthlane`; max 50 chars)
- [ ] Short description (max 80 chars)
- [ ] Full description
- [ ] App icon (512 x 512 PNG)
- [ ] Feature graphic (1024 x 500 PNG)
- [ ] Phone screenshots (minimum 2; 16:9 or similar aspect ratio)
- [ ] 7-inch tablet screenshots (if targeting tablets)
- [ ] 10-inch tablet screenshots (if targeting tablets)
- [ ] Privacy policy URL
- [ ] Data safety form
- [ ] Content rating questionnaire
- [ ] Target audience / app category
- [ ] Ads declaration (no ads in V1)
- [ ] App access instructions (no login required beyond administrator setup)
- [ ] News app / health / financial declarations (not applicable for V1)

## Store listing / privacy inventory

The following assets and declarations must be prepared before going to
production. This is an inventory only; marketing content is out of scope for
this engineering task.

- **App name:** `Hearthlane` (must be unique enough in the Play Store)
- **Short description:** One-line summary for store listing
- **Full description:** Paragraph explaining what the app does
- **Icon:** Final adaptive icon (foreground + background + monochrome)
- **Feature graphic:** Top-of-store banner image
- **Screenshots:** Home screen grid, live view, settings/diagnostics (optional)
- **Privacy policy:** Required by Play Console; must explain what data the app
  collects. V1 collects only the configured Frigate URL locally; no analytics,
  no account, no cloud backend.
- **Data safety declaration:** No data collected or shared in V1.
- **Content rating:** Likely "Everyone" or "Teen"; confirm via the
  Play Console questionnaire.
- **Target audience:** Family users with an administrator setting up the app.
- **Ads:** No ads.
- **App access:** Fully functional without a login; the administrator setup
  happens on the device.

## Release compliance checklist

Hearthlane is distributed under GPL-3.0-only. The application bundles
third-party components under their own licenses; the current inventory and the
required notices are documented in `THIRD_PARTY_NOTICES.md`. Follow this
checklist before every release so the distributed binary and the published
source stay in sync.

1. Generate the release from a clean working tree.
2. Confirm `versionCode` and `versionName` before building.
3. Re-audit runtime dependencies whenever dependencies or their versions
   changed since the last release (`./gradlew :app:dependencies
   --configuration releaseRuntimeClasspath` and, for the Go component,
   `go version -m` on the produced `libgojni.so`).
4. Update `THIRD_PARTY_NOTICES.md` when the audit reveals new components,
   version changes, or new license/NOTICE requirements.
5. Commit the `LICENSE`, `THIRD_PARTY_NOTICES.md`, and any documentation
   changes before building the release artifact.
6. Generate the AAB from that final commit.
7. Create the release tag corresponding to the release (for example `v1.0.0`).
8. Ensure the source corresponding to the distributed binary remains
   accessible in the public repository at no charge.
9. Record the commit and/or tag used to produce the AAB (for example in the
   release notes).

## Rollback / update procedure

1. Build the fixed release with a new `versionCode` and appropriate
   `versionName` bump.
2. Sign it with the same upload keystore used for the previous release.
3. Upload the new AAB to Play Console.
4. Promote through the same track (internal / closed / production).
5. To roll back, use Play Console's retained artifact feature or publish a
   higher `versionCode` that reverts the code; Android does not allow
   downgrading `versionCode`.

## Project identity note

Hearthlane is an open-source Android gateway for privately accessing services in
your homelab. The Frigate camera integration is the first implemented service;
future homelab-service integrations (Immich, Nextcloud, Home Assistant,
monitoring, and others) are architectural direction, not committed features.

## Known release blockers / debt

- **Plain HTTP:** `usesCleartextTraffic="true"` is required for V1 because the
  LAN and tailnet Frigate origins are HTTP. Before widening distribution,
  configure Frigate with TLS or place it behind a private reverse proxy and
  remove `usesCleartextTraffic`.
- **R8 / ProGuard:** Disabled. Validate and enable only after device testing
  confirms the gomobile native bridge and any reflection paths remain intact.
- **16 KB page size:** Resolved by adding external-linker flags to the
  gomobile build script; verified on the release AAB for both ABIs.
