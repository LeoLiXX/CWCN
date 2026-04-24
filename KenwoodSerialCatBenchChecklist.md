# Kenwood Native Serial CAT Bench Checklist

Use this checklist when testing the first native `Kenwood-style CAT` probe path in `Rig Setup`.

## Goal

Confirm that CWCN can:

- see the USB CDC/ACM serial device
- obtain USB permission
- open the serial CAT link
- get at least one safe read-only Kenwood-style reply from:
- `ID;`
- `FA;`
- `IF;`

This is still a probe/read-validation pass, not native TX.

## Recommended setup

- Rig profile: `Generic Kenwood Serial CAT`
- Serial CAT family: `Kenwood-style CAT`
- Baud rate: start with the rig's configured CAT baud rate
- Serial port hint: optional unless multiple serial devices are attached

## Bench steps

1. Open `Rig Setup`.
2. Select `Generic Kenwood Serial CAT`.
3. Confirm `Serial CAT` family is `Kenwood-style CAT`.
4. Set the expected CAT baud rate.
5. If needed, fill `Serial port hint`.
6. Press `Request Serial CAT USB Permission`.
7. Press `Test Serial CAT Connection`.
8. Copy the probe result into `KenwoodSerialCatBenchResults.md`.

## Good outcome

- Probe message says `Kenwood-style CAT responded`
- Reply contains a readable ASCII response such as:
- `ID...;`
- `FA...;`
- `IF...;`

## Common failure directions

- No response:
- confirm rig-side CAT is enabled
- confirm baud rate matches
- confirm the bridge is really CDC/ACM serial
- Permission failure:
- retry USB permission
- re-seat cable / OTG adapter
- Garbage or partial text:
- re-check serial settings and CAT family selection

## Next step after first success

- Repeat once with the exact model you care about
- then use that evidence to guide native TX/control work for the family
