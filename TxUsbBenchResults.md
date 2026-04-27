# TX USB Bench Results

Last updated: 2026-04-23

Use this file to store real-hardware P0 results.
Copy one block per scenario.

## Session Metadata Template

- Date:
- Android device:
- CWCN build:
- USB device:
- OTG / cable path:
- External confirmation method:
- Notes:

## Result Block Template

### Scenario: 

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
Paste Copy Report output here.
```

## Captured Results

### Scenario: mock no-permission to ready with DTR DIT playback

- Backend: Mock USB Serial Keyer Adapter
- Selected device mode: Auto / first available
- Key line: DTR
- Physical keying observed: N/A (mock route)
- Outcome: Passed. The route advanced from `usb-serial-no-device` to `usb-serial-no-permission`, then to `usb-serial-ready`, and completed a short DIT playback successfully.
- Decision: Treat the mock permission-to-ready flow and mock DTR TX path as validated. Next priority is failure-path coverage for `Open Failed`, `Claim Failed`, and `Locked Target Missing`.

```text
== Bench Summary ==
Last TX completed. USB keyer route is ready for a short bench transmission.
Next: Load DIT Test or VVV Test, verify the key line wiring, then press Start TX.

== Backend ==
Backend: Mock USB Serial Keyer Adapter
Route: Simulate a USB serial keyer route and toggle the DTR control line without external hardware.
Ready: yes
Availability: Mock mode: Ready.
Uses WPM: yes
Uses tone frequency: no
Supports live profile: yes
Progress snapshots: yes

== Plan ==
WPM: 12
Tone: 650 Hz
Dot: 100 ms
Elements: 5
Estimated duration: 1.700s

== USB Route ==
Target mode: Auto / first available
Target state: Auto target available
Diagnostic stage: Ready (usb-serial-ready)
Active target: mock-usb-keyer-1 (VID:PID 1D50:60C7)
Availability: Mock mode: Ready.
Next action: Load DIT Test or VVV Test, verify the key line wiring, then press Start TX.

== Playback Status ==
State: completed
Status: Rig adapter accepted text TX request.
Current element: -
Tone active: no
Elapsed: 1.700s / 1.700s

== Playback Progress ==
Progress: 100%
Elements: 5 / 5

== Bench Log ==
[19:10:08] SESSION  TX console opened.
[19:10:08] BACKEND  Selected Local Sidetone (local-sidetone).
[19:10:31] BACKEND  Selected Mock USB Serial Keyer Adapter (rig-text:usb-serial-keyer-mock).
[19:10:31] USB  Diagnostic stage -> No USB device (usb-serial-no-device). Next: Attach a CDC/ACM USB device, then press Refresh USB Devices.
[19:10:32] USB  Device selector switched to Auto target mode.
[19:10:32] USB  Key line changed to RTS.
[19:10:32] MOCK  Mock USB scenario switched to No Device Attached.
[19:10:38] MOCK  Mock USB scenario switched to Device Attached, No Permission.
[19:10:38] USB  Diagnostic stage -> Permission missing (usb-serial-no-permission). Next: Press Request USB Permission. If the line may be stuck, use Release Key Line after permission is granted.
[19:10:39] USB  Permission request sent for the current target device.
[19:10:39] USB  Diagnostic stage -> Ready (usb-serial-ready). Next: Load DIT Test or VVV Test, verify the key line wiring, then press Start TX.
[19:10:39] MOCK  Mock USB scenario switched to Ready.
[19:10:41] USB  Key line changed to DTR.
[19:10:43] USB  Loaded bench preset BENCH_DIT at 12 WPM.
[19:10:48] TX  Start requested on Mock USB Serial Keyer Adapter using 5 elements.
[19:10:48] TX  Playback started. Sending text to rig adapter: Mock USB Serial Keyer Adapter
[19:10:50] TX  Playback completed. Rig adapter accepted text TX request.
```

### Scenario: mock failure-sweep plus recovery to ready with DTR pattern playback

- Backend: Mock USB Serial Keyer Adapter
- Selected device mode: Locked target `mock-usb-keyer-missing`, then returned to Ready
- Key line: DTR
- Physical keying observed: N/A (mock route)
- Outcome: Passed. A single session exercised `Open Failed`, `Claim Failed`, `No Control Interface`, `Locked Target Missing`, and `No CDC Candidate`, then returned to `usb-serial-ready` and completed a longer DTR pattern playback successfully.
- Decision: Treat mock P0 as functionally coherent. The remaining TX uncertainty is no longer mock route semantics, but real hardware behavior. One UX gap is still visible: when multiple failure scenarios are swept in one session, the copied report header reflects the final current state while earlier failure stages only remain in the bench log timeline.

```text
== Bench Summary ==
Last TX completed. USB keyer route is ready for a short bench transmission.
Next: Load DIT Test or VVV Test, verify the key line wiring, then press Start TX.

== Backend ==
Backend: Mock USB Serial Keyer Adapter
Route: Simulate a USB serial keyer route and toggle the DTR control line without external hardware.
Ready: yes
Availability: Mock mode: Ready.
Uses WPM: yes
Uses tone frequency: no
Supports live profile: yes
Progress snapshots: yes

== Plan ==
WPM: 15
Tone: 650 Hz
Dot: 80 ms
Elements: 95
Estimated duration: 13.200s

== USB Route ==
Target mode: Locked to mock-usb-keyer-missing
Target state: Locked target attached
Diagnostic stage: Ready (usb-serial-ready)
Active target: mock-usb-keyer-missing (VID:PID 1D50:60C7)
Availability: Mock mode: Ready.
Next action: Load DIT Test or VVV Test, verify the key line wiring, then press Start TX.

== Playback Status ==
State: completed
Status: Rig adapter accepted text TX request.
Current element: -
Tone active: no
Elapsed: 13.200s / 13.200s

== Playback Progress ==
Progress: 100%
Elements: 95 / 95

== Bench Log ==
[19:26:58] TX  Stop pressed while Mock USB Serial Keyer Adapter was already idle.
[19:26:58] USB  Key line released and USB route state refreshed.
[19:27:03] TX  Start requested on Mock USB Serial Keyer Adapter using 5 elements.
[19:27:03] TX  Playback started. Sending text to rig adapter: Mock USB Serial Keyer Adapter
[19:27:05] TX  Playback completed. Rig adapter accepted text TX request.
[19:27:11] MOCK  Mock USB scenario switched to Open Failed.
[19:27:11] USB  Diagnostic stage -> Open failed (usb-serial-open-failed). Next: Refresh USB Devices, re-seat the cable or OTG adapter, then retry the short DIT test.
[19:27:12] USB  Permission request sent for the current target device.
[19:27:20] MOCK  Mock USB scenario switched to Claim Failed.
[19:27:20] USB  Diagnostic stage -> Interface claim failed (usb-serial-claim-failed). Next: Release Key Line, refresh the USB route, and make sure no other app is holding the interface.
[19:27:23] USB  Permission request sent for the current target device.
[19:27:33] MOCK  Mock USB scenario switched to No Control Interface.
[19:27:33] USB  Diagnostic stage -> Control interface missing (usb-serial-no-control-interface). Next: This USB device does not expose the expected CDC control interface. Try another keyer profile or device.
[19:27:40] MOCK  Mock USB scenario switched to Locked Target Missing.
[19:27:40] USB  Diagnostic stage -> Locked target missing (usb-serial-target-missing). Next: Reconnect the locked USB keyer or switch the device selector back to Auto, then press Refresh USB Devices.
[19:27:46] MOCK  Mock USB scenario switched to No CDC Candidate.
[19:27:46] USB  Diagnostic stage -> No CDC/ACM keyer (usb-serial-no-cdc). Next: Attach a CDC/ACM-compatible USB serial/keyer device, then refresh the route.
[19:27:50] MOCK  Mock USB scenario switched to Ready.
[19:27:50] USB  Diagnostic stage -> Ready (usb-serial-ready). Next: Load DIT Test or VVV Test, verify the key line wiring, then press Start TX.
[19:27:52] USB  Key line changed to DTR.
[19:27:54] USB  Loaded bench preset BENCH_PATTERN at 15 WPM.
[19:28:04] TX  Start requested on Mock USB Serial Keyer Adapter using 95 elements.
[19:28:04] TX  Playback started. Sending text to rig adapter: Mock USB Serial Keyer Adapter
[19:28:17] TX  Playback completed. Rig adapter accepted text TX request.
```

## Current Focus

Mock P0 is now good enough for this phase.
The highest-value next step is no longer more mock routing coverage, but choosing one of:

1. Real USB hardware bench when equipment exists
2. Small TX observability polish around multi-failure report capture
3. Shift priority back to RX real-input stability

## Suggested Scenarios

### Scenario: mock open-failed

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```

### Scenario: mock claim-failed

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```

### Scenario: mock locked-target-missing

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```

### Scenario: mock no-device baseline

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```

### Scenario: mock no-permission to ready

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```

### Scenario: no-device baseline

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```

### Scenario: permission granted and route ready

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```

### Scenario: RTS test

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```

### Scenario: DTR test

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```

### Scenario: recovery after release or detach

- Backend:
- Selected device mode:
- Key line:
- Physical keying observed:
- Outcome:
- Decision:

```text
```
