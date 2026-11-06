# Muninn

**A keyboard that remembers what you save and brings it back.**

In Norse myth, Muninn is Odin's raven of memory. Every day it flies over the world and comes back to tell Odin what it saw. This keyboard does the same with the things you copy and save: it keeps them, and it brings them back at the right moment instead of letting them sink into a list you never open again.

Muninn is an Android keyboard. It has no internet permission, so everything stays on your phone.

## Status

Early development. Muninn is built in layers, and each layer is a version you can install and use:

| Layer | What it adds | State |
|---|---|---|
| 0 · Baseplate | Muninn name and icon, Rune Night theme, number row, unlimited clipboard history | **in progress** |
| 1 · Memory | The cache: every copy kept and searchable, source app, Recently deleted | planned |
| 2 · Organise | Tags, pins, editing, templates, fingerprint lock | planned |
| 3 · Remind | Reminders on saved clips: calm, noticeable or loud | planned |
| 4 · Guard | Stops other keyboards from taking over | planned |
| 5 · Catch | "Share to Muninn" from any app | planned |
| 6 · Smart return | Reminders based on the app you are in, weekly look back | planned |
| 7 · Polish | The full Rune & Feather design, themes, animations and haptics | planned |

## Built on HeliBoard

Muninn is a fork of [HeliBoard](https://github.com/HeliBorg/HeliBoard), which is based on [OpenBoard](https://github.com/openboard-team/openboard) and the [AOSP keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/). All of the typing engine, layouts, dictionaries support and much more comes from those projects and their contributors. HeliBoard's original README is kept in [README-HeliBoard.md](README-HeliBoard.md).

Please don't report Muninn problems to HeliBoard. Muninn is a separate project.

Dictionaries for more languages: https://codeberg.org/Helium314/aosp-dictionaries

## License

Muninn is licensed under the GNU General Public License v3.0, the same as HeliBoard. See [LICENSE](LICENSE). Parts that come from the AOSP keyboard are also available under the Apache License 2.0 ([LICENSE-Apache-2.0](LICENSE-Apache-2.0)). HeliBoard's icon artwork is CC BY-SA 4.0 ([LICENSE-CC-BY-SA-4.0](LICENSE-CC-BY-SA-4.0)); Muninn uses its own feather icon.

Muninn is developed with help from an AI assistant (Claude, by Anthropic).
