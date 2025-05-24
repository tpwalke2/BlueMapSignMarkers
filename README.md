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
- `matchType` - the type of match to use when checking the first non-empty sign text line; optional; default is `STARTS_WITH` (case-sensitive exact match);
  - `STARTS_WITH` - line must start with the prefix;
  - `REGEX` - line must match the regular expression (uses [Java regex engine](https://docs.oracle.com/javase/8/docs/api/java/util/regex/Pattern.html));
- `name` - the name of the marker group; required;
- `type` - the type of marker to display; optional; default is `POI`
- `icon` - the icon path or URL to display for the marker; optional; default is `null` (BlueMap default POI icon)
- `offsetX` - the x offset of the marker; optional; default is `0` (corresponds with `anchor.x` in BlueMap base configuration)
- `offsetY` - the y offset of the marker; optional; default is `0` (corresponds with `anchor.y` in BlueMap base configuration)
- `defaultHidden` - If this is true, the marker-set will be hidden by default and can be enabled by the user; optional; default is `false`
- `minDistance` - the minimum distance from the camera at which the marker will be displayed; optional; default is `0.0` (floating point, double precision)
- `maxDistance` - the maximum distance from the camera at which the marker will be displayed; optional; default is `10000000.0` (floating point, double precision)

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
    },
    {
      "prefix": "\\[[vV][iI][lL][lL][aA][gG][eE]\\]",
      "matchType": "REGEX",
      "name": "Villages"
    }
  ]
}
```

This example configuration creates 3 marker groups: one for `[poi]` signs, one for `[store]` signs, and one for signs
where the prefix is a regex match for villages (e.g. `[Village]` or `[VILLAGE]`).

The `[poi]` and `Villages` marker groups use the default POI icon, while the `[store]` marker group uses a custom icon
located at `assets/store.png`.

Signs with the `[poi]` prefix will be displayed in the "Points of Interest" marker group. Signs with the `[store]`
prefix will be displayed in the "Stores" marker group. Signs that match the villages regex will be displayed in the
'Villages' marker group.

