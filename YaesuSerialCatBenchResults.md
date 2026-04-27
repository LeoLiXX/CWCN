# Yaesu Serial CAT Bench Results

Paste each native-serial Yaesu CAT attempt here.

## Current Verified Findings

- FT-710 current verified standalone keying path:
  - CAT port: `Port 0`
  - Keying port: `Port 1`
  - CWCN key line: `RTS`
  - CWCN keying polarity: `Normal`
  - Radio menu: `PC KEYING = RTS`
  - Result: works normally
- FT-710 current verified `PC KEYING = DTR` finding on this Android USB serial path:
  - `RTS + DTR`: works normally
  - `RTS only`: TX state appears and power may be present, but sidetone is absent
  - `DTR only`: does not work
- This now matches the user's Ham Radio Deluxe DM780 observation on the same rig family:
  - when the radio menu is set to `PC KEYING = DTR`, practical CW operation requires dual-line assertion (`RTS + DTR`) rather than `DTR` alone.

## FT-710 Keying Matrix Notes

### Holding Key Lines 1.5s

#### RIG -> DTR

- Assert RTS only -> TX keys, power output present, no sidetone.
- Assert DTR only -> no TX.
- Assert RTS + DTR -> TX keys, power output present, sidetone present.

#### RIG -> RTS

- Assert RTS only -> TX keys, power output present, sidetone present.
- Assert DTR only -> no TX.
- Assert RTS + DTR -> TX keys, power output present, sidetone present.

### Sending `EEE` / `VVV`

#### RIG -> DTR

- Final accepted bench conclusion:
  - `RTS + DTR` -> normal
  - `RTS only` -> TX/RF without normal sidetone
  - `DTR only` -> does not work
- Earlier mixed short-pulse observations should be treated as superseded by the later FT-710 retest after the radio-side menu confusion was corrected.

#### RIG -> RTS

- Assert RTS only -> `EEE` / `VVV` test normal.
- Assert DTR only -> `EEE` / `VVV` test normal.
- Assert RTS + DTR -> `EEE` / `VVV` test normal.

## Session Template

### Radio

- Model:
- USB CAT path:
- Baud:
- Serial port hint:

### Permission

- Request sent:
- Granted / denied:

### Probe

- Selected profile:
- CAT family:
- Result:

### Notes

- Radio-side CAT settings:
- Anything unexpected:

### Raw Output

```text
Paste probe status text here.
```
