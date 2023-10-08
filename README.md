BlueMap Sign Markers
====================

# Overview
Fabric plugin for BlueMap that displays markers based on in-game signs

# Installation
Place in your `mods` folder along with BlueMap.

Server-side only.

# Usage
Place a sign or hanging sign with the following text on either the front or back:
```
[poi]
<short description>
```
The sign will be displayed on the map at the location of the sign with the text `<short description>`. The sign will be removed from the map when the sign is broken.

Note that the prefix can be changed in the configuration file.

# Configuration
The mod will create a `BMSM-Core.json` file in the `config` folder. This file contains the following options:
- `poiPrefix` - The prefix to use on a sign for showing it on the BlueMap. Defaults to `[poi]`.