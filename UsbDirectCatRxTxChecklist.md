# USB Direct CAT RX/TX Checklist

Last updated: 2026-05-09

## Purpose

This checklist is the current real-device validation pass for the `USB direct CAT` route in CWCN.

It focuses on the current actual implementation status:

1. `USB RX` now exists as a first formal path through Android external audio input routing
2. `USB direct CAT TX` is partially usable today
3. `Yaesu-style serial CAT` is the main native-CAT TX path worth validating first
4. `Icom CI-V` and `Kenwood-style serial CAT` are not yet at the same completion level

This checklist is meant to answer:

1. Can the Android device route the rig's USB audio into CWCN RX?
2. Does `Operate` really stop using the phone microphone in `USB/External Audio` mode?
3. Can the selected serial CAT route pass probe / readiness / permission cleanly?
4. Can the current direct CAT TX path actually key and stop cleanly?
5. If a step fails, which exact stage fails: Android routing, USB permission, serial CAT readiness, or TX control?

## Current Scope

### In scope now

- `USB RX` through Android-routed external USB audio input
- `Operate` page RX mode switching:
  - `Auto`
  - `Phone Microphone`
  - `USB/External Audio`
- `Yaesu-style` native serial CAT TX first pass
- `RTS/DTR` keying if the rig path exposes or uses a separate keying line
- stop / interrupt behavior during TX

### Not fully complete yet

- full native `Icom CI-V` text CW TX
- full native `Kenwood-style` text CW TX
- proof that every Android phone model routes every USB audio rig correctly
- advanced hotplug / re-route recovery across all devices

## Required Hardware

- Android phone or tablet with current CWCN debug build
- OTG adapter or direct USB-C cable as needed
- one rig that exposes:
  - USB CAT serial
  - and ideally USB audio
- a safe CW TX validation path:
  - sidetone
  - monitor receiver
  - dummy load
  - or other safe bench setup

## Safety Notes

- Start with RX verification first.
- For TX, start with the shortest possible text.
- Use low-risk bench text first:
  - single DIT
  - `VVV`
  - then short real text
- Do not start with a long over-the-air transmission.
- If TX looks stuck, stop immediately and record the exact visible UI state.

## Before Starting

Confirm all of the following:

- The CWCN debug APK installs and launches normally.
- The rig is visible to Android over USB.
- The rig USB CAT path is physically connected.
- If the rig also exposes USB audio, the same cable/path is connected and stable.
- `Rig Setup` opens normally.
- `Operate` opens normally.
- `Settings` opens normally.

## Phase A. RX Mode Setup

### A1. Confirm RX mode selector exists

Steps:

1. Open `Settings`
2. Go to the `CW Defaults` section
3. Confirm there is an RX input selector with:
   - `Auto`
   - `Phone Microphone`
   - `USB/External Audio`

Expected:

- The selector is visible
- The value can be changed and saved

### A2. Save `USB/External Audio`

Steps:

1. Select `USB/External Audio`
2. Save CW/RX defaults
3. Return to `Operate`

Expected:

- The setting persists
- `Operate` does not immediately ask for microphone permission just because RX starts

## Phase B. USB RX Validation

### B1. No USB audio baseline

Steps:

1. Set RX mode to `USB/External Audio`
2. Disconnect the rig USB audio path, or use a baseline where Android has no usable USB audio input
3. Open `Operate`

Expected:

- RX should not silently fall back to the phone microphone
- UI should indicate external / USB RX is waiting or unavailable
- Random room noise should not be decoded as if the phone mic were active

### B2. USB audio connected

Steps:

1. Connect the rig over the intended USB path
2. Ensure the rig exposes USB audio to Android
3. Open `Operate`
4. Observe top status, source chip, and spectrum behavior

Expected:

- RX source should behave like `USB` / `External Audio`, not phone mic
- Normal room noise should no longer dominate if the rig audio path is the real routed input
- Spectrum should respond to rig audio, not to ambient phone-mic noise

### B3. Fixed-tone receive sanity

