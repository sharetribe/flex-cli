# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a
Changelog](http://keepachangelog.com/en/1.0.0/) and this project
adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [v1.16.0] - 2026-07-13

### Added

- Add support for `:action/reveal-listing-protected-files` action. [#120](https://github.com/sharetribe/flex-cli/pull/120)

### Fixed

- Exit gracefully using by setting `process.exitCode` instead of calling `process.exit()`. [Calling `process.exit(...)` will force the process to exit as quickly as possible even if there are still asynchronous operations pending that have not yet completed fully, including I/O operations to process.stdout and process.stderr.](https://nodejs.org/api/process.html#processexitcode) This may result in an error when e.g. piping the output to `jq`.[#119](https://github.com/sharetribe/flex-cli/pull/119)

### Changed

- The CLI now requires Node.js version 18 or newer. [#113](https://github.com/sharetribe/flex-cli/pull/113)
- Update shadow-cljs to 2.15.12.
  [#111](https://github.com/sharetribe/flex-cli/pull/111)

## [v1.15.0-beta.0] - 2025-06-10

### Changed

- Allow provider to be the actor of a transaction initializing transition. [#106](https://github.com/sharetribe/flex-cli/pull/106)

### Added

- Add support for `:time/last-entered-state` time point definition. [#109](https://github.com/sharetribe/flex-cli/pull/109)

## [v1.14.1] - 2025-04-15

### Added

- Add CHANGELOG.md

### Changed

- Update option descriptions for set and unset search commands. [#108](https://github.com/sharetribe/flex-cli/pull/108)


[unreleased]: https://github.com/sharetribe/flex-cli/compare/v1.14.1...HEAD

[v1.14.1]: https://github.com/sharetribe/flex-cli/compare/v1.14.0...v1.14.1

