# TX USB Bench Checklist

Last updated: 2026-04-23

## Purpose

This checklist is for the first real-hardware validation pass of the `USB serial keyer` TX route.

The goal is not to prove every edge case in one sitting.
The goal is to answer these questions quickly:

1. Does the target device appear as a usable candidate in the app?
2. Does Android USB permission work as expected?
3. Does the route reach `usb-serial-ready`?
4. Does `RTS` or `DTR` actually key the external path?
5. If it fails, which diagnostic stage and event sequence appears in `Copy Report`?

## Required Hardware

- Android phone or tablet with the current CWCN debug build
- OTG adapter or direct USB-C cable as needed
- One target USB CDC/ACM keyer or USB serial interface
- Visible or audible keying confirmation:
  - rig sidetone
  - external key line monitor
  - scope / logic probe
  - or a safe test load path

If real hardware is not available yet, use the in-app `Mock USB Serial Keyer Adapter` backend.
That mock path can still validate most of P0:

- no-device baseline
- no-permission to ready transition
- ready state
- open-failed / claim-failed / no-control-interface
- locked-target-missing
- `RTS` / `DTR` path behavior at the app routing layer

## Safety Notes

- Start with the shortest possible bench macro.
- Prefer the built-in `Load DIT Test` first.
- Use conservative `WPM` before trying longer patterns.
- If the line looks stuck, use `Release Key Line` before reconnecting hardware.
- Avoid over-the-air validation until the hardware path is understood.

## Before Starting

Confirm all of the following:

- The TX page opens normally.
- `USB serial keyer` backend is visible in the backend selector.
- `Mock USB Serial Keyer Adapter` backend is also visible in the backend selector.
- `Bench Summary`, `Bench Log`, and `Copy Report` are visible.
- The device is allowed to show Android USB permission prompts.

## Mock-First P0 Path

If you do not have real hardware yet, use this order first:

1. Select `Mock USB Serial Keyer Adapter`
2. Use the `Mock bench scenario` spinner inside the USB route panel
3. Run these scenario states in order:
   - `No Device Attached`
   - `Device Attached, No Permission`
   - press `Request USB Permission`
   - `Open Failed`
   - `Claim Failed`
   - `Locked Target Missing`
4. For `Ready`, run both:
   - `RTS` + `Load DIT Test`
   - `DTR` + `Load DIT Test`
5. Copy one report for each interesting state into `TxUsbBenchResults.md`

This does not replace real hardware validation, but it lets us verify:

- UI state transitions
- diagnostic-stage semantics
- bench summary wording
- report structure
- recovery guidance consistency

## Test Matrix

Run the following in order.
After each major step, use `Copy Report` and paste the result into `TxUsbBenchResults.md`.

### T1. No Device Attached Baseline

Steps:

1. Select `USB serial keyer`.
2. Confirm no target USB device is attached.
3. Press `Refresh USB Devices`.
4. Copy the report.

Expected:

- Diagnostic stage should be `usb-serial-no-device` or equivalent no-device state.
- Bench summary should say the USB route is blocked because no device is attached.

### T2. Device Attached Before Permission

Steps:

1. Attach the USB device.
2. Wait for attach handling or press `Refresh USB Devices`.
3. Do not grant permission yet if Android prompts later.
4. Copy the report.

Expected:

- Candidate device should appear.
- Diagnostic stage should normally be `usb-serial-no-permission`.
- Bench log should show attach and/or refresh events.

### T3. Permission Grant Path

Steps:

1. Press `Request USB Permission`.
2. Grant permission in the Android dialog.
3. Wait for the route to refresh.
4. Copy the report.

Expected:

- Bench log should show permission request then permission granted.
- Diagnostic stage should move to `usb-serial-ready` if the device is compatible.
- If not ready, the exact blocking stage should now be visible.

### T4. RTS Key Line Test

Steps:

1. Set the control line to `RTS`.
2. Press `Load DIT Test`.
3. Press `Start TX`.
4. Observe whether the external keying path reacts.
5. Copy the report.

Expected:

- If wiring matches `RTS`, the external path should key briefly.
- Bench log should show `Start requested`, playback start, and either complete or error.

### T5. DTR Key Line Test

Steps:

1. Set the control line to `DTR`.
2. Press `Load DIT Test`.
3. Press `Start TX`.
4. Observe whether the external keying path reacts.
5. Copy the report.

Expected:

- If wiring matches `DTR`, the external path should key briefly.
- Compare this report directly against the `RTS` report.

### T6. Short Pattern Test

Steps:

1. Keep the working key line selected.
2. Press `Load VVV Test`.
3. Press `Start TX`.
4. Observe timing and stuck-line behavior.
5. Copy the report.

Expected:

- Route should remain stable through a slightly longer sequence.
- If timing or line recovery is bad, the report should capture the route state and recent event trail.

### T7. Release Key Line Recovery

Steps:

1. Press `Release Key Line`.
2. Press `Refresh USB Devices`.
3. Copy the report.

Expected:

- Bench log should show release plus refresh.
- Route should recover to the best available idle state.

### T8. Hotplug / Locked Target Behavior

Steps:

1. If multiple devices are available, lock to one explicit device.
2. Unplug that device.
3. Observe summary and route state.
4. Reconnect it or switch back to `Auto`.
5. Copy the report.

Expected:

- Locked-target detach should surface `usb-serial-target-missing`.
- Recovery path should be obvious in bench summary and next-step hints.

## What To Capture

For each important run, record:

- hardware name
- cable / OTG path
- Android device model
- selected key line
- whether permission was granted
- whether physical keying was observed
- copied report text

## Minimal Success Criteria For P0

P0 is considered successful if we can collect at least:

1. One `no-device` or `no-permission` report
2. One `ready` report
3. One actual `RTS` or `DTR` keying result
4. One recovery report after refresh / release / detach

If running in mock mode only, the equivalent target is:

1. One `no-device` mock report
2. One `no-permission` then `ready` transition report
3. One `open-failed` or `claim-failed` report
4. One `RTS`/`DTR` mock TX report
5. One `locked-target-missing` report

## Decision Rules After P0

Use the collected reports to choose the next branch:

- Mostly permission or operator-flow issues:
  prioritize TX safety/recovery UX
- Mostly open/claim/interface failures:
  prioritize USB compatibility/profile work
- Mostly successful on CDC/ACM hardware:
  hold TX steady and shift priority back to RX real-input work
