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

- `Home`
- `Rig Setup`
- future `RX`
- future `TX`
- `QSO Editor / Logbook`

### UX principle

Normal users should not have to understand:

- adapter ids
- signal snapshots
- fixture evaluation
- raw pipeline stages

Those stay in Debug.

## Immediate Deliverables

This milestone should land:

- `RigProfile` and related enums
- `RigProfileCatalog`
- `RigRegistry.defaultProfiles()`
- `Rig Setup` activity
- `Home` entry to `Rig Setup`

That gives us a stable place to grow from before attaching real vendor/model support.
