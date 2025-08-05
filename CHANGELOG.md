# Changelog

## [0.5.2](https://github.com/spotify/confidence-sdk-android/compare/0.5.1...0.5.2) (2025-08-05)


### 🧹 Chore

* Bump OpenFeature SDK to 0.5.3 ([#207](https://github.com/spotify/confidence-sdk-android/issues/207)) ([17187e3](https://github.com/spotify/confidence-sdk-android/commit/17187e3240574d208bc0f49476cf15f269f82f9e))
* change credentials to central ([d6936eb](https://github.com/spotify/confidence-sdk-android/commit/d6936ebf7d7fbcd0a9ccc595e2eae57c1075fd01))
* support maven central deployment ([#208](https://github.com/spotify/confidence-sdk-android/issues/208)) ([d6936eb](https://github.com/spotify/confidence-sdk-android/commit/d6936ebf7d7fbcd0a9ccc595e2eae57c1075fd01))


### 📚 Documentation

* on dot notation ([#205](https://github.com/spotify/confidence-sdk-android/issues/205)) ([ff0a05c](https://github.com/spotify/confidence-sdk-android/commit/ff0a05c492467d4316f2038a7c345aded049d19c))

## [0.5.1](https://github.com/spotify/confidence-sdk-android/compare/0.5.0...0.5.1) (2025-04-28)


### 🐛 Bug Fixes

* change how we log resolve tester hints ([#203](https://github.com/spotify/confidence-sdk-android/issues/203)) ([6cdbc41](https://github.com/spotify/confidence-sdk-android/commit/6cdbc4117e7b11dc1294d3cda45b2d6f72f2ff52))


### 🧹 Chore

* move to toml for version catalog ([#200](https://github.com/spotify/confidence-sdk-android/issues/200)) ([ca26a67](https://github.com/spotify/confidence-sdk-android/commit/ca26a67ca69773f5302e3f76168eca9b52df6e67))

## [0.5.0](https://github.com/spotify/confidence-sdk-android/compare/0.4.0...0.5.0) (2025-03-17)


### ⚠ BREAKING CHANGES

* **Provider:** update to OF SDK 0.4.1 ([#197](https://github.com/spotify/confidence-sdk-android/issues/197))

### 🐛 Bug Fixes

* skip extra resolve when initialising ConfidenceProvider ([#199](https://github.com/spotify/confidence-sdk-android/issues/199)) ([b074b8b](https://github.com/spotify/confidence-sdk-android/commit/b074b8b06727e44768382faebb06c6300b3e69c7))


### ✨ New Features

* **Provider:** update to OF SDK 0.4.1 ([#197](https://github.com/spotify/confidence-sdk-android/issues/197)) ([21e3302](https://github.com/spotify/confidence-sdk-android/commit/21e3302237073dae6559335835f489f580707815))

## [0.4.0](https://github.com/spotify/confidence-sdk-android/compare/0.3.6...0.4.0) (2025-02-18)


### ⚠ BREAKING CHANGES

* refactor context provider ([#191](https://github.com/spotify/confidence-sdk-android/issues/191))

### 🐛 Bug Fixes

* add additional logging to detect network issues ([#193](https://github.com/spotify/confidence-sdk-android/issues/193)) ([780feb1](https://github.com/spotify/confidence-sdk-android/commit/780feb184ff8ee0c93b1c5e4ab8a7f6e3a7669e5))
* align debug resolve log wording on all platforms ([a2b10f1](https://github.com/spotify/confidence-sdk-android/commit/a2b10f1bfb948b37425f8c30c5e9445c0671d83d))
* Align debug resolve log wording on all platforms ([#188](https://github.com/spotify/confidence-sdk-android/issues/188)) ([a2b10f1](https://github.com/spotify/confidence-sdk-android/commit/a2b10f1bfb948b37425f8c30c5e9445c0671d83d))
* close leaking okhttp response body ([#196](https://github.com/spotify/confidence-sdk-android/issues/196)) ([5a14feb](https://github.com/spotify/confidence-sdk-android/commit/5a14feb9d79df41330af8ae237ab26657c9edaef))


### ✨ New Features

* add shouldApply to the apply call to depend on backend ([#192](https://github.com/spotify/confidence-sdk-android/issues/192)) ([70598bd](https://github.com/spotify/confidence-sdk-android/commit/70598bd3d3adc1dd9d36155653a0d7f64c26ddfa))
* **Provider:** Add support for Tracking from OpenFeature ([#190](https://github.com/spotify/confidence-sdk-android/issues/190)) ([97c763c](https://github.com/spotify/confidence-sdk-android/commit/97c763c6bd2f07249a808d44cdbba63cba58fc2e))
* refactor context provider ([#191](https://github.com/spotify/confidence-sdk-android/issues/191)) ([37c243e](https://github.com/spotify/confidence-sdk-android/commit/37c243e1cc8c02ee06c09a018d6211fb778a2fdf))


### 🧹 Chore

* update OF version ([#195](https://github.com/spotify/confidence-sdk-android/issues/195)) ([56852a9](https://github.com/spotify/confidence-sdk-android/commit/56852a984d8ef032a96762b66ef15890bfb367e5))

## [0.3.6](https://github.com/spotify/confidence-sdk-android/compare/0.3.5...0.3.6) (2024-11-29)


### 🐛 Bug Fixes

* await reconciliation bug ([#187](https://github.com/spotify/confidence-sdk-android/issues/187)) ([a352533](https://github.com/spotify/confidence-sdk-android/commit/a3525332573bc623321d7e91e272c615e790a728))
* cancellation of requests work correctly ([#186](https://github.com/spotify/confidence-sdk-android/issues/186)) ([95377ad](https://github.com/spotify/confidence-sdk-android/commit/95377ad7f8706bdb572703cdd492d5e5fa35bb9d))
* Fix the bug regarding accessing flags using with context ([#172](https://github.com/spotify/confidence-sdk-android/issues/172)) ([39625e6](https://github.com/spotify/confidence-sdk-android/commit/39625e6de9f7f2e878b4397616b0954adb0ee92b))
* guard possible changes in serialization ([#182](https://github.com/spotify/confidence-sdk-android/issues/182)) ([64c77ac](https://github.com/spotify/confidence-sdk-android/commit/64c77ac1bb8eb8c810e7923ac1d2e145d7e7c2d9))
* update how debug logging works ([#174](https://github.com/spotify/confidence-sdk-android/issues/174)) ([bd79fad](https://github.com/spotify/confidence-sdk-android/commit/bd79fad16079345ed9046c115cfbe09d13b791fe))


### ✨ New Features

* add binary-compatibility-validator plugin ([111e8bd](https://github.com/spotify/confidence-sdk-android/commit/111e8bd1c86cfc397845af97f63c8b63a8b4f295))
* add timeout to the http client ([#181](https://github.com/spotify/confidence-sdk-android/issues/181)) ([eab3985](https://github.com/spotify/confidence-sdk-android/commit/eab3985f7394fef229ce576212586dbbcb016020))
* Resolve Debug to redirect to UI tool on web ([#183](https://github.com/spotify/confidence-sdk-android/issues/183)) ([ae05eef](https://github.com/spotify/confidence-sdk-android/commit/ae05eeff0e52b0450c1a54e3083f3b890fd9987c))


### 🧹 Chore

* minor cleanups ([#177](https://github.com/spotify/confidence-sdk-android/issues/177)) ([8950ffc](https://github.com/spotify/confidence-sdk-android/commit/8950ffcedf4ae0a6015fe1d89f8869caae1955b6))


### 📚 Documentation

* Add docs to public APIs ([#179](https://github.com/spotify/confidence-sdk-android/issues/179)) ([054fb0f](https://github.com/spotify/confidence-sdk-android/commit/054fb0fd397eed410b0ae30ebb412c7c897df4ac))
* update readme ([#180](https://github.com/spotify/confidence-sdk-android/issues/180)) ([52200f4](https://github.com/spotify/confidence-sdk-android/commit/52200f4d57f1839237126e5f7614400ba3534c2f))

## [0.3.5](https://github.com/spotify/confidence-sdk-android/compare/0.3.4...0.3.5) (2024-06-25)


### ✨ New Features

* add DebugLogger to Confidence ([#165](https://github.com/spotify/confidence-sdk-android/issues/165)) ([0848912](https://github.com/spotify/confidence-sdk-android/commit/08489126c94db2abb227e7aab439308f483df3c4))


### 🔄 Refactoring

* Revert "feat: Expose set sdk metadata ([#166](https://github.com/spotify/confidence-sdk-android/issues/166))" ([#173](https://github.com/spotify/confidence-sdk-android/issues/173)) ([743a076](https://github.com/spotify/confidence-sdk-android/commit/743a076d13581f9d9581a835fcef352b0e0b0141))

## [0.3.4](https://github.com/spotify/confidence-sdk-android/compare/0.3.3...0.3.4) (2024-06-18)


### ✨ New Features

* expose convert value to json ([#168](https://github.com/spotify/confidence-sdk-android/issues/168)) ([ceb8a2e](https://github.com/spotify/confidence-sdk-android/commit/ceb8a2e98f00925366caf40b87c20190f96da078))

## [0.3.3](https://github.com/spotify/confidence-sdk-android/compare/0.3.2...0.3.3) (2024-06-18)


### 🔄 Refactoring

* Add ConfidenceForOF proxy for safe init ([#167](https://github.com/spotify/confidence-sdk-android/issues/167)) ([e177c5b](https://github.com/spotify/confidence-sdk-android/commit/e177c5b5ada3f48d57014d6f8f3d7b95ea9bdc92))

## [0.3.2](https://github.com/spotify/confidence-sdk-android/compare/0.3.1...0.3.2) (2024-06-17)


### ✨ New Features

* Expose set sdk metadata ([#166](https://github.com/spotify/confidence-sdk-android/issues/166)) ([10548d9](https://github.com/spotify/confidence-sdk-android/commit/10548d95cf923d3a10d37f3cc9c59394d087bf66))


### 🔄 Refactoring

* remove is_foreground from event producer ([#163](https://github.com/spotify/confidence-sdk-android/issues/163)) ([27846fc](https://github.com/spotify/confidence-sdk-android/commit/27846fc60102f7f61ac35cf0885ef09e2a51c806))

## [0.3.1](https://github.com/spotify/confidence-sdk-android/compare/0.3.0...0.3.1) (2024-06-03)


### 🐛 Bug Fixes

* make the event publish url global ([#160](https://github.com/spotify/confidence-sdk-android/issues/160)) ([564b081](https://github.com/spotify/confidence-sdk-android/commit/564b081513fe6607d150c69bb30fcb56407215ae))
* wrap around dispatcher in fetch and activate ([#162](https://github.com/spotify/confidence-sdk-android/issues/162)) ([f8456fb](https://github.com/spotify/confidence-sdk-android/commit/f8456fbb42d8eaf1df3855fe864f42f039c3f6c5))

## [0.3.0](https://github.com/spotify/confidence-sdk-android/compare/0.2.1...0.3.0) (2024-05-29)


### ⚠ BREAKING CHANGES

* context container in payload
* `context` container in event payload ([#158](https://github.com/spotify/confidence-sdk-android/issues/158))

### 🐛 Bug Fixes

* Add version/build to context ([#154](https://github.com/spotify/confidence-sdk-android/issues/154)) ([1e71fb9](https://github.com/spotify/confidence-sdk-android/commit/1e71fb99a32b90fd4785ec2aeb7782399b959628))
* Prevent coroutine crashing on errors ([#157](https://github.com/spotify/confidence-sdk-android/issues/157)) ([d75229a](https://github.com/spotify/confidence-sdk-android/commit/d75229a766d9f72c522e0503a8cd34566e7649ae))
* Remove the duplicate singleton confidence ([#156](https://github.com/spotify/confidence-sdk-android/issues/156)) ([60deb61](https://github.com/spotify/confidence-sdk-android/commit/60deb61cb87029d21b792de9719bf431bb4a26cb))


### ✨ New Features

* add manual flush capabilities ([#152](https://github.com/spotify/confidence-sdk-android/issues/152)) ([157eeb9](https://github.com/spotify/confidence-sdk-android/commit/157eeb955df663d163877e1e659e9472dde7d4c6))


### 📚 Documentation

* Remove some README parts ([#151](https://github.com/spotify/confidence-sdk-android/issues/151)) ([45b9964](https://github.com/spotify/confidence-sdk-android/commit/45b996432606e0154fe73df847068bb025bf96bc))
* Smaller README refactoring ([#153](https://github.com/spotify/confidence-sdk-android/issues/153)) ([d4efefc](https://github.com/spotify/confidence-sdk-android/commit/d4efefcabdd4cc2c06694221aab7b57d300db995))
* update readme to cover Confidence APIs ([#149](https://github.com/spotify/confidence-sdk-android/issues/149)) ([8cfea1c](https://github.com/spotify/confidence-sdk-android/commit/8cfea1caf8dfe4ce89c21546fdc23f413a4cd65a))


### 🔄 Refactoring

* `context` container in event payload ([#158](https://github.com/spotify/confidence-sdk-android/issues/158)) ([fd54e21](https://github.com/spotify/confidence-sdk-android/commit/fd54e2186f531f3a4d4f4449eb5b0aeb828c1aed))
* context container in payload ([fd54e21](https://github.com/spotify/confidence-sdk-android/commit/fd54e2186f531f3a4d4f4449eb5b0aeb828c1aed))
* from "message" to "data" ([#159](https://github.com/spotify/confidence-sdk-android/issues/159)) ([06b4d5f](https://github.com/spotify/confidence-sdk-android/commit/06b4d5f2a34e64ff9035129c3f0c8b8a784caad2))

## [0.2.1](https://github.com/spotify/confidence-sdk-android/compare/0.2.0...0.2.1) (2024-05-16)


### 🐛 Bug Fixes

* do not throw while evaluating flag, return proper error ([#147](https://github.com/spotify/confidence-sdk-android/issues/147)) ([d26d8e0](https://github.com/spotify/confidence-sdk-android/commit/d26d8e025dd72365d8bd28d07f71eba27d7287ef))
* update visitorId context key to visitor_id ([#145](https://github.com/spotify/confidence-sdk-android/issues/145)) ([e960451](https://github.com/spotify/confidence-sdk-android/commit/e960451f916fd5614cdc676e983c7e93badcd8bd))


### 🧹 Chore

* update openfeature sdk version to 0.3.0 ([#128](https://github.com/spotify/confidence-sdk-android/issues/128)) ([0a4be58](https://github.com/spotify/confidence-sdk-android/commit/0a4be5872f49527aff6310addadfd66a2e83dfd6))

## [0.2.0](https://github.com/spotify/confidence-sdk-android/compare/0.1.7...0.2.0) (2024-05-07)


### ⚠ BREAKING CHANGES

* split the confidence module to a new module ([#136](https://github.com/spotify/confidence-sdk-android/issues/136))
* Total Confidence - Eventsender ([#124](https://github.com/spotify/confidence-sdk-android/issues/124))

### 🐛 Bug Fixes

* change STALE reason in case of context change ([#123](https://github.com/spotify/confidence-sdk-android/issues/123)) ([fae882f](https://github.com/spotify/confidence-sdk-android/commit/fae882f433fd451ba889ea0e570c449d7c60fb67))
* ConfidenceValue.List can only contain single type [#129](https://github.com/spotify/confidence-sdk-android/issues/129) ([c532f9f](https://github.com/spotify/confidence-sdk-android/commit/c532f9f0ce6dac00d224e91c44b22ccec43e8c9d))
* Merge context and message together ([#137](https://github.com/spotify/confidence-sdk-android/issues/137)) ([2fc83b2](https://github.com/spotify/confidence-sdk-android/commit/2fc83b2852c4f7a6f6bd89cab5df4c08f6b39e75))
* Release please increase depth ([#141](https://github.com/spotify/confidence-sdk-android/issues/141)) ([e0e1676](https://github.com/spotify/confidence-sdk-android/commit/e0e1676bee2a2d01918a0f9f33f1bed27a8e55f3))
* Remove extra files in rel-pls ([#140](https://github.com/spotify/confidence-sdk-android/issues/140)) ([d2d54e8](https://github.com/spotify/confidence-sdk-android/commit/d2d54e808f52b305eba05b89f76386fa6fba39ff))
* Remove STALE event ([#122](https://github.com/spotify/confidence-sdk-android/issues/122)) ([019dbfc](https://github.com/spotify/confidence-sdk-android/commit/019dbfc57a09dcd98134ce54247409a3e7b8127d))
* Update release-please-config.json ([#139](https://github.com/spotify/confidence-sdk-android/issues/139)) ([9da8d7e](https://github.com/spotify/confidence-sdk-android/commit/9da8d7e3c3e9e35c3672ad2ca1d2d2726a29e542))
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
* split the confidence module to a new module ([#136](https://github.com/spotify/confidence-sdk-android/issues/136)) ([ebe2733](https://github.com/spotify/confidence-sdk-android/commit/ebe27338366c56878ffced17d59b2e74360e0873))

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
