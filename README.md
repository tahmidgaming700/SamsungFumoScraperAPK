# OS Updater for Galaxy Tab S

Native Android firmware/update utility focused on Samsung Galaxy Tab S variants.

## Supported models
- SM-T805
- SM-T805K
- SM-T807
- SM-T800
- SM-T705

## Features
- Native updater UI modeled after the LineageOS Archive Downloader experience
- Bottom navigation: Home, Stock Firmware, LineageOS, Downloads, Tools, Settings
- Native model picker for SM-T805, SM-T805K, SM-T807, SM-T800 and SM-T705
- Multi-CSC picker with regional CSC choices
- Native SamFW firmware lookup screen; no WebView or embedded SamFW page
- TimSchumi LineageOS archive lookup with SHA-256 metadata
- Samsung FOTA/version.xml OTA metadata lookup without opening the FUMO website
- Download Manager integration
- Root detection, TWRP/OrangeFox OpenRecoveryScript preparation and dd image flashing
- Firmware extraction workflow guidance

## Safety
This is an unofficial community utility. Samsung firmware pages and FUMO services may require authentication or device registration. Old firmware can be unavailable. The LineageOS archive is unofficial and warns that archived builds may be obsolete. Flashing the wrong partition can permanently brick a device.

The app does not bypass Samsung authentication or bootloader security.

## Sources
- LineageOS archive: https://lineage-archive.timschumi.net/
- SamsungFumoScraper: https://github.com/timschneeb/SamsungFumoScraper
- SamsungFumoClient: https://github.com/timschneeb/SamsungFumoClient
- SamFW: https://samfw.com/

## License
GPL-3.0-or-later.