# SocialFlow (Android)

A dark, restrained black-and-red Android app for manually organizing an outreach review queue.

## Included
- Persistent local profile list (name, HTTPS profile URL, source label)
- Audience/source grouping
- Review queue and reviewed history
- Open a saved profile in the installed browser/Facebook app
- Adjustable 1–30 second review pacing preference
- Local-only storage; no Facebook password or account connection

## Deliberate scope
This app does not scrape Facebook group members or reaction lists, automate bulk friend requests, or bypass Facebook/Android restrictions. Profiles are added manually and each profile is opened/reviewed by the user.

## Build
Open this folder in Android Studio (JDK 17, Android SDK 35), allow Gradle sync, then Build > Build APK(s).

Minimum Android version: Android 6.0 (API 23). Target SDK: 35.
