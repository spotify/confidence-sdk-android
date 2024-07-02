# CONTRIBUTING

## Formatting

This repo uses [ktlint](https://github.com/JLLeitschuh/ktlint-gradle) for formatting.

Please consider adding a pre-commit hook for formatting using

```
./gradlew addKtlintCheckGitPreCommitHook
```
Manual formatting is done by invoking
```
./gradlew ktlintFormat
```

## Binary compatibility validations

This repo uses the [Kotlin Binary Validator](https://github.com/Kotlin/binary-compatibility-validator) to 
ensure that changes do not break binary compatibility without us noticing.

The gradle `check` task on CI will validate against the generated API file.

When doing code changes that do not imply any changes in public API, no additional actions should be performed. 

When doing code changes that imply changes in public API, whether it is a new API or adjustments in existing one, 
the `check` task will start to fail. 

This requires you to run the gradle `apiDump` task (on the failing gradle project) manually, the 
resulting diff in .api file should be verified: only signatures you expected to change should be changed.

**Commit the resulting .api diff along with code changes.**