# RST Auto Evaluation Plan

Last updated: 2026-05-31

## Goal

Add a practical `RST` auto-suggestion path for `Operate` without breaking the current
manual QSO-draft workflow.

This plan intentionally treats:

- `RST Sent`
- `RST Received`
- template variables like `<RST>` / `<RST_RCVD>`

as related but different responsibilities.

## Confirmed variable semantics

Template variables should stay fixed to the following meanings:

- `<RST>` = `RST Sent`
- `<RST_SENT>` = `RST Sent`
- `<URRST>` = `RST Sent`
- `<RST_RCVD>` = `RST Received`
- `<RST_RECV>` = `RST Received`
- `<MYRST>` = `RST Received`

Practical interpretation:

- the default meaning of `RST` is "the report I will send to the other station"
- the received report must be referenced explicitly by `RST_RCVD`-style aliases

## Current code reality

The project already has a useful base:

- `QsoStateMachine` can already extract report-like text semantics such as `UR 599` and `R 599`
- `QsoDraftSnapshot` already stores:
  - `rstSentCandidate`
  - `rstRcvdCandidate`
- `QsoEditorActivity` already resolves the visible `RST` fields from the draft snapshot

This means the first phase is not "invent RST support".
It is "connect existing RST draft values into Operate and template rendering".

## Source priority

`RST Sent` and `RST Received` should both use a stable priority chain:

1. manual value
2. copied / text-derived value from live QSO draft
3. auto-suggested value
4. placeholder fallback

Important rule:

- manual edits must always win
- auto suggestion must never silently overwrite a manual operator decision

## Split responsibility

### `RST Received`

This should remain primarily text-driven.

Reason:

- it is the other station's actual report
- the app should not "guess" what the other station meant if text evidence is absent

So:

- keep `rstRcvdCandidate` driven by `QsoStateMachine` semantic extraction
- do not build a signal-quality estimator that fabricates `RST Received`

### `RST Sent`

This is the right place for auto evaluation.

Reason:

- it is the report our operator may want to send
- it can reasonably be suggested from current RX readability and signal quality

## Recommended MVP

First MVP should auto-suggest only `RST Sent`.

Recommended output style:

- `449`
- `559`
- `579`
- `599`

Avoid an over-fine first version.
The goal is practical operator assistance, not lab-grade scoring theater.

## Suggested scoring model

### `R` from readability

Estimate `R` from decode usability rather than raw amplitude.

Candidate inputs:

- callsign stability
- unknown-character ratio
- whether core QSO semantics survive
- whether repeated text remains human-readable

Suggested first mapping:

- `R=5`
  - solid readable copy
  - callsign and core exchange are stable
- `R=4`
  - readable enough for operator use
  - some damage remains
- no auto `R` suggestion below that first readable floor
  - if copy is too ragged, the app should stay quiet rather than fabricate a report

Do not try to use the full classic `1-5` readability scale in the first version.
On phone-local CW decode, a coarse operator-friendly band is more honest.

### `S` from signal quality

Estimate `S` from relative signal quality, not raw microphone level.

Candidate inputs:

- tone RMS versus noise floor
- tone isolation / dominance
- lock coverage
- tone-active unlock ratio

Suggested first mapping:

- `S=4`
  - weak tone separation
  - lock often unstable
- `S=5`
  - usable but not strong
- `S=7`
  - clearly above noise
  - stable enough for normal operation
- `S=9`
  - very strong relative signal
  - clean lock and strong tone separation

Important:

- this must be based on relative metrics
- absolute input amplitude alone is not trustworthy across microphone / USB / rig paths

### `T`

For CWCN MVP, keep `T = 9`.

Reason:

- automatic `tone / note` purity estimation is easy to over-promise and hard to trust
- many Android and rig audio paths distort this dimension in non-radio ways

Later, if needed, `T` can be revisited as an expert-only feature.

## Proposed new component

Add a dedicated component instead of mixing this into `QsoStateMachine`:

- `CwRstAutoEvaluator`

Suggested output model:

- `RstAssessment`
  - `suggestedRstSent`
  - `confidence`
  - `readabilityScore`
  - `signalScore`
  - `source`
  - `debugSummary`

Why separate it:

- `QsoStateMachine` should keep focusing on text semantics and draft convergence
- RST auto suggestion is an advisory layer, not core transcript truth

## Operate integration

### Phase 1

- connect `OperateActivity` template variables to active draft values
- `<RST>` starts reflecting draft `rstSentCandidate` when available
- `<RST_RCVD>` starts reflecting draft `rstRcvdCandidate` when available

### Phase 2

- if `rstSentCandidate` is still empty, show a soft auto suggestion
- example UI:
  - field text remains editable
  - hint text or chip shows `Auto 579`

### Phase 3

- allow one-tap adoption of the suggested value
- once the operator edits manually, freeze auto overwrite

## Future CAT-assisted enhancement

If reliable CAT-side strength data becomes available later:

- prefer rig-reported `S meter` data for the `S` part
- keep readability-based `R`

That would produce a better hybrid model:

- `R` from decode usability
- `S` from rig telemetry when available

## Test plan

### Unit tests

- manual value wins over copied value
- copied value wins over auto suggestion
- auto suggestion wins over placeholder
- `RST Received` is never fabricated when text evidence is absent

### Bench probes

- clean `599`-style report exchange
- damaged `5NN` exchange
- readable but weak nearby-tone scenario
- strong signal but poor text scenario

### UI behavior

- template `<RST>` and `<RST_RCVD>` render correctly in `Operate`
- manual edit survives refresh and resume
- opening `QsoEditor` and returning does not lose chosen RST value

## Implementation order

1. connect `Operate` template variables to active draft RST values
2. add `CwRstAutoEvaluator` as a pure advisory component
3. surface `Auto xxx` for `RST Sent` only
4. add manual-lock behavior
5. optionally add CAT-assisted `S` later
