# Upstream SamsungFumoScraper

This Android project is a native Android port/reimplementation of the workflow in:

- https://github.com/timschneeb/SamsungFumoScraper
- https://github.com/timschneeb/SamsungFumoClient

The upstream scraper is a WebAssembly application using Samsung OMA-DM with FUMO extensions to retrieve Samsung OTA firmware files. The upstream project explicitly notes that Samsung may require a real IMEI/device identity for registration.

The Android UI follows the upstream modes:

- Android
- Galaxy Buds
- Advanced

The Android implementation keeps the upstream protocol boundaries: FUMO registration, SyncML/OMA-DM session, download descriptor, objectURI, and final firmware download. Samsung authorization is not bypassed and protected URLs are never fabricated.

Upstream license: GPLv3. See the upstream repository for the original license text and attribution.
