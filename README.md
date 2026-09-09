# Samsung FUMO Scraper APK

Native Android port/reimplementation of the workflow and UI concepts from the public GPLv3 SamsungFumoScraper and SamsungFumoClient projects by timschneeb.

## v1.0.0
- SamsungFumoScraper-style **Android / Galaxy Buds / Advanced** modes
- Samsung model and CSC input
- Automatic Android model/build detection
- Samsung `version.xml` discovery
- Dedicated **Download** action
- Dedicated **Changelog** action
- Downloads screen with Android DownloadManager integration
- FUMO/OMA-DM architecture and authentication diagnostics
- Rounded Material 3 interface with light/dark themes
- No flashing, unlocking, recovery or partition operations

### Important
Samsung OSP/FUMO registration is authenticated. This application does not forge signatures, bypass authentication, or embed private Samsung credentials. Samsung may require a real registered device identity. A protected firmware download URL is only used when Samsung's authenticated FUMO/OMA-DM session returns an objectURI.

## Upstream
See `UPSTREAM_FUMO.md` for the source repositories and port scope.

## Build
GitHub Actions builds a debug APK first and then a release APK on pushes to `main`. The workflow publishes `v1.0.0` from `main`.

## Credits
Protocol concepts and the downloader workflow are based on the public GPLv3 SamsungFumoClient and SamsungFumoScraper projects by timschneeb.

## License
GPL-3.0-or-later.
