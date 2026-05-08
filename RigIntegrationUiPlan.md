# Rig Integration + UI Plan

## Goal

Move CWCN from `debug-first tools` toward a `formal operating app` without losing engineering visibility.

The key rule is:

- separate `transport family`
- from `rig profile family`
- from `adapter implementation`
- from `user-facing screen flow`

That lets us add concrete radio models later by filling profiles instead of rewriting the app structure.

## Current Position

Already usable:

- `Audio VOX` bench path
- `USB Serial Keyer / RTS-DTR` bench path
- `Mock USB Serial Keyer` diagnostics
- `Debug RX` and `TX Console` as engineering tools

Still missing:

- formal `Rig Profile` layer
- user-facing `Rig Setup` page
- CAT family abstraction
- concrete vendor/model profile mapping
- normal-user receive/transmit workflow pages

Update:

- `Rig Profile` layer is now present
- `Rig Setup` page is present
- first-pass CAT family abstraction is now present through reusable `CatProtocolFamily`
- still missing are concrete vendor/model mappings and real CAT adapter implementations

## Layering

### 1. Transport

Transport answers: `how do we physically or logically reach the rig?`

Examples:

- `USB_SERIAL`
- `BLUETOOTH_SERIAL`
- `NETWORK_CAT`
- `AUDIO_VOX`

### 2. Rig Profile

Profile answers: `what family of rig behavior do we expect on top of that transport?`

Examples:

- generic audio VOX
- generic USB serial keyer
- generic serial CAT/PTT
- generic network CAT
- generic Bluetooth serial keyer

Profile owns:

- display name
- vendor/model family label
- transport kind
- adapter id
- support maturity
- capability list
- setup notes
- known constraints

### 3. Adapter Implementation

Adapter answers: `what code actually performs keying/PTT/text TX right now?`

Examples:

- `AudioVoxRigControlAdapter`
- `UsbSerialKeyerRigControlAdapter`
- mock USB serial adapter
- future CAT adapters

### 4. User Flow

User-facing screens should not expose adapter internals first.
The user should think in terms of:

1. choose rig family
2. choose connection method
3. verify readiness
4. operate RX/TX
5. save QSO

## Recommended Build Order

### P0

- keep current debug and TX bench stable
- formalize `RigProfile` model
- add `Rig Setup` screen

### P1

- harden `Generic USB Serial Keyer`
- keep `Audio VOX` as fallback path
- make the formal UI point users to the best available route

### P2

- define reusable CAT profile schema
- include capability flags for:
- PTT
- frequency read/write
- mode read/write
- text-to-CW if available

Current status:

- first reusable CAT schema now exists at the `Rig Setup` level:
- serial CAT protocol family
- network CAT protocol family
- serial CAT baud rate
- serial port hint
- network host/port
- next layer is no longer "invent the schema", but "bind concrete adapter implementations and vendor/model profiles to it"
- the first concrete backend skeleton now exists on the network side through `Hamlib rigctld`

### P3

- add concrete profile families for real radios or common bridges
- example target families:
- Yaesu serial CAT
- Icom CI-V style CAT
- Kenwood serial CAT
- Bluetooth serial bridge family
- LAN/Wi-Fi CAT bridge family

## UI Direction

### Keep

- `Debug` as a hidden/secondary engineering page
- `TX Console` as a bench page until formal TX UX catches up

### Add / Grow

- `Operate` as the real primary screen
- `Spectrum`
- `Logbook`
- `Settings`
- `Rig Setup` as a focused setup/configuration path
- `QSO Editor / Logbook`

### UX principle

Normal users should not have to understand:

- adapter ids
- signal snapshots
- fixture evaluation
- raw pipeline stages

Those stay in Debug.

## 2026-05-02 Revised UI Decisions

The earlier `Home -> explain -> enter tools` direction is no longer the target.
The current agreed direction is much closer to an `FT8CN`-style operating console.

### 1. Entry flow

- the long-term primary launcher target should be `Operate`
- a separate heavy `Home` page is not the desired end state
- if `Home` exists temporarily during transition, it should stay thin and disposable
- the real product center is:
  - `Operate`
  - `Spectrum`
  - `Logbook`
  - `Settings`

### 2. Operate is the main battlefield

`Operate` should not be treated as a status page.
It should become the full-screen day-to-day operating console.

Agreed structure:

1. top status strip
- current WPM
- current tone / tracked tone state
- likely remote callsign
- possibly estimated receive quality / RST

2. top compact signal chart
- a compact spectrum / waterfall / trend view
- borrowed from useful `Debug` observability
- not as heavy as the full debug page

3. largest page region = received content
- this is the main visual focus
- not explanatory text blocks
- it should show what was received right now
- later this should distinguish RX/TX lines clearly

4. bottom send/input area
- one custom text input area
- quick template selection for common macros
- send / pause / resume / rewind / repeat controls

5. bottom standard navigation bar
- `Operate`
- `Spectrum`
- `Logbook`
- `Settings`
- visual language should lean toward simple Metro-like icon + short label usage

6. right floating side controls
- a draggable side button strip is acceptable and encouraged
- currently preferred first occupants:
  - `SQL`
  - `Templates`
