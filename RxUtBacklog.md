# RX UT Backlog

Last updated: 2026-04-28

## Purpose

This note captures what the local recordings taught us about the current CW RX test strategy, what is already covered well, and what still needs to be added.

It is intentionally focused on decoder/fixture coverage, not on UI or TX work.

## Current Coverage Strengths

The synthetic and local-audio tests already cover several important static dimensions:

- fixed-WPM tone offset cases
- fixed-tone WPM range cases
- light and heavy QSB
- nearby interferer / hum / soft clipping
- fixed-part speed sweep
- fixed-part tone sweep
- USB-like nominal / low / hot audio levels
- local recorded fixtures split into `strict`, `soft`, and `observability` buckets

Main files:

- [CwUserCaptureCoverageTest.java](/d:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwUserCaptureCoverageTest.java)
- [CwLocalAudioFolderRegressionTest.java](/d:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwLocalAudioFolderRegressionTest.java)
- [CwFixtureLibrary.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/eval/CwFixtureLibrary.java)

## What The Real Recordings Taught Us

### Recording (2): mid-stream speed shift is a real failure mode

- This is not just "fast CW is hard".
- The important structure is: first round faster, second round intentionally slower.
- Current timing logic is still weak when one continuous stream changes WPM mid-message without any explicit reset.
- Current concrete probe state is now clearer:
- the stream can later recover to an effective tracked tone around `660 Hz`
- but acquisition still tends to report `preferredWindowWinner=450`, `wideScanWinner=450`, `acquisitionWinner=450`
- `finalAdoptedSource` can still remain `SEARCH_FALLBACK`
- So the next missing guard is not "more penalty everywhere", but better acquisition-process observability before tightening this into a strict red-line test.

Implication:

- We need explicit `fast -> slow` and later `slow -> fast` continuity fixtures using real message text, not just `VVV` probes.

### Recording (9): long-text accumulated drift matters

- This is a long payload where decode quality gradually degrades.
- The main risk is not immediate front-end collapse, but cumulative timing / segmentation / boundary drift.

Implication:

- We need tests that measure stability across the first half and second half of a long message, not just final aggregate recall.

### Recording (13): acquisition observability exposed an important mismatch

- We already observed cases where:
- `preferredWindowWinner`
- `wideScanWinner`
- `acquisitionWinner`
- `finalAdopted`

do not line up cleanly.

Implication:

- More tests should assert process-level behavior, not only final decoded text.
- Long enough fixtures should verify we do not silently live forever in `SEARCH_FALLBACK` while still producing superficially acceptable output.

### Recording (3): similar callsign clusters expose basic segmentation weakness

- This is not primarily a "callsign intelligence" problem.
- It is a good probe for:
- repeated tokens
- similar token discrimination
- local error propagation

Implication:

- Add more low-level fixtures with repeated near-neighbor callsigns and repeated short tokens.

### Recording (4): repeated long CQ loops can suffer mid-stream corruption

- Repetition alone can expose timing fatigue.
- The issue is less about one isolated symbol, more about decode stability over repeated patterns.

Implication:

- Add "fatigue" fixtures with three or more repeated CQ / station-ID loops and require no middle-block degradation.

### Recording (5): short-tail endings are real, but not representative of the mainline problem

- This sample still reminds us that tail handling matters.
- But it should not dominate the current algorithm direction.

Implication:

- Keep it as observability / edge handling coverage.
- Do not let EOF-only hacks drive design.

## Missing Case Types Worth Adding

### Priority 1

1. Real-text `fast -> slow` continuous fixture

- Suggested structure:
- `CQ CQ DX DE JV3VV JV3VV PAGE K / CQ DX CQ DX DE JV3VV JV3VV PAGE K`
- First round around `28-30 WPM`
- Second round around `18-20 WPM`

Why:

- Direct synthetic mirror of `录音 (2)`.

2. Long-text gradual timing drift fixture

- Long payload near realistic QSO text length
- Tone stable
- WPM drifts gradually rather than switching once

Why:

