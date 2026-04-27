# Yaesu rigctld Bench Checklist

This checklist is the current shortest formal test path for Yaesu FT-series radios in CWCN, including:

- `FT-710`
- `FT-891`
- `FT-991A`
- nearby Yaesu rigs that are already usable through `Hamlib rigctld`

## Target

Validate that CWCN can send a short CW bench macro through:

- `Rig Setup -> Yaesu FT-Series via rigctld`
- `TX Console -> Hamlib rigctld Adapter`

without committing yet to a native Android Yaesu serial CAT implementation.

## Recommended order

1. Prepare a working `rigctld` endpoint that is already bound to the target Yaesu radio.
2. In `Rig Setup`, choose and pin:
- `Yaesu FT-Series via rigctld`
3. In the same screen, confirm:
- `Network CAT family = Hamlib rigctld`
- correct host
- correct port
4. Press `Test rigctld Connection`.
5. Only after probe succeeds, open `TX Console`.
6. Confirm TX auto-selects `Hamlib rigctld Adapter`.
7. Start with a very short macro:
- `DIT test`
- or a short `VVV`
8. Only after short TX is clean, try a slightly longer CQ macro.

## Suggested initial TX settings

- `WPM`: `12-20`
- `Tone`: `600-700 Hz`
- Keep the first transmission below `10-15s`

## What to record

Paste back:

- `Rig Setup` probe result
- `TX Console` backend summary
- `Playback status`
- any `bench log` or failure note
- the radio model actually used
- whether the path was:
- direct USB CAT into a local rigctld host
- LAN/bridge into rigctld

## Current scope / limits

- This path is for practical bench validation now.
- It does **not** mean native Android Yaesu CAT is finished.
- If the probe succeeds but TX fails, the next likely issues are:
- rigctld command support / model mapping
- local daemon binding to the wrong radio
- host/port mismatch
- radio-side CAT/PTT/CW permissions or mode state
