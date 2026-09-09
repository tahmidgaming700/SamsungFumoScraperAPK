# Samsung FUMO Scraper APK

Native Android Samsung firmware discovery client inspired by the LineageOS Archive Downloader UI.

## v1.0.0
- Samsung model and CSC input
- Automatic Android model/build detection
- Samsung `version.xml` discovery
- FUMO/OMA-DM architecture and authentication diagnostics
- Rounded translucent Material 3 interface
- Downloads and Settings sections
- No flashing, unlocking, recovery or partition operations

### Important
Samsung OSP/FUMO registration is authenticated. This application does not forge signatures, bypass authentication, or embed private Samsung credentials. If Samsung returns an authentication error such as `SSO_8003`, it is shown as a real protocol/authentication failure.

## Build
GitHub Actions builds the release APK when a `v*` tag is pushed.

## Credits
Protocol concepts are based on the public GPLv3 SamsungFumoClient and SamsungFumoScraper projects by timschneeb. See `NOTICE.md`.

## License
GPL-3.0-or-later.
