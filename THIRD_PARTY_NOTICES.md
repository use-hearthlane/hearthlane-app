# Third-Party Notices

Hearthlane 1.0.0

Hearthlane is licensed under the GNU General Public License, version 3
(GPL-3.0-only). The complete license text is available in the
[LICENSE](LICENSE) file at the root of this repository.

This document lists the third-party components that are actually distributed
with the Hearthlane 1.0.0 application (APK/AAB): Android/JVM libraries packaged
in the APK/AAB and Go components embedded in the native library `libgojni.so`.
License texts are reproduced verbatim from each component's LICENSE, COPYING or
PATENTS file as shipped by the upstream project. No notice or copyright line is
invented here.

Components used only for building or testing the application (build tools,
test-only dependencies, Gradle plugins, the Go toolchain, the Android NDK/SDK)
are not distributed with the application and are not listed.

## Android / JVM dependencies

All components listed in this section are licensed under the Apache License,
Version 2.0 (Apache-2.0). The full Apache-2.0 text is reproduced once in
[License texts](#license-texts) and applies to every component listed here.
None of these components ships a NOTICE file, so no additional NOTICE text is
required to be reproduced. The license was verified in the POM metadata of each
artifact (Maven Central / Google Maven).

Versions below are the versions resolved in the release runtime classpath.

### AndroidX platform and core libraries

- androidx.annotation:annotation 1.9.1, annotation-jvm 1.9.1, annotation-experimental 1.4.1
- androidx.collection:collection 1.5.0, collection-jvm 1.5.0, collection-ktx 1.5.0
- androidx.arch.core:core-common 2.2.0, core-runtime 2.2.0
- androidx.core:core 1.18.0, core-ktx 1.18.0, core-viewtree 1.0.0
- androidx.activity:activity 1.13.0, activity-ktx 1.13.0, activity-compose 1.13.0
- androidx.lifecycle:lifecycle-common 2.9.4, lifecycle-common-jvm 2.9.4,
  lifecycle-common-java8 2.9.4, lifecycle-runtime 2.9.4, lifecycle-runtime-android 2.9.4,
  lifecycle-runtime-ktx 2.9.4, lifecycle-runtime-ktx-android 2.9.4,
  lifecycle-runtime-compose 2.9.4, lifecycle-runtime-compose-android 2.9.4,
  lifecycle-livedata 2.9.4, lifecycle-livedata-core 2.9.4,
  lifecycle-livedata-core-ktx 2.9.4, lifecycle-viewmodel 2.9.4,
  lifecycle-viewmodel-ktx 2.9.4, lifecycle-viewmodel-android 2.9.4,
  lifecycle-viewmodel-savedstate 2.9.4, lifecycle-viewmodel-savedstate-android 2.9.4,
  lifecycle-process 2.9.4
- androidx.savedstate:savedstate 1.3.3, savedstate-android 1.3.3, savedstate-ktx 1.3.3,
  savedstate-compose 1.3.3, savedstate-compose-android 1.3.3
- androidx.startup:startup-runtime 1.1.1
- androidx.tracing:tracing 1.2.0
- androidx.profileinstaller:profileinstaller 1.4.1
- androidx.emoji2:emoji2 1.4.0
- androidx.window:window 1.5.0, window-core 1.5.0, window-core-android 1.5.0
- androidx.appcompat:appcompat-resources 1.7.0
- androidx.autofill:autofill 1.0.0
- androidx.concurrent:concurrent-futures 1.1.0
- androidx.customview:customview 1.0.0, customview-poolingcontainer 1.0.0
- androidx.documentfile:documentfile 1.0.0
- androidx.dynamicanimation:dynamicanimation 1.0.0
- androidx.exifinterface:exifinterface 1.3.7
- androidx.graphics:graphics-path 1.0.1
- androidx.interpolator:interpolator 1.0.0
- androidx.legacy:legacy-support-core-utils 1.0.0
- androidx.loader:loader 1.0.0
- androidx.localbroadcastmanager:localbroadcastmanager 1.0.0
- androidx.print:print 1.0.0
- androidx.recyclerview:recyclerview 1.3.0
- androidx.transition:transition 1.6.0
- androidx.vectordrawable:vectordrawable 1.1.0, vectordrawable-animated 1.1.0
- androidx.versionedparcelable:versionedparcelable 1.1.1

### AndroidX Compose

- androidx.compose.runtime:runtime 1.11.4, runtime-android 1.11.4,
  runtime-saveable 1.11.4, runtime-saveable-android 1.11.4,
  runtime-retain 1.11.4, runtime-retain-android 1.11.4,
  runtime-annotation 1.11.4, runtime-annotation-android 1.11.4
- androidx.compose.ui:ui 1.11.4, ui-android 1.11.4, ui-geometry 1.11.4,
  ui-geometry-android 1.11.4, ui-graphics 1.11.4, ui-graphics-android 1.11.4,
  ui-text 1.11.4, ui-text-android 1.11.4, ui-unit 1.11.4, ui-unit-android 1.11.4,
  ui-util 1.11.4, ui-util-android 1.11.4
- androidx.compose.foundation:foundation 1.11.4, foundation-android 1.11.4,
  foundation-layout 1.11.4, foundation-layout-android 1.11.4
- androidx.compose.animation:animation 1.11.4, animation-android 1.11.4,
  animation-core 1.11.4, animation-core-android 1.11.4
- androidx.compose.material3:material3 1.4.0, material3-android 1.4.0
- androidx.compose.material:material-icons-core 1.7.8, material-icons-core-android 1.7.8,
  material-icons-extended 1.7.8, material-icons-extended-android 1.7.8,
  material-ripple 1.11.4, material-ripple-android 1.11.4
- androidx.navigationevent:navigationevent 1.0.0, navigationevent-android 1.0.0,
  navigationevent-compose 1.0.0, navigationevent-compose-android 1.0.0

### AndroidX Media3

- androidx.media3:media3-common 1.9.1, media3-container 1.9.1, media3-database 1.9.1,
  media3-datasource 1.9.1, media3-decoder 1.9.1, media3-exoplayer 1.9.1,
  media3-exoplayer-hls 1.9.1, media3-extractor 1.9.1, media3-ui 1.9.1

### AndroidX DataStore

- androidx.datastore:datastore 1.1.7, datastore-android 1.1.7,
  datastore-core 1.1.7, datastore-core-android 1.1.7,
  datastore-core-okio 1.1.7, datastore-core-okio-jvm 1.1.7,
  datastore-preferences 1.1.7, datastore-preferences-android 1.1.7,
  datastore-preferences-core 1.1.7, datastore-preferences-core-android 1.1.7,
  datastore-preferences-proto 1.1.7, datastore-preferences-external-protobuf 1.1.7

### JetBrains Kotlin and Kotlinx libraries

- org.jetbrains.kotlin:kotlin-stdlib 2.2.21, kotlin-stdlib-common 2.2.21,
  kotlin-parcelize-runtime 1.9.22, kotlin-android-extensions-runtime 1.9.22
- org.jetbrains.kotlinx:kotlinx-coroutines-core 1.10.2, kotlinx-coroutines-core-jvm 1.10.2,
  kotlinx-coroutines-android 1.10.2, kotlinx-serialization-core 1.7.3,
  kotlinx-serialization-core-jvm 1.7.3, atomicfu 0.23.2, atomicfu-jvm 0.23.2
- org.jetbrains:annotations 23.0.0

### JetBrains Compose Multiplatform (transitive via Coil)

- org.jetbrains.compose.animation:animation 1.7.3, animation-core 1.7.3
- org.jetbrains.compose.annotation-internal:annotation 1.7.3
- org.jetbrains.compose.collection-internal:collection 1.7.3
- org.jetbrains.compose.foundation:foundation 1.7.3, foundation-layout 1.7.3
- org.jetbrains.compose.runtime:runtime 1.9.2, runtime-saveable 1.9.2
- org.jetbrains.compose.ui:ui 1.7.3, ui-geometry 1.7.3, ui-graphics 1.7.3,
  ui-text 1.7.3, ui-unit 1.7.3, ui-util 1.7.3
- org.jetbrains.androidx.lifecycle:lifecycle-common 2.9.5, lifecycle-runtime 2.9.5,
  lifecycle-runtime-compose 2.9.5, lifecycle-viewmodel 2.8.4
- org.jetbrains.androidx.savedstate:savedstate 1.3.5, savedstate-compose 1.3.5

### Coil 3

- io.coil-kt.coil3:coil 3.1.0, coil-android 3.1.0, coil-core 3.1.0,
  coil-core-android 3.1.0, coil-compose 3.1.0, coil-compose-android 3.1.0,
  coil-compose-core 3.1.0, coil-compose-core-android 3.1.0

### Okio

- com.squareup.okio:okio 3.10.2, okio-jvm 3.10.2

### Google Guava

- com.google.guava:guava 33.3.1-android, failureaccess 1.0.2,
  listenablefuture 9999.0-empty-to-avoid-conflict-with-guava (empty
  compatibility artifact, no classes)

### Accompanist

- com.google.accompanist:accompanist-drawablepainter 0.36.0

### JSpecify

- org.jspecify:jspecify 1.0.0

### Not listed

The BOMs that participate in dependency resolution
(`androidx.compose:compose-bom`, `org.jetbrains.kotlin:kotlin-bom`,
`org.jetbrains.kotlinx:kotlinx-coroutines-bom`, `kotlinx-serialization-bom`)
are metadata-only artifacts and contribute no classes or resources to the
APK/AAB.

## Embedded Go dependencies

The following Go modules are compiled into the native library `libgojni.so`.
The list was verified against the build info embedded in the release binary
(`go version -m`); it includes only modules actually linked into the Android
artifact. Copyright lines below are taken verbatim from each module's LICENSE
file in the Go module cache used for the release build.

| Module | Version | License | Copyright notice in LICENSE |
|---|---|---|---|
| filippo.io/edwards25519 | v1.2.0 | BSD-3-Clause | Copyright (c) 2009 The Go Authors. All rights reserved. |
| github.com/creachadair/msync | v0.8.1 | Custom permissive (3 clauses) | Copyright (C) 2022, Michael J. Fromberger. All Rights Reserved. |
| github.com/fxamacker/cbor/v2 | v2.9.0 | MIT | Copyright (c) 2019-present Faye Amacker |
| github.com/gaissmai/bart | v0.26.1 | MIT | Copyright (c) 2024 Karl Gaissmaier |
| github.com/go-json-experiment/json | v0.0.0-20260214004413-d219187c3433 | BSD-3-Clause | Copyright (c) 2020 The Go Authors. All rights reserved. |
| github.com/golang/groupcache | v0.0.0-20241129210726-2c02b8208cf8 | Apache-2.0 | — (plain Apache-2.0 text) |
| github.com/google/btree | v1.1.3 | Apache-2.0 | — (plain Apache-2.0 text) |
| github.com/hdevalence/ed25519consensus | v0.2.0 | BSD-3-Clause | Copyright (c) 2009 The Go Authors. All rights reserved. Copyright (c) 2020 Henry de Valence. All rights reserved. |
| github.com/huin/goupnp | v1.3.0 | BSD-2-Clause | Copyright (c) 2013, John Beisley <johnbeisleyuk@gmail.com>. All rights reserved. |
| github.com/klauspost/compress | v1.19.1 | BSD-3-Clause | Copyright (c) 2012 The Go Authors. All rights reserved. Copyright (c) 2019 Klaus Post. All rights reserved. |
| github.com/mdlayher/socket | v0.5.0 | MIT | Copyright (C) 2021 Matt Layher |
| github.com/miekg/dns | v1.1.58 | BSD-3-Clause | Copyright (c) 2009, The Go Authors. Extensions copyright (c) 2011, Miek Gieben. All rights reserved. |
| github.com/pires/go-proxyproto | v0.8.1 | Apache-2.0 | — (plain Apache-2.0 text) |
| github.com/tailscale/peercred | v0.0.0-20250107143737-35a0c7bd7edc | BSD-3-Clause | Copyright (c) 2021, Tailscale Inc. All rights reserved. |
| github.com/tailscale/wireguard-go | v0.0.0-20260715223240-2e01ba5b00f0 | MIT | MIT text in LICENSE has no copyright line; the source files carry: Copyright (C) 2017-2023 WireGuard LLC. All Rights Reserved. |
| github.com/x448/float16 | v0.8.4 | MIT | Copyright (c) 2019 Montgomery Edwards⁴⁴⁸ and Faye Amacker |
| go4.org/mem | v0.0.0-20240501181205-ae6ca9944745 | Apache-2.0 | — (plain Apache-2.0 text) |
| go4.org/netipx | v0.0.0-20231129151722-fdeea329fbba | BSD-3-Clause | Copyright (c) 2020 The Inet.af AUTHORS. All rights reserved. |
| golang.org/x/crypto | v0.54.0 | BSD-3-Clause | Copyright 2009 The Go Authors. |
| golang.org/x/exp | v0.0.0-20260410095643-746e56fc9e2f | BSD-3-Clause | Copyright 2009 The Go Authors. |
| golang.org/x/mobile | v0.0.0-20260803200217-62cee1672c8e | BSD-3-Clause | Copyright 2009 The Go Authors. |
| golang.org/x/net | v0.57.0 | BSD-3-Clause | Copyright 2009 The Go Authors. |
| golang.org/x/oauth2 | v0.36.0 | BSD-3-Clause | Copyright 2009 The Go Authors. |
| golang.org/x/sync | v0.22.0 | BSD-3-Clause | Copyright 2009 The Go Authors. |
| golang.org/x/sys | v0.47.0 | BSD-3-Clause | Copyright 2009 The Go Authors. |
| golang.org/x/term | v0.45.0 | BSD-3-Clause | Copyright 2009 The Go Authors. |
| golang.org/x/text | v0.40.0 | BSD-3-Clause | Copyright 2009 The Go Authors. |
| golang.org/x/time | v0.15.0 | BSD-3-Clause | Copyright 2009 The Go Authors. |
| gvisor.dev/gvisor | v0.0.0-20260224225140-573d5e7127a8 | Apache-2.0 | — (plain Apache-2.0 text) |
| tailscale.com | v1.102.2 | BSD-3-Clause | Copyright (c) 2020 Tailscale Inc & contributors. |

Notes:

- The MIT license texts of `github.com/fxamacker/cbor/v2`, `github.com/gaissmai/bart`,
  `github.com/x448/float16` and `github.com/mdlayher/socket` are identical in body;
  the per-module copyright lines above are preserved. The MIT text of
  `github.com/tailscale/wireguard-go` is reproduced verbatim from its LICENSE
  below; its copyright notice is taken from the source-file headers because the
  LICENSE file itself carries none.
- `github.com/creachadair/msync` uses a custom three-clause permissive text that
  is neither a standard MIT nor a standard BSD text; it is reproduced verbatim
  below without reclassification.
- The Apache-2.0 text reproduced in [License texts](#license-texts) also covers
  the Apache-2.0 Go components (`github.com/golang/groupcache`,
  `github.com/google/btree`, `github.com/pires/go-proxyproto`, `go4.org/mem`,
  `gvisor.dev/gvisor`).

## License texts

### Apache License 2.0

The following text is reproduced verbatim from the LICENSE file of
`go4.org/mem` (Apache License, Version 2.0) and applies to all Apache-2.0
components listed in this document (all Android/JVM components, plus
`github.com/golang/groupcache`, `github.com/google/btree`,
`github.com/pires/go-proxyproto`, `go4.org/mem`, `gvisor.dev/gvisor`).

```
                                 Apache License
                           Version 2.0, January 2004
                        http://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "{}"
      replaced with your own identifying information. (Don't include
      the brackets!)  The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright {yyyy} {name of copyright owner}

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```

### BSD 3-Clause (Go, "Google Inc.")

The following text is reproduced verbatim from the LICENSE files of
`filippo.io/edwards25519` and applies to the BSD-3-Clause Go components that
use the same "Neither the name of Google Inc." wording:
`github.com/go-json-experiment/json`, `github.com/hdevalence/ed25519consensus`
and `github.com/klauspost/compress` (main license; the s2/snappy/gzhttp
sub-licenses of `klauspost/compress` cover code that is not linked into
`libgojni.so`). The copyright lines of each component are preserved in the
table above.

```
Copyright (c) 2009 The Go Authors. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google Inc. nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### BSD 3-Clause (Go, "Google LLC")

The following text is reproduced verbatim from the LICENSE files of the
`golang.org/x/*` modules and applies to `golang.org/x/crypto`, `x/exp`,
`x/mobile`, `x/net`, `x/oauth2`, `x/sync`, `x/sys`, `x/term`, `x/text` and
`x/time`, whose LICENSE files are identical (Copyright 2009 The Go Authors).

```
Copyright 2009 The Go Authors.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   * Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
   * Redistributions in binary form must reproduce the above
copyright notice, this list of conditions and the following disclaimer
in the documentation and/or other materials provided with the
distribution.
   * Neither the name of Google LLC nor the names of its
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### BSD 3-Clause ("Neither the name of the copyright holder")

The following text is reproduced verbatim from the LICENSE files of
`tailscale.com`, `github.com/tailscale/peercred` and `github.com/miekg/dns`,
which share the same license body. The per-component copyright lines are
preserved in the table above.

```
BSD 3-Clause License

Copyright (c) 2020 Tailscale Inc & contributors.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its
   contributors may be used to endorse or promote products derived from
   this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### BSD 2-Clause

The following text is reproduced verbatim from the LICENSE file of
`github.com/huin/goupnp`.

```
Copyright (c) 2013, John Beisley <johnbeisleyuk@gmail.com>
All rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice, this
  list of conditions and the following disclaimer in the documentation and/or
  other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

### MIT License

The following MIT text is reproduced verbatim from the LICENSE files of
`github.com/fxamacker/cbor/v2`, `github.com/gaissmai/bart`,
`github.com/x448/float16` and `github.com/mdlayher/socket`, whose license
bodies are identical. The per-module copyright lines are preserved in the
table above.

```
MIT License

Copyright (c) 2019-present Faye Amacker

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The MIT text of `github.com/tailscale/wireguard-go` is reproduced verbatim
from its LICENSE file. Because that file carries no copyright line, the
copyright notice below is taken from the source-file headers
(`Copyright (C) 2017-2023 WireGuard LLC. All Rights Reserved.`).

```
Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
of the Software, and to permit persons to whom the Software is furnished to do
so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

### creachadair/msync custom license

The following text is reproduced verbatim from the LICENSE file of
`github.com/creachadair/msync`. It is a three-clause permissive text with
custom wording and is intentionally not classified as a standard MIT or BSD
license.

```
Copyright (C) 2022, Michael J. Fromberger
All Rights Reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    (1) Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

    (2) Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.

    (3) The name of the author may not be used to endorse or promote products
    derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY
OF SUCH DAMAGE.
```

## Patent notices

The following patent grants are part of the distribution of the respective
components and are reproduced verbatim from each component's PATENTS file.
They grant additional patent rights to recipients and accompany the
BSD-3-Clause license of those components. None of the other distributed
components ships a PATENTS file or a NOTICE file, and no upstream NOTICE is
invented here.

### Tailscale patent grant

Reproduced verbatim from the PATENTS file of `tailscale.com`.

```
Additional IP Rights Grant (Patents)

"This implementation" means the copyrightable works distributed by
Tailscale Inc. as part of the Tailscale project.

Tailscale Inc. hereby grants to You a perpetual, worldwide,
non-exclusive, no-charge, royalty-free, irrevocable (except as stated
in this section) patent license to make, have made, use, offer to
sell, sell, import, transfer and otherwise run, modify and propagate
the contents of this implementation of Tailscale, where such license
applies only to those patent claims, both currently owned or
controlled by Tailscale Inc. and acquired in the future, licensable
by Tailscale Inc. that are necessarily infringed by this
implementation of Tailscale.  This grant does not include claims that
would be infringed only as a consequence of further modification of
this implementation.  If you or your agent or exclusive licensee
institute or order or agree to the institution of patent litigation
against any entity (including a cross-claim or counterclaim in a
lawsuit) alleging that this implementation of Tailscale or any code
incorporated within this implementation of Tailscale constitutes
direct or contributory patent infringement, or inducement of patent
infringement, then any patent rights granted to you under this License
for this implementation of Tailscale shall terminate as of the date
such litigation is filed.
```

### Google patent grant

Reproduced verbatim from the PATENTS files of the `golang.org/x/*` modules
(`x/crypto`, `x/exp`, `x/mobile`, `x/net`, `x/sync`, `x/sys`, `x/term`,
`x/text`, `x/time`), whose PATENTS files are identical. `golang.org/x/oauth2`
does not ship a PATENTS file.

```
Additional IP Rights Grant (Patents)

"This implementation" means the copyrightable works distributed by
Google as part of the Go project.

Google hereby grants to You a perpetual, worldwide, non-exclusive,
no-charge, royalty-free, irrevocable (except as stated in this section)
patent license to make, have made, use, offer to sell, sell, import,
transfer and otherwise run, modify and propagate the contents of this
implementation of Go, where such license applies only to those patent
claims, both currently owned or controlled by Google and acquired in
the future, licensable by Google that are necessarily infringed by this
implementation of Go.  This grant does not include claims that would be
infringed only as a consequence of further modification of this
implementation.  If you or your agent or exclusive licensee institute or
order or agree to the institution of patent litigation against any
entity (including a cross-claim or counterclaim in a lawsuit) alleging
that this implementation of Go or any code incorporated within this
implementation of Go constitutes direct or contributory patent
infringement, or inducement of patent infringement, then any patent
rights granted to you under this License for this implementation of Go
shall terminate as of the date such litigation is filed.
```