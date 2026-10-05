# OS Updater for Galaxy Tab S

Native Android firmware/update utility focused on Samsung Galaxy Tab S variants.

## Supported models
- SM-T805
- SM-T805K
- SM-T807
- SM-T800
- SM-T705

## Features
- Modern updater UI, separate from the LineageOS Archive Downloader layout
- TimSchumi LineageOS archive discovery and download
- LineageOS OTA-ready package download for TWRP/OrangeFox OpenRecoveryScript
- SamFW firmware pages and SamFW-server download workflow through Android WebView/DownloadManager
- Samsung FUMO/OMA-DM stock OTA workflow based on the public SamsungFumoScraper concept
- ZIP and TAR.MD5 extraction procedure
- Root detection and /dev/block/by-name partition discovery
- dd image flashing for explicitly selected stock images
- TWRP and OrangeFox OpenRecoveryScript preparation

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