# Context Checkpoint

Last updated: 2026-04-23

## Purpose

This file is a compact handoff note for quickly resuming work without rereading long chat history.

## Current Git State

- Latest local commit: `0bc01f2`
- Commit message: `milestone: surface usb keyer failure stages explicitly`
- Working tree status at checkpoint: clean

## What Is Already In Good Shape

### TX v1 baseline

- `text -> Morse -> local sidetone` loop is already usable.
- TX page has backend selection, preview, presets, start/stop, and progress/status.

### USB keyer prototype

- `USB serial keyer` route is no longer a placeholder.
- Current TX USB route now has:
- permission request
- CDC/ACM device discovery
- explicit device selection
- `RTS/DTR` key-line selection
- persisted USB route preferences
- hotplug refresh
- strict locked-target behavior
- short bench macros
- explicit `Release Key Line`
- explicit failure-stage diagnostics

### Diagnostic stages already surfaced

- `usb-serial-ready`
- `usb-serial-target-missing`
- `usb-serial-no-device`
- `usb-serial-no-cdc`
- `usb-serial-no-permission`
- `usb-serial-open-failed`
- `usb-serial-claim-failed`
- `usb-serial-no-control-interface`

These are already shown through the TX USB route summary and used by recovery hints.

## Recent TX Milestone Trail

- `cc8946a` `milestone: add audio vox rig tx prototype`
- `664e094` `milestone: wire usb keyer permission and tx route controls`
- `2377a5e` `milestone: tighten usb keyer device targeting and refresh`
- `a3dce3a` `milestone: align usb tx semantics with hotplug refresh`
- `bea78ef` `milestone: add usb bench presets and key line recovery`
- `0bc01f2` `milestone: surface usb keyer failure stages explicitly`

## Current Priority Reset

The TX USB branch is now mature enough that more pure UI refinement is no longer the highest-value next step.

Recommended priority order now:

1. Real hardware / real input validation
2. RX real-input stability and observability
3. M2 draft/review/confirm/log workflow polish
4. TX compatibility expansion only after real bench feedback
5. Callsign intelligence as continuous enhancement, not mainline driver

## Why Priority Changed

- TX USB work has reached a "bench-ready tool" level.
- The next highest-value information now comes from real device behavior, not more abstract route plumbing.
- RX remains the product's core differentiator, so it should come back to the main line.

## Recommended Next Step

If continuing coding immediately, do this next:

1. Add a lightweight TX bench event/failure log on the TX page.
2. Use it during first real USB keyer / RTS-DTR hardware validation.
3. Record which diagnostic stage actually occurs on real hardware.

## Questions The Next Session Should Answer

- Does the target USB keyer work with plain CDC `SET_CONTROL_LINE_STATE`?
- Are failures mostly:
- permission
- OTG/cable/open
- interface claim
- wrong device class
- stuck line / timing / wiring
- After first bench results, is the next investment:
- broader USB-serial chipset compatibility
- richer hardware profile handling
- or TX safety/recovery improvements

## Important Files To Open First

- [CurrentProgress.md](/D:/Workshop/CWCN/CurrentProgress.md)
- [CodingPlan.md](/D:/Workshop/CWCN/CodingPlan.md)
- [TxActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/tx/TxActivity.java)
- [AndroidUsbSerialKeyerPortFactory.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/AndroidUsbSerialKeyerPortFactory.java)
- [UsbSerialKeyerRigControlAdapter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/UsbSerialKeyerRigControlAdapter.java)
- [CwTxRouteAdvisor.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/tx/CwTxRouteAdvisor.java)

## Resume Hint

When resuming, do not start by adding more generic TX UI controls.
Start from real bench diagnostics or return to RX real-input stability work.
