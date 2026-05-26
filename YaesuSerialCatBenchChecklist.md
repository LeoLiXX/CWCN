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

## Cross-rig support snapshot

### FT-891

- Recommended current CWCN route:
  - `CAT over USB serial`
  - `TX via dedicated keying/control`
  - `RX via phone microphone`
- Why:
  - the radio exposes a workable CAT/control path
  - its native USB path does not reliably give Android a usable audio-input device
- Current CWCN status:
  - serial CAT probe path exists
  - PTT / keyed TX path exists
  - frequency read path exists
  - hybrid route `CAT over USB + RX from phone mic` is now modeled explicitly
- If a separate Android-visible USB audio interface is attached:
  - RX can move to `USB external audio`
  - otherwise stay on `phone microphone`

### FT-710

- Recommended current CWCN route:
  - keep the existing Yaesu serial CAT path that already works on bench
  - do not force the FT-891-style hybrid route unless the real device lacks usable USB audio input
- Current CWCN status:
  - CAT / PTT / frequency-read path is the best current Yaesu native-serial baseline
  - FT-710 should remain on its current working path unless a device-specific issue is proven

### IC-7300

- Recommended current CWCN route today:
  - safest formal path: `Icom family (rigctld)`
  - native serial `Icom CI-V` is now wired for probe / PTT / frequency read, but still needs more real-device validation
- Current CWCN status:
  - CI-V probe path exists
  - CI-V PTT path exists
  - CI-V frequency-read path exists
  - RX audio is still separate from CAT:
    - if Android sees a usable USB audio input device, use `USB external audio`
    - otherwise use `phone microphone`

## FT8CN reference note

- FT8CN does not appear to have FT-891-specific USB audio routing logic.
- For non-network Yaesu paths such as `FT-891`, FT8CN RX falls back to generic `AudioRecord(DEFAULT)`.
- FT8CN TX audio is generic `AudioTrack` playback through the Android-selected output path.
- FT8CN only switches away from mic RX when the rig path itself supports wave/audio-over-CAT or a network-audio path.
