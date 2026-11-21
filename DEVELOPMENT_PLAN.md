# IING STD Launcher Pro — Development Plan

## Principles
- Free and ad-free: no advertising SDKs, trackers, or subscriptions.
- Real Android HOME launcher, not a mockup.
- Offline-first, privacy-focused, no root required.
- Prioritize reliable functionality over decorative UI.

## MVP
- [ ] Kotlin Android project with Gradle and GitHub Actions debug APK build.
- [ ] HOME and DEFAULT launcher intent filters; user-controlled default launcher selection.
- [ ] Display installed launchable apps with Android package visibility rules.
- [ ] Launch apps from a searchable app drawer.
- [ ] Home screen with clock, battery status, Wi-Fi status, and wallpaper.
- [ ] Custom status indicators only within launcher UI; do not claim to replace system status bar.
- [ ] Cyber Neon and AMOLED themes.
- [ ] Settings for icon size and grid columns.
- [ ] Persist launcher settings locally.

## Later
- [ ] App shortcuts, folders, and icon rearrangement.
- [ ] Android AppWidgetHost support.
- [ ] Gesture customization and accessibility improvements.
- [ ] Backup and restore layouts.
- [ ] Device testing and performance tuning.

## Platform constraints
- System status bar and Quick Settings cannot be globally redesigned by a normal launcher without privileged access.
- Signal details and some Wi-Fi information depend on Android permissions and version.
- Android gesture navigation / Recents integration may be OEM- or system-launcher-dependent.

## Release criteria
- Build succeeds in CI; APK artifact downloadable.
- HOME intent recognized; switching back to stock launcher works.
- Apps launch reliably; search, clock, and battery update correctly.
- No ad SDK, tracking SDK, or unnecessary network permissions.
