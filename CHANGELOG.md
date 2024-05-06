# Changelog

## [0.2.0](https://github.com/spotify/confidence-sdk-android/compare/0.1.7...0.2.0) (2024-05-06)


### ⚠ BREAKING CHANGES

* Total Confidence - Eventsender ([#124](https://github.com/spotify/confidence-sdk-android/issues/124))

### 🐛 Bug Fixes

* change STALE reason in case of context change ([#123](https://github.com/spotify/confidence-sdk-android/issues/123)) ([fae882f](https://github.com/spotify/confidence-sdk-android/commit/fae882f433fd451ba889ea0e570c449d7c60fb67))
* ConfidenceValue.List can only contain single type [#129](https://github.com/spotify/confidence-sdk-android/issues/129) ([c532f9f](https://github.com/spotify/confidence-sdk-android/commit/c532f9f0ce6dac00d224e91c44b22ccec43e8c9d))
* Merge context and message together ([#137](https://github.com/spotify/confidence-sdk-android/issues/137)) ([2fc83b2](https://github.com/spotify/confidence-sdk-android/commit/2fc83b2852c4f7a6f6bd89cab5df4c08f6b39e75))
* Remove STALE event ([#122](https://github.com/spotify/confidence-sdk-android/issues/122)) ([019dbfc](https://github.com/spotify/confidence-sdk-android/commit/019dbfc57a09dcd98134ce54247409a3e7b8127d))
* use the local DateSerializer ([#127](https://github.com/spotify/confidence-sdk-android/issues/127)) ([d300b98](https://github.com/spotify/confidence-sdk-android/commit/d300b98c84cb11c1b0b7139a48bd7adb61f3ed40))


### ✨ New Features

* include persistent visitor id in context ([#130](https://github.com/spotify/confidence-sdk-android/issues/130)) ([5d4ff21](https://github.com/spotify/confidence-sdk-android/commit/5d4ff2114c84c21f9a7096217bf2ebce096c7061))
* listen for changes in the provider for the context changes ([#133](https://github.com/spotify/confidence-sdk-android/issues/133)) ([2fca516](https://github.com/spotify/confidence-sdk-android/commit/2fca5161d39cb75cdc4489e77812f6e5e4c04368))
* Stale return value instead of default ([#132](https://github.com/spotify/confidence-sdk-android/issues/132)) ([611200c](https://github.com/spotify/confidence-sdk-android/commit/611200c33b4d7f701a19d509d26c4cd0668e56e0))
* Total Confidence - Eventsender ([#124](https://github.com/spotify/confidence-sdk-android/issues/124)) ([5f1164c](https://github.com/spotify/confidence-sdk-android/commit/5f1164c85b20737453b0d893812105e3d29e447e))
* Track app lifecycle, track activities lifecycle, track deeplink, track install/update ([#131](https://github.com/spotify/confidence-sdk-android/issues/131)) ([8de5d54](https://github.com/spotify/confidence-sdk-android/commit/8de5d54135aad33158a212011956148ba8b94202))


### 📚 Documentation

* Add apply note to readme ([#125](https://github.com/spotify/confidence-sdk-android/issues/125)) ([2bc0bae](https://github.com/spotify/confidence-sdk-android/commit/2bc0bae4c2b6b1919a8029e9f60235a3dd382461))
* Add documentation about `apply` ([#119](https://github.com/spotify/confidence-sdk-android/issues/119)) ([5973467](https://github.com/spotify/confidence-sdk-android/commit/597346777b7534986c7c99ccb669d08688117397))
* Add documentation about apply ([5973467](https://github.com/spotify/confidence-sdk-android/commit/597346777b7534986c7c99ccb669d08688117397))


### 🔄 Refactoring

* Add `message` container to `payload` ([#134](https://github.com/spotify/confidence-sdk-android/issues/134)) ([7ba3954](https://github.com/spotify/confidence-sdk-android/commit/7ba39541d003dd985209a35908351d074adbf4ea))
* rename repository ([#138](https://github.com/spotify/confidence-sdk-android/issues/138)) ([f9cd491](https://github.com/spotify/confidence-sdk-android/commit/f9cd491a74360e41719148d5c65f39dd3c6ff2dc))
* send to track rename ([#135](https://github.com/spotify/confidence-sdk-android/issues/135)) ([a90639d](https://github.com/spotify/confidence-sdk-android/commit/a90639d8d7f7718c1329dbb4489986df9895efcd))

## [0.1.7](https://github.com/spotify/confidence-openfeature-provider-kotlin/compare/0.1.6...0.1.7) (2024-01-16)


### 🐛 Bug Fixes

* Fix sdk metadata format ([#117](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/117)) ([e9ad5d8](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/e9ad5d826874005d0211dbc3d1f6ea1b6c115005))


### 🧹 Chore

* add a default endpoint to the provider ([#115](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/115)) ([dc53f9f](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/dc53f9f5e5c31051e7187ede1bb11099258faa62))


### 📚 Documentation

* Add docs on initialization strategy config ([#116](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/116)) ([ed96ca2](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/ed96ca27157d4bd34dc80e13bc1aff49f7d8f92c))
* Update README ([#113](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/113)) ([605c509](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/605c5095f5d5ddc9981075a80257240974a589ad))

## [0.1.6](https://github.com/spotify/confidence-openfeature-provider-kotlin/compare/0.1.5...0.1.6) (2024-01-04)


### 🐛 Bug Fixes

* correctly handle apply response in event processor ([#111](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/111)) ([84ac3d5](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/84ac3d50ea3a68f8d64491ecadfb7e49f46c0694))

## [0.1.5](https://github.com/spotify/confidence-openfeature-provider-kotlin/compare/0.1.4...0.1.5) (2023-12-19)


### 🧹 Chore

* sonatype - publish and close on release ([#108](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/108)) ([a4c0d41](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/a4c0d4181187ade5722f08e69a23563a68af647c))

## [0.1.4](https://github.com/spotify/confidence-openfeature-provider-kotlin/compare/0.1.3...0.1.4) (2023-12-19)


### 🧹 Chore

* Latest OF SDK ([#109](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/109)) ([4ea71ab](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/4ea71ab5020da055923e3c36773c48ec1c47d04d))


### 📚 Documentation

* Add links to the README ([#103](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/103)) ([12c5b6d](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/12c5b6d3158f9d3a19b7f9ce9e98167e61b15836))

## [0.1.3](https://github.com/spotify/confidence-openfeature-provider-kotlin/compare/0.1.2...0.1.3) (2023-11-14)


### ✨ New Features

* Add SDK id and version to requests ([#102](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/102)) ([70cb206](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/70cb20649d32e715833d4f9de7d0faef6256741d))


### 🧹 Chore

* bump coroutines patch ([#100](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/100)) ([ce7cabb](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/ce7cabb1d7d672d2ff6e68248abdbd53d308e61b))
* bump kotlinx-serialization to 1.6.0 ([#101](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/101)) ([432af3e](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/432af3eb431db6272886764d55dd8d3acff4c20f))
* update to consume open-feature sdk from maven-central ([#99](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/99)) ([8a1ece8](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/8a1ece8ba4aff7a189f4d10cc38fd279d1d67b27))


### 📚 Documentation

* update the readme with correct maven central group id ([#94](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/94)) ([2db5abf](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/2db5abf7d60559c8a5b8da07ea970ba08784e90d))

## [0.1.2](https://github.com/spotify/confidence-openfeature-provider-kotlin/compare/v0.1.2...0.1.2) (2023-10-12)


### 🐛 Bug Fixes

* add pom declaration to maven publication ([#81](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/81)) ([34e2f63](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/34e2f6366a0932f4a0e5cc3d45aaa5a5f92144d9))
* do not fail network deserialization on unknown fields ([#79](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/79)) ([a939aac](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/a939aac4d18ea296423829e1f6a914ec15982bf6))
* Move apply cache into disk storage ([#80](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/80)) ([5b67fd8](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/5b67fd86cc7a1accf36cc001a23330368de9f0bd))
* On context change ([#69](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/69)) ([0904cd6](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/0904cd6205312cab822b0eb8ecc39e64923a534e))
* stop minification ([#87](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/87)) ([2b141d3](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/2b141d39dc859bbb92b61aabcbbc9f6e3125ce2a))


### ✨ New Features

* create release please manifest and config file and ci workflow ([#59](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/59)) ([e8df118](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/e8df118143c904e17d8b95c95f86ae62a920ed00))


### 🧹 Chore

* artifact update android instead of kotlin ([#73](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/73)) ([b41f750](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/b41f750e4ad91823e8d3399a450c19ce1c2a841e))
* Bump gradle wrapper to 8.2 ([6720bb8](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/6720bb8ee35a3572a0ec57312765a855b01cceae))
* **docs:** update readme to include the logic about context change ([#71](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/71)) ([b266dd5](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/b266dd5d0ab3f128ccc9b6bc15a53af56a4ce51e))
* **main:** release 0.1.1 ([#64](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/64)) ([5837aa7](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/5837aa730aff7983f238a247f77f9dbff2eb59fa))
* **main:** release 0.1.1 ([#83](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/83)) ([3c09925](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/3c09925018d8b13e512c988392de0e85d7b9c5da))
* **main:** release 0.1.1 ([#86](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/86)) ([1354d91](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/1354d9155147e5f5be23ecc686e1809e49094a01))
* **main:** release 0.1.2 ([#88](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/88)) ([be1ce32](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/be1ce3222e38209bf0f0cdb218b2d4917948556e))
* **main:** release 0.1.2 ([#91](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/91)) ([50b72c8](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/50b72c87a253cee76adae09446b512bc3c7cc3f6))
* **main:** release 0.1.2 ([#92](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/92)) ([530f5f0](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/530f5f0ba3c573fde4b7eafc83a8921c96bc2121))
* manually release 0.1.1 ([305e7fa](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/305e7fa641b7624a4a50e4c38477e6e8020679ac))
* manually release 0.1.2 ([28c9b78](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/28c9b78e211fcdf167d0d8d542d100ee10d740fd))
* only Provider as part of release-please release ([#65](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/65)) ([5102f46](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/5102f46066f68ef5f4894f5e21fbbb4ded8baf1c))
* rename package structure and artifact id ([#72](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/72)) ([421782a](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/421782a2b5c62b15a8b29e38e1ad29bac035b4a8))
* update to use openfeature sdk dependency ([#56](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/56)) ([856f40f](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/856f40f796ec6cbba7ee477c3d54aa40333d5026))
* update with release please markers ([#70](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/70)) ([079c1cb](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/079c1cb097ed3cdb22d00cceaa603b3dd2fa8921))


### 🚦 CI

* add -p on mkdir ([50e6822](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/50e68220e954a35242b601868b4608d9195f97ed))
* correct RP outputs ([14303c2](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/14303c2fb6b8df7e42fc06d93981dc5f9e8a9c23))


### 🔄 Refactoring

* minor fix with a param name ([#90](https://github.com/spotify/confidence-openfeature-provider-kotlin/issues/90)) ([4609f56](https://github.com/spotify/confidence-openfeature-provider-kotlin/commit/4609f56e0f1a545e74f21d4621f876762029616e))

## Changelog
