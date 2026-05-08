# Production MVP Plan

Last updated: 2026-05-08

## Goal

Build the first normal-user path that works without opening Debug tools.

Target user journey:

1. Open app
2. Configure route in `Settings` / `Rig Setup`
3. Enter `Operate`
4. Observe live RX text and draft state
5. Send a reply or macro
6. Confirm log entry
7. Export ADIF later

## Core Principles

1. `Operate` is the primary production screen.
2. `InputDebugActivity` and bench tools stay behind developer mode.
3. We support one recommended real-world route first, not every route equally.
4. RAW/RX fidelity stays upstream; production UI should not hide uncertainty with fake polish.

## 2026-05-08 RX Strategy Correction

Real-device validation changed the recommended RX direction materially.

Observed problems from phone-microphone testing:

- ordinary office noise can still trigger large amounts of false `E / T / I` style output
- the tracked tone can be dragged away from the intended operator-selected region
- radio sidetone decoding through `Phone Mic` is not yet reliable enough to serve as the default production RX assumption
- PTT/key-line click transients are a realistic front-end contaminant and must be treated explicitly

Working interpretation:

- the current `Phone Mic -> broad acquisition -> decode` assumption is too optimistic for production
- for normal CW use, a `known target tone` is more trustworthy than broad free-running acquisition
- the next RX work should prefer `narrow, conservative, no-false-text` behavior over aggressive auto-adoption

### Revised RX Priorities

1. `Fixed Tone / Narrow Lock` becomes the preferred production RX mode

- center on the configured operator tone, e.g. `700 Hz`
- use a narrow acquisition/retune window around that tone
- do not let ordinary room noise pull the decode center freely

2. `No confident tone -> no decode text`

- when confidence is weak, keep the UI in a listening/unlocked state
- do not emit long false `E/E/T/T` streams just because some energy crossed the amplitude gate

3. `PTT click / transient blanking`

- explicitly ignore the first short transient region after key/PTT activity
- require short stability before accepting a candidate tone into the decode path

4. `Microphone source mode` becomes a first-class setting

- try, in order, where supported:
  - `UNPROCESSED`
  - `VOICE_RECOGNITION`
  - `MIC`
- production validation should compare these modes explicitly instead of assuming stock `MIC` is acceptable

5. `Phone Mic` remains a fallback and diagnostic path, not the primary production truth source

- useful for demos, emergency fallback, and developer observation
- not yet trustworthy enough to define the main RX success criteria

### Immediate Next RX Build Order

1. add configurable microphone input-source mode
2. add `Fixed Tone / Auto Track` RX mode selection
3. implement narrow-lock behavior around configured tone in fixed-tone mode
4. add no-lock/no-confident-tone gating before decoder text emission
5. add PTT/transient blanking window

### Explicit Non-Priority For The Next Step

- broadening the tone-tracking range is not the first response to the current field report
- the main issue is not failure to include the real tone in the search range
- the main issue is false acquisition and false decode under weak-confidence conditions

## Scope Order

### P0. Separate Production From Debug

Goal:

- normal users should never need to enter Debug

Tasks:

1. Remove debug-oriented wording from normal screens
2. Keep developer entrances only in `Settings -> Advanced / Developer`
3. Keep rig bench / RX debug / TX console behind developer mode
4. Make `Rig Setup` read as user setup first, bench second

Success:

- no normal screen tells the user to "go back to Debug"

### P1. Formalize `Operate` As The Main Product Screen

Goal:

- turn `Operate` into the first real RX/TX page

Tasks:

1. Top status: route, RX readiness, tone/WPM summary
2. Main RX area: live receive text
3. Draft/QSO summary area: callsign, RST, name, QTH, review state
4. TX area: macro selector, compose box, send, stop
5. Side tools: compact spectrum / SQL / macro overlays
6. Confirm/review flow should stay reachable from `Operate`

Success:

- a normal user can use `Operate` as the daily working page

### P2. Bless One Recommended Connection Path

Goal:

- define one route we can confidently recommend

Recommended first route:

1. No-rig fallback:
   - RX: phone microphone
   - TX: phone audio
2. First wired TX route:
   - USB serial keyer

Deferred as broader compatibility layers:

- deeper CAT family support
- wider vendor/model coverage
- richer external RX capture options

Success:

- we can document one first recommended setup clearly

### P3. Stabilize The Production Workflow

Goal:

- remove "bench-only" fragility from the production path

Tasks:

1. Clear route-not-ready and TX/RX failure states
2. Clean app resume / state restore behavior
3. Stable draft persistence
4. Stable confirmed-log flow
5. ADIF export works from production flow

Success:

- repeated daily use does not require engineering knowledge

## Immediate Next Build Order

1. Continue P0 wording and entrance cleanup
2. Start P1 by reshaping `Operate` around live RX text + draft summary
3. Reduce production reliance on bench-only helper text in `Spectrum` / `QSO Editor` / `Rig Setup`
4. After `Operate` is credible, document and harden the first recommended route

## Not First

These are valuable, but should not lead the roadmap right now:

1. More `Normalize` cleanup
2. More synthetic fixture expansion
3. More visual polish on secondary pages
4. Broader multi-family rig promises

## Working Definition Of MVP Complete

We can call the first production MVP ready when:

1. A user can configure a route without Debug
2. `Operate` is the normal daily RX/TX screen
3. A reply can be sent and stopped from `Operate`
4. A QSO can be reviewed, confirmed, and logged
5. Developer tools remain available, but clearly secondary