- Mirrors `录音 (9)` more honestly than current short or blocky fixtures.

3. Long-text gradual tone drift fixture

- Same long payload idea
- Tone drifts slowly, for example `650 -> 680 -> 700 Hz`
- No artificial hard retune boundaries

Why:

- More realistic than step-wise tone sweep.

4. Wrong-preferred plus strong true peak over long payload

- Start preferred around `450 Hz`
- Real signal around `650-750 Hz`
- Sustain for a long enough message that stale fallback behavior would show up

Why:

- Current acquisition bugs are often process bugs, not instant one-frame bugs.

5. Multi-round continuous stream without reset

- Example shape:
- `CQ ... K / DE ... / RST ... / BK / TU 73`
- Feed as one stream

Why:

- This is much closer to actual RX usage than isolated fixture runs.

### Priority 2

6. Repetition fatigue fixture

- Three or more repeated CQ / callsign loops
- Require middle block quality to stay near the first block

7. Similar-callsign collision fixture

- Example families:
- `BI9CLT / BI8DLT / BI9CMS / BI9CXC`

8. Short-tail continuous-stream fixture

- Final character followed by only slightly-more-than-letter-gap silence
- No synthetic EOF `TONE_OFF`

9. Edge-frequency long-text fixture

- Long payload near `450/500 Hz`
- Another long payload near `800/850 Hz`

10. Humanized spacing jitter fixture without heavy QSB

- Spacing irregularity is the dominant distortion
- Tone and amplitude otherwise stable

## Assertion Gaps To Close

The current tests still lean heavily on:

- final decoded text
- aggregate recall
- tracked tone
- estimated WPM

We should add more process-level assertions.

### New process assertions to add

- `finalAdoptedSource` should not remain `SEARCH_FALLBACK` for an entire long healthy fixture
- `acquisitionWinner` and `finalAdopted` should not diverge excessively for long stable regions
- second-half recall should not collapse relative to first-half recall on long fixtures
- WPM should re-stabilize within a bounded number of tone/gap events after a deliberate speed change
- final character emission should not depend on synthetic EOF-only behavior
- known-gap cases should distinguish:
- front-end collapse
- timing drift
- decode drift

## Immediate Test-Backlog Order

1. Add a synthetic fixture that mirrors `录音 (2)` as real message text with `fast -> slow` speed change.
2. Add a synthetic long-text gradual drift fixture that mirrors `录音 (9)`.
3. Add a multi-round no-reset continuous-stream fixture.
4. Add a short-tail non-EOF fixture.
5. Add a similar-callsign collision fixture.
6. Add low-edge and high-edge long-text fixtures.

## Classification Guidance

When adding these cases, do not force all of them directly into `strict`.

Suggested rollout:

- `observability` first for brand-new dynamic fixtures
- `soft` after behavior stabilizes
- `strict` only after the dynamic behavior is truly converged

This is especially important for:

- mid-stream speed shift
- long gradual drift
- multi-round continuous-stream fixtures

## 2026-04-28 Progress Update

Completed in the current branch:

1. Added a synthetic `fast -> slow` real-text continuity fixture mirroring `录音 (2)`.
2. Added a synthetic long-text gradual-drift fixture mirroring `录音 (9)`.
3. Added a true single-stream multi-round fixture without large inter-message reset gaps.
4. Added a similar-callsign collision fixture based on repeated `BI9CMS / BI9CLT / BI8DLT` style confusion.

Recommended next additions:

1. Short-tail non-EOF continuous-stream fixture.
2. Low-edge and high-edge long-text fixtures.
3. Repetition-fatigue fixture based on repeated `CQ ... PSE K` loops.
4. Tighter process assertions for dynamic fixtures:
- bounded re-stabilization after speed shift
- first-half versus second-half recall guardrails
- no long healthy fixture ending entirely in `SEARCH_FALLBACK`

### Follow-up after the next fixture tranche

