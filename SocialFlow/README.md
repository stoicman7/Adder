# Eagle Assist for Android

A dark, black-and-crimson Facebook screen assistant with eagle branding.

## Version 3.1
- Floating Eagle Assist controls while Facebook or Facebook Lite is in the foreground.
- Detects supported visible Add Friend labels in English, French and Arabic.
- Each Add Friend tap requires an individual CONFIRM ONE press.
- NEXT scrolls the current list once, only when pressed.
- Detects common per-person add-failure dialogs and offers DISMISS NOTICE; the user must press this control to tap the dialog's OK button.
- Detects common Facebook temporary-block and request-limit messages and locks request/scroll actions.
- STOP disarms the assistant and removes its floating panel.
- No credentials, profile database, batch requests, automatic timer, or unattended request loop.
- Screen text is inspected locally on the device.

## Setup
1. Install the APK.
2. Open Eagle Assist and choose Enable Accessibility.
3. In Android Settings, enable Eagle Assist.
4. Return and press ARM ASSIST + OPEN FACEBOOK.
5. Open a group member list or post reactions.
6. Use CONFIRM ONE for one visible Add Friend button, NEXT for one scroll, and DISMISS NOTICE to acknowledge a detected per-person failure dialog.

## Safety and limitations
The tool does not bypass Facebook request limits or restrictions. When a limit is detected, request and scroll controls are locked. A tap reported as successful means Android accepted the accessibility click; it does not prove Facebook accepted the friend request. Always inspect Facebook's response.

## Build
JDK 17 and Android SDK 35. Build with Gradle 8.9 using the included GitHub Actions workflow.