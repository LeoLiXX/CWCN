# Xiegu Support Plan

Last updated: 2026-05-26

## Goal

Add practical, field-usable Xiegu support to CWCN without regressing the currently working Yaesu paths.

Current protection targets:

- `FT-710`
- `FT-891`

## Core decision

Do not treat "Xiegu" as one uniform family in the first implementation.

Split support into two product lines:

1. `G90 / G90N / G90S`
2. `X6100 / X6200`

These two lines differ materially in:

- CAT transport expectations
- RX audio path
- how much can be handled over a single USB link

## Current repo reality

### Already present

- The native USB serial layer already recognizes at least `Xiegu X6100`.
- The app already has a production-facing mixed route mode:
  - `HYBRID_PHONE_RX`
  - RX from phone microphone
  - TX/CAT continue through the selected rig path
- Native serial CAT infrastructure already supports these protocol families:
  - `Yaesu-style CAT`
  - `Icom CI-V`
  - `Kenwood-style CAT`

### Not present yet

- No dedicated Xiegu profiles in the rig catalog
- No Xiegu family helper in rig-family detection
- No dedicated Xiegu CAT protocol family
- No Xiegu-specific setup guidance in `Rig Setup`
- No explicit X6200 device recognition in the current USB serial mini driver table

## Product model split

### Line A: G90 / G90N / G90S

Working assumption for now:

- CAT is Xiegu's CI-V-like control path
- RX audio is not as "single-cable native" as X6100/X6200
- practical deployment may rely on:
  - external audio interface
  - DE-19 / similar expansion accessory
  - or phone microphone fallback

CWCN implication:

- This line should not be the first implementation target.
- It needs a more careful CAT + audio matrix.

Practical field interpretation:

- the blue Xiegu USB cable should be treated primarily as `CAT`, not as a guaranteed RX audio path
- for CWCN, `RX` means radio audio flowing from the rig into Android
- if Android can see a real external recording-capable audio device over OTG, CWCN can use that as rig RX
- otherwise CWCN should fall back to phone microphone RX

Current recommended route shapes for G90 family:

1. `USB CAT + external RX audio`
2. `USB CAT + phone microphone RX`

Avoid assuming:

- that "USB connected" also means "USB audio connected"
- that a passive 3.5 mm splitter automatically gives Android a usable RX source
- that the rig's audio input path and RX audio output path are the same thing

Meaning of audio direction:

- `rig -> phone` audio path: RX
- `phone -> rig` audio path: TX
- ACC or line-in style paths on the rig are usually TX injection paths, not RX capture paths

CWCN product consequence for G90 / G90S:

- phase-one value is mostly CAT and route modeling
- RX should be productized as:
  - external USB audio when Android exposes a recording source
  - otherwise `HYBRID_PHONE_RX`
- TX remains a separate design decision and should not be inferred from FT8-style PTT + audio behavior

### Line B: X6100 / X6200

Working assumption for now:

- Better single-USB integration story
- More suitable for first-class product support in CWCN
- Better candidate for:
  - USB serial CAT
  - USB external RX audio
  - mixed fallback when USB audio is unavailable

CWCN implication:

- This should be Phase 1.

## Phase plan

## Phase 1

Target:

- `X6100`
- `X6200`

Scope:

1. Add productized rig profiles
2. Reuse existing CAT infrastructure instead of inventing a new Xiegu protocol immediately
3. Keep existing Yaesu/Icom/Kenwood behavior stable
4. Expose clear route guidance in setup/status text

Implementation direction:

- Add rig profiles for:
  - `Xiegu X6100`
  - `Xiegu X6200`
- Use existing serial CAT transport
- First reuse `Icom CI-V` family settings as the least-wrong current control bucket
- Preserve mixed route support:
  - standard auto
  - hybrid phone RX
- Add setup/status copy that explains:
  - CAT over USB serial
  - RX preferably via USB external audio when available
  - fallback to phone microphone when required

Nice-to-have in the same phase if low risk:

- add X6200 USB VID/PID recognition once verified
- add Xiegu naming in route summaries

Out of scope for Phase 1:

- new `CatProtocolFamily.XIEGU_CIV`
- native Xiegu CW text TX over CAT
- G90 family formal support

## Phase 2

Target:

- formal Xiegu CAT family support

Scope:

1. Add `CatProtocolFamily.XIEGU_CIV` if real radios prove it is warranted
2. Add Xiegu-specific probe behavior
3. Add Xiegu-specific frequency-read handling if needed
4. Decide whether any Xiegu family supports real native CW TX over CAT, or whether production TX should remain:
  - dedicated key line
  - or PTT + audio only

## Phase 3

Target:

- `G90 / G90N / G90S`

Scope:

1. Define the practical supported matrix
2. Separate CAT, RX, and TX expectations clearly
3. Productize the external-audio / fallback story

Detailed matrix to land in this phase:

- `CAT`
  - preferred: native USB serial CAT
- `RX`
  - preferred: external OTG audio interface that Android recognizes as an input device
  - fallback: phone microphone RX
- `TX`
  - to be decided independently from CAT
  - should not assume that FT8-style digital-audio TX maps directly to CW keying requirements

Reference connection topologies to preserve in product language:

1. `G90S USB -> Android OTG` for CAT
2. `G90S audio out -> external USB audio input -> Android OTG` for rig RX
3. `G90S speaker / monitor audio -> phone mic` only as fallback RX

## First implementation checklist

1. Add Xiegu profiles to `RigProfileCatalog`
2. Add Xiegu family helper to `RigProfileFamilies`
3. Update route/status formatting to mention Xiegu explicitly where useful
4. Re-check `Rig Setup` route-mode availability for Xiegu serial CAT profiles
5. Add focused unit coverage for:
  - profile lookup
  - family detection
  - route summary behavior

## Risks

### Risk 1

Using `Icom CI-V` as a temporary Xiegu bucket may be directionally correct but not fully accurate.

Mitigation:

- keep this only as Phase 1 scaffolding
- do not over-promise native CW TX
- keep setup/status copy explicit

### Risk 2

X6200 USB enumeration may differ from X6100.

Mitigation:

- do not assume the same PID
- add actual device recognition only after confirmation

### Risk 3

Touching shared serial CAT code could regress Yaesu or current Icom behavior.

Mitigation:

- prefer profile-layer additions first
- avoid changing shared command-building logic unless necessary
- run focused unit tests after each step

## Success criteria for Phase 1

- X6100/X6200 can be selected as named profiles in `Rig Setup`
- route summaries read like product features instead of generic placeholders
- no regression to current Yaesu paths
- focused rig-related unit tests pass
