# Yaesu Serial CAT Bench Checklist

This checklist is the first native-serial Yaesu CAT validation path in CWCN.

## Current scope

This path currently focuses on:

- USB CDC/ACM serial link detection
- USB permission flow
- baud / link configuration sanity
- safe read-only CAT probe such as `FA;` / `IF;`
- a short native CAT TX/PTT pulse through `TX1;` / `TX0;`
- dedicated keyed-CW validation through a separate RTS/DTR keying port when the radio exposes one

This path does **not** yet mean full native Yaesu TX/CW support is complete.

## FT-710 practical note

- Current accepted FT-710 result:
  - if the radio menu is `PC KEYING = RTS`, `RTS` keying works normally
  - if the radio menu is `PC KEYING = DTR`, `RTS + DTR` works normally
  - if the radio menu is `PC KEYING = DTR`, `RTS only` may show TX/RF but no normal sidetone
  - if the radio menu is `PC KEYING = DTR`, `DTR only` does not work
- This behavior now matches the user's DM780 comparison well enough to treat it as the current FT-710 baseline.

## Intended rigs

- `FT-710`
- nearby Yaesu FT-family rigs that expose a workable USB CDC/ACM CAT path

## Recommended order

1. Connect the Yaesu radio over a USB CAT path that exposes a CDC/ACM serial interface.
2. In `Rig Setup`, select:
- `Generic Yaesu Serial CAT`
3. Confirm:
- `Serial CAT family = Yaesu-style CAT`
- baud matches the radio
- `Serial port hint` is blank for auto-first-candidate, or exactly matches the target USB device name when needed
4. Press `Request Serial CAT USB Permission`.
5. After permission is granted, press `Test Serial CAT Connection`.
6. If the probe succeeds and the rig looks safe to key briefly, press `Pulse Serial CAT TX/PTT`.
7. If the radio exposes a dedicated keying port, choose the keying port and validate keyed CW behavior:
- for `PC KEYING = RTS`, start with `Assert RTS during keying`
- for `PC KEYING = DTR`, start with `Assert RTS during keying` and `Assert DTR during keying`
8. Use `Pulse Keying Port`, `Hold Keying Line`, or `Short Pulse Lab` as needed.
9. Record both the probe result and the keyed-CW result.

## Expected outcomes

### Good

- USB permission succeeds
- serial link opens
- CAT probe returns a readable reply to `FA;` or `IF;`
- short CAT TX/PTT pulse keys the radio briefly and then releases cleanly

### Common failures

- no USB device attached
- no CDC/ACM path found
- permission not granted
- wrong baud or radio-side CAT config
- link opens but no readable CAT reply
- brief CAT TX/PTT pulse does not key the radio or does not release cleanly
- keyed CW enters TX but no sidetone is heard
- keyed CW flashes TX on short pulses but does not behave like normal CW

## What to paste back

- selected profile
- serial CAT family
- baud rate
- serial port hint
- permission result
- probe result text
- CAT TX/PTT pulse result text
- keying-port result text
- radio model
- anything notable on the radio side
