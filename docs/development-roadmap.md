# Paperize-PikPak development roadmap

## Incremental deliverables
- [x] Verify true fork, upstream relationship and GPL-3.0; create isolated `feature/pikpak-cloud-gallery`.
- [x] Inspect source + risk assessment; document tested vs untested assumptions.
- [x] Add pure Kotlin paginated scanner and unit tests.
- [x] Add safe bounded private image cache and unit tests.
- [x] Add separate Room metadata index and incremental reconciliation (not yet connected to UI/real provider).
- [x] Enable new feature-branch debug APK CI artifact; CI run 37944509717 produced a debug APK from accc171 and passed tests; a subsequent docs-only commit does not change compiled Kotlin files.
- [ ] Resolve stable official PikPak mobile OAuth and read-only API access.
- [ ] Build PikPak auth/folder UI, select/exclude recursive folders and test on real account.
- [ ] Wire on-demand image cache into existing bitmap preparation and HOME/LOCK setting.
- [ ] Implement Wi-Fi prefetch, retry/recovery and remote-sync scheduling.
- [ ] Extend anti-repeat, preview, favorite, blacklist, mixed local/cloud collections.
- [ ] Execute 1k/10k/50k index scale tests and OnePlus Ace 2 ColorOS 16 tests.

## MVP acceptance
PikPak login -> user-approved folder selection -> paginated metadata discovery -> fetch ONE image on demand -> apply wallpaper -> automatic random schedule (with HOME/LOCK sync). Every step must show code / unit test / CI / PikPak account / Ace 2 status separately. Existing local albums MUST keep working.

## Safety gates
No private tokens in GitHub, no root, no paid daemon or server. No assertion of official API compatibility before account test. Do not modify upstream master or merge incomplete PR automatically.