- The next fixture tranche is now also landed:
1. `user_short_tail_qrz_bi3tuk_kn`
2. `user_repetition_fatigue_cq_bi9clt`
3. `user_long_qso_edge_low_500hz`
4. `user_long_qso_edge_high_800hz`
- Practical note:
- a first attempt at `460Hz / 840Hz` long-text edge fixtures was too aggressive for the current pipeline and mostly measured front-end collapse rather than useful regression boundaries
- for now, `500Hz / 800Hz` is the better bench-useful edge pair
- We should revisit harder `460Hz / 840Hz` style cases later as `observability` or `stress` fixtures after tone acquisition improves.
- We also found and fixed an important synthetic-fixture modeling bug:
- the original `user_speed_shift_jv3vv_700hz` timing profile was accidentally authored as `slow -> fast`
- the fixture now correctly models `fast -> slow`
- this matters because process-level re-stabilization probes were otherwise measuring the opposite transition.

## Crowded-Band Combination Review

Recently landed bench-useful combination fixtures:

1. `user_weak_adjacent_cluster_cq_700hz`
2. `user_noisy_bursty_adjacent_cluster_cq_700hz`
3. `user_cochannel_underlay_proxy_cq_700hz`
4. `user_hum_noise_adjacent_cluster_cq_700hz`

Why they matter:

- They move beyond single-noise or single-interferer fixtures into more radio-like crowded-band occupancy.
- They are intentionally not judged by "precise clean decode" standards.
- The goal is that the content remains basically legible even when the fixture is no longer pretty.

Interesting next combinations worth adding later:

1. long-text QSO under weak crowded-band occupancy, not just short CQ loops
2. wrong-preferred + crowded-band matrix, but in `probe/stress` style first
3. repeated CQ under drifting adjacent occupancy to see whether fatigue and wrong-tone risks combine
4. stronger hum + crowded-band combinations, but probably as `observability` or `stress` first
5. asymmetric occupancy plus `speed shift` or `long payload` so we can tell whether the left/right skew stays stable once timing pressure is added

## 2026-04-28 Asymmetric Occupancy Follow-Up

What just landed:

1. `user_left_adjacent_occupancy_cq_700hz`
2. `user_right_adjacent_occupancy_cq_700hz`
3. paired coverage in [CwCrowdedBandCoverageTest.java](/d:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwCrowdedBandCoverageTest.java)

What the paired run taught us:

- This should not currently be treated as a "left/right must decode equally" regression.
- The present front end shows different failure shapes on the two sides:
- left-side occupancy tends to keep tracking nearer the target but damages text recall more
- right-side occupancy more easily drags tracking upward onto the stronger high-side interferer while still preserving more of the CQ skeleton
- That asymmetry is useful observability, not test noise, so the paired test now guards against total collapse rather than forcing fake symmetry.

Most valuable next crowded-band additions after this:

1. a long-text QSO version of the same left/right occupancy pair
2. adjacent occupancy plus gradual WPM shift in one continuous stream
3. adjacent occupancy plus wrong preferred tone, initially as `probe/stress`
4. adjacent occupancy plus hum or burst noise on only one side

Current generator limitation:

- The fixture engine can already synthesize:
- continuous carriers
- drifting carriers
- bursty carriers
- wobbling burst occupancy
- multiple simultaneous interferers
- It can now also synthesize a second independently keyed Morse stream on the same tone.
- New first probe:
- `user_same_tone_dual_sequence_target_priority_700hz`
- Added first amplitude-matrix follow-ups:
- `user_same_tone_dual_sequence_target_dominant_700hz`
- `user_same_tone_dual_sequence_interferer_dominant_700hz`
- Current reality:
- this is still an `observability` case, not a success-case regression
- the front end can hold a healthy same-tone lock, but downstream timing/character recovery still collapses under true keyed cochannel overlap
- Even making the target branch clearly stronger is not yet enough to recover the intended text reliably.
- That means "stronger signal first" is still only a future strategy direction; today the pipeline is mostly proving branch ambiguity, not solving it.
- That is still useful, because the different sequences make it obvious whether future work is actually choosing one branch or just smearing both.