- not preferred there:
  - `WPM`
  - `Tone`
- `WPM` and `Tone` state already belong in the top area

Resolved interaction details:

- the main receive area should default to a `mixed RX/TX stream`
- TX progress should appear inline as characters are sent
- RX/TX distinction should use `both color and prefix`
- top status should use `one main line + one secondary line`
- the compact `Draft / QSO` block should stay visible by default
- it should expose an `Expand` affordance
- expanded details should prefer a `semi-transparent floating panel`
- templates should use both:
  - a compact selector near the send area
  - a side-button entry path
- the selector itself should stay title-first
- a small preview/detail snippet may appear beside or below it
- send controls should be `dynamic` rather than always fully expanded
- compact playback-style controls are acceptable for:
  - `Pause`
  - `Resume`
  - `Rewind`

### 2.2 Operate v1 freeze

The following should now be treated as the current `Operate` baseline unless a later review explicitly changes it:

1. `Operate` stays single-screen and dense
- avoid turning it back into an explanatory dashboard
- the receive stream must remain the largest area

2. top area = runtime state only
- main line for current receive state
- secondary line for route / source / review status
- `WPM` and tone stay here instead of becoming floating tools

3. center area = mixed stream
- show `RX RAW`, optional readable `RX TXT`, and staged `TX`
- preserve RAW as descriptive capture output
- normalized text remains a separate interpretation layer

4. lower area = compact operating dock
- small always-visible `Draft / QSO` summary
- inline `Review` action
- `Expand` opens a semi-transparent detail layer
- send composer remains directly below or adjacent in the same lower operating zone

5. template access = dual path
- quick selector near the composer for speed
- side-button access for fuller preview without crowding the main page

6. floating overlays are preferred over permanent secondary panes
- `Chart`
- `SQL`
- `Template preview`
- `Draft detail`

8. right-side control strip should remain draggable
- draggable positioning is useful and should stay
- near-term implementation may use a dedicated drag handle
- remembering the last parked position between sessions is preferred

7. bottom navigation remains page-level only
- `Operate`
- `Spectrum`
- `Logbook`
- `Settings`
- `TX` is not promoted to a bottom tab

### 2.5 TX is part of Operate, not bottom navigation

- `TX` is not a bottom tab
- send initiation belongs inside the `Operate` composer area
- a right-side send button beside the input field is an acceptable interaction
- bottom navigation should remain focused on page-level destinations such as:
  - `Operate`
  - `Spectrum`
  - `Logbook`
  - `Settings`

### 3. Spectrum / waterfall

- current agreed direction is acceptable for now:
  - compact visual on `Operate`
  - fuller dedicated `Spectrum` page
- this is intentionally kept somewhat independent so it can evolve later
- on `Operate`, the compact visual may be implemented as a `semi-transparent overlay`
- the overlay can be opened from the right-side strip
- it can be dismissed either from the strip again or by an explicit close affordance

### 4. Logbook

- current direction is acceptable
- it remains a formal product page, not an engineering extra

### 5. Settings replaces scattered config entry points

Settings should follow a flattened `FT8CN`-style grouped layout.
It is the formal home for nearly all configuration.

Target sections:

1. `Station`
- callsign
- name
- QTH
- grid
- template variables such as rig / ant / power

2. `Radio`
- transport and audio-related setup

3. `CAT & Keying`
- serial / bluetooth / OTG related rig control configuration
- CAT protocol family
- baud rate
- serial port hint
- CW keying line choice such as `RTS / DTR`
- keying polarity and related formal user settings

4. `CW Templates`
- CQ / QRZ / TU73 and similar macro management

5. `Logs & Export`
- lightweight log/export preferences only
- avoid turning this into a complex rules engine page

6. `Advanced / Developer`
- developer mode switch
- hidden engineering entrances

### 6. Rig Bench is not the formal config page

This boundary is now important:

- `Settings` should own formal rig/CAT/CW keying configuration
- `Rig Bench` should exist only for testing / diagnostics / verification
- users should not be forced into a bench page just to set normal options like:
  - CAT family
  - baud rate
  - `RTS / DTR`
  - keying polarity

### 7. Default route fallback

- if no radio is pinned, RX input should default to the phone microphone
- if no radio is pinned, TX should default to the phone audio path
- if a radio is pinned, the app should use the rig-specific RX/TX route from `Settings`
- this fallback choice should be user-editable in `Settings`
- the UI should treat this as the normal default behavior, not a debug-only switch

### 7. Debug visibility rule

- `Debug / Developer Tools` should stay available
- they should keep roughly the current engineering scope
- but they should be hidden under `Settings -> Advanced / Developer`
- they should not dominate the normal user path

## Immediate Deliverables

This milestone should land:

- continue moving from a shell `Operate` page toward a true operating console
- introduce / stabilize the dedicated `Settings` page as the formal config hub
- keep `Debug` available but clearly secondary
- progressively move formal CAT / keying configuration out of `Rig Bench` and into `Settings`

That gives us a stable place to grow from before attaching real vendor/model support and before polishing the final visual design from mockups.