Steps:

1. Keep RX mode on `USB/External Audio`
2. Use a known CW sidetone source near the configured tone
3. Watch:
   - spectrum line
   - waterfall
   - decode text

Expected:

- Tone tracking should stay near the expected rig sidetone
- It should not immediately drift to unrelated ambient peaks the way phone mic did
- Decoded output should be materially better than office-noise phone-mic baseline

### B4. Auto vs explicit RX mode comparison

Steps:

1. Test `Phone Microphone`
2. Test `USB/External Audio`
3. Test `Auto` with pinned rig
4. Compare observed behavior

Expected:

- `Phone Microphone` should clearly behave like phone ambient capture
- `USB/External Audio` should clearly behave like routed rig audio input
- `Auto` with pinned rig should behave like external audio, not phone mic

## Phase C. USB Direct CAT Readiness

### C1. Rig Setup and permission

Steps:

1. Open `Rig Setup`
2. Pin the intended serial CAT profile
3. Grant Android USB permission if requested
4. Run the CAT readiness / probe flow

Expected:

- USB permission succeeds
- The route reaches a usable ready/probe state
- The app does not lose RX configuration when returning to `Operate`

### C2. Probe result capture

Record:

- profile name
- CAT family
- baud rate
- USB permission outcome
- probe result text
- whether the target rig actually responded

## Phase D. TX Validation

### D1. Yaesu-style native serial CAT first pass

Steps:

1. Use a Yaesu-style serial CAT profile
2. Confirm probe/readiness succeeds
3. In `Operate`, send the shortest test text first
4. Observe:
   - TX start
   - on-rig indication
   - stop behavior

Expected:

- TX starts from `Operate`
- the rig keys
- TX can be stopped cleanly
- playback/highlight state remains coherent

### D2. Short real text

Steps:

1. Use a short text such as:
   - one DIT-equivalent short text
   - `VVV`
   - a very short real message
2. Start TX
3. Stop during the middle once
4. Start again

Expected:

- Start works
- Stop works
- Restart works
- No stuck TX state remains in UI or on the rig

### D3. RX/TX coexistence observation

Steps:

1. Keep the USB RX path active
2. Start a short TX
3. Watch what happens to RX state before, during, and after TX

Expected:

- Behavior should be understandable and repeatable
- If RX pauses, the UI should make that obvious
- If RX resumes, it should resume cleanly

Record whether:

- RX fully pauses during TX
- RX partially survives during TX
- RX fails to resume correctly after TX

## Phase E. Family Status Reality Check

### E1. Icom CI-V

Current expectation:

- treat current native serial CI-V as partial
- do not assume full production text CW TX is complete

Record:

- whether probe works
- whether minimal PTT behavior works
- whether text TX is actually usable or still incomplete

### E2. Kenwood-style serial CAT

Current expectation:

- treat current native Kenwood serial CAT as not yet full TX-complete

Record:

- whether probe works
- whether readiness looks good
- whether usable text TX is still missing

## What To Record

For each real-device pass, record:

- Android device model
- Android version
- rig model
- cable / OTG path
- pinned rig profile
- RX input mode used
- whether Android clearly routed USB audio
- whether RX behaved like USB audio or phone mic
- whether Yaesu native CAT TX worked
- whether TX stop worked
- any stuck or confusing state

## Minimal Success Criteria For This Pass

This pass is successful if we can prove all of the following:

1. `USB/External Audio` mode no longer behaves like phone microphone fallback
2. `Auto` with a pinned rig prefers the external USB audio path
3. `Yaesu-style` direct serial CAT TX can start and stop from `Operate`
4. We can clearly distinguish:
   - completed now
   - usable but fragile
   - still pending

## Current Expected Summary

As of this checklist revision, the expected truth is:

- `USB RX`: first formal path implemented, needs real-device validation
- `Yaesu direct serial CAT TX`: first usable validation target
- `Icom CI-V native TX`: partial / not fully complete
- `Kenwood native TX`: still behind Yaesu

