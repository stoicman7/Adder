# SocialFlow for Android

A dark, black-and-crimson workspace for organizing profile review.

## Version 2.0
- Fixed the zero-width content container that caused the blank main screen.
- Reworked the desktop-style left navigation and page layout.
- Dashboard with saved-profile and pending-review totals.
- Manually add and edit Facebook profile links.
- Duplicate URL detection and HTTPS/Facebook-domain validation.
- Search the review queue and reviewed history.
- Group profiles by source and filter the queue by source.
- Open individual profiles in the browser or Facebook app.
- Mark a profile reviewed, return it to the queue, or delete it.
- Adjustable 1–30 second personal review interval.
- Export a local JSON backup through Android's share menu.
- Eagle launcher icon and matching in-app branding.
- Local-only storage; no Facebook password or account connection.

## Deliberate scope
Profiles are added manually. The app does not scrape group members or reaction lists, send friend requests automatically, or perform unattended Facebook actions. Every profile is opened and reviewed by the user.

## Build
Open this folder in Android Studio (JDK 17, Android SDK 35), allow Gradle sync, then build the debug APK.

Minimum Android version: Android 6.0 (API 23).