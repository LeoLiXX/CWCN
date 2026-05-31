# Icom CI-V Bench Checklist

This checklist is the first native-serial Icom CI-V validation path in CWCN.

## Current scope

This path currently focuses on:

- USB CDC/ACM serial link detection
- USB permission flow
- baud / link configuration sanity
- CI-V address configuration
- safe read-only CI-V probe using a transceiver-ID query
- a short CI-V PTT pulse using the current standard PTT on/off frame assumption

This path does **not** yet mean full native Icom TX/CW support is complete.
The current pulse step assumes the common CI-V `1C 00` PTT on/off convention and should be confirmed on real hardware.

## Intended rigs

- `IC-705`
- `IC-7300`
- `IC-9700`
- nearby Icom rigs that expose a workable serial CI-V path

## Current field-verified IC-7300 combination

Field note recorded on `2026-05-31`:

- radio-side:
- `USB Keying (CW) = RTS`
- `USB SEND = DTR`
- app-side:
- enable both `RTS` and `DTR`

Current practical conclusion:

- for the current `IC-7300` bench path, CWCN should be tested with `RTS + DTR` both enabled on the App side
- this is the currently known working combination to keep CAT probing and actual CW keying behaving normally together

## Recommended order

1. Connect the Icom radio over a USB CAT / serial path that exposes a CDC/ACM serial interface.
2. In `Rig Setup`, select:
- `Generic Icom CI-V`
3. Confirm:
- `Serial CAT family = Icom CI-V`
- baud matches the radio
- `Serial port hint` is blank for auto-first-candidate, or exactly matches the target USB device name when needed
- `CI-V address` is set correctly
4. Press `Request Serial CAT USB Permission`.
5. After permission is granted, press `Test Serial CAT Connection`.
6. If the probe succeeds, press `Pulse Serial CAT TX/PTT`.
7. Record both the probe result and the pulse result.

## Expected outcomes

### Good

- USB permission succeeds
- serial link opens
- CI-V probe returns a readable binary reply to the transceiver-ID query
- short CI-V PTT pulse keys the radio briefly and then releases cleanly

### Common failures

- no USB device attached
- no CDC/ACM path found
- permission not granted
- wrong baud or radio-side CI-V config
- wrong CI-V address
- link opens but no readable CI-V reply
- CI-V pulse does not key the radio or does not release cleanly

## What to paste back

- selected profile
- CI-V address
- baud rate
- serial port hint
- permission result
- probe result text
- CAT TX/PTT pulse result text
- radio model
- anything notable on the radio side
