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
A marker will be displayed on the map at the location of the sign with the text `<short description>`. The marker will
be removed from the map when the sign is broken.

Note that the prefixes can be configured in the configuration file.

# Configuration
The mod will create a `BMSM-Core.json` file in the `config/bluemapsignmarkers` folder. This file contains the following
options:
- `markerGroups` - a list of marker groups (described in detail below); default is a list with a single marker group
configured for the `[poi]` prefix.

## Marker Groups
A marker group is a collection of markers that can be toggled on and off in the BlueMap UI. Each marker group
configuration contains the following options:
- `prefix` - prefix that the sign must contain to be included in the marker group; required;
- `name` - the name of the marker group; required;
- `type` - the type of marker to display; optional; default is `POI`
- `icon` - the icon path or URL to display for the marker; optional; default is `null` (BlueMap default POI icon)
- `offsetX` - the x offset of the marker; optional; default is `0` (corresponds with `anchor.x` in BlueMap base configuration)
- `offsetY` - the y offset of the marker; optional; default is `0` (corresponds with `anchor.y` in BlueMap base configuration)

## Example

```json
{
  "markerGroups": [
    {
      "prefix": "[poi]",
      "name": "Points of Interest"
    },
    {
      "prefix": "[store]",
      "name": "Stores",
      "icon": "assets/store.png"
    }
  ]
}
```

This example configuration creates two marker groups: one for `[poi]` signs and one for `[store]` signs.

The `[poi]` marker group uses the default POI icon, while the `[store]` marker group uses a custom icon located at `assets/store.png`.

Any signs with the `[poi]` prefix will be displayed in the "Points of Interest" marker group, while any signs with the
`[store]` prefix will be displayed in the "Stores" marker group.

