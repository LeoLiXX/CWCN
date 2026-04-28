# Context Checkpoint

Last updated: 2026-04-27

## Purpose

This file is a compact handoff note for quickly resuming work without rereading long chat history.

## Current Git State

- Latest mainline checkpoint commit: `eb0adbf`
- Commit message: `milestone: checkpoint rig ui and tone acquisition refactor`
- Newer focused RX timing experiment commit: `e534482`
- Commit message: `milestone: speed up fast-cw timing convergence`
- Current working tree is intentionally not clean:
- modified: `ContextCheckpoint.md`
- untracked experimental A/B files:
- `cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwAdaptiveTimingModel.java`
- `cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/timing/CwAdaptiveTimingModelTest.java`
- `cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwTimingModelAbComparisonTest.java`

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

## Newly Added Since That Checkpoint

- TX page now has a lightweight `Bench Log` panel.
- The bench log currently records:
- backend changes
- USB target and key-line changes
- permission request / grant / denial
- attach / detach and manual refresh
- TX start / stop / error / completion
- USB diagnostic-stage transitions
- The log is in-memory only for now, intentionally lightweight for first bench sessions.
- TX page also now has a `Copy Report` action that copies:
- backend summary
- plan summary
- USB route summary
- playback status/progress
- full bench log timeline
- TX page now also shows a compact `Bench Summary` that interprets the current state into one short actionable diagnosis.
- The copied report includes the same `Bench Summary` section at the top.
- Added [TxUsbBenchChecklist.md](/D:/Workshop/CWCN/TxUsbBenchChecklist.md) for the first real USB hardware validation pass.
- Added [TxUsbBenchResults.md](/D:/Workshop/CWCN/TxUsbBenchResults.md) as the paste target for copied bench reports.
- Added a `Mock USB Serial Keyer Adapter` backend so P0 can be exercised on a phone without external hardware.
- Mock mode now supports selectable diagnostic scenarios directly in the TX page.

## Recommended Next Step

If continuing coding immediately, do this next:

1. Run the mock-first path in [TxUsbBenchChecklist.md](/D:/Workshop/CWCN/TxUsbBenchChecklist.md) on a phone.
2. Paste each copied report into [TxUsbBenchResults.md](/D:/Workshop/CWCN/TxUsbBenchResults.md).
3. After mock validation is coherent, move to real hardware when available.
4. Only after that, decide whether the next TX investment should be:
- broader USB-serial chipset compatibility
- per-device route/profile handling
- or explicit TX safety recovery refinements

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

## 2026-04-27 RX / Rig Reality Check

### What has now converged

- Rig-side CW serial keying behavior has been bench-validated on the user's `FT-710`.
- Practical result is now aligned with `DM780` behavior rather than our earlier assumptions.
- For the tested setup:
- when the rig is configured to use `RTS`, `RTS` keying works normally
- when the rig is configured to use `DTR`, `RTS + DTR` is the reliable combination
- `DTR only` is not a reliable standalone assumption for this station setup
- Conclusion:
- `CW keying type` must stay configurable
- dual-line assertion is a valid compatibility mode, not a hack
- this is important for future `Icom / Kenwood / Yaesu` serial-keying compatibility

### TX product direction that already changed

- The app has been pulled away from wizard-style flow and toward a single daily-use entry.
- `Operate` is the intended normal user entry.
- Experimental rig and bench actions should live in developer/debug surfaces, not in the primary daily UI.
- The user explicitly wants feature sprawl reduced:
- de-emphasize `QSL`, `Log Book`, and other distant features for now
- keep focus on `RX decode stability` first

### RX direction that is now confirmed

- The current biggest problem is not generic UI or CAT plumbing.
- The real bottleneck is `CW RX front-end robustness`, especially:
- dominant single-peak acquisition
- noise discrimination
- target-tone confidence
- believable dynamic WPM behavior
- stability under real-world preferred-tone mismatch

### Important decoding conclusions already reached

- `Preferred Tone` must be treated as a soft prior only.
- It must never silently become a hard target anchor during acquisition.
- Earlier hidden `preferred -> target` fallback behavior was identified as one source of wrong-tone lock and has already been cleaned up in several places.
- Timing-only optimization is not enough.
- The experimental `CwAdaptiveTimingModel` A/B path did not materially beat the current mainline path.
- Therefore:
- do not merge the timing-only B path into live decode
- do not spend the next cycle polishing timing in isolation

### A/B timing experiment status

- Experimental files currently exist only as a side branch in the working tree:
- `CwAdaptiveTimingModel.java`
- `CwAdaptiveTimingModelTest.java`
- `CwTimingModelAbComparisonTest.java`
- Their purpose is observability and comparison, not promotion into production.
- Current conclusion from that experiment:
- `20/25/30 WPM` decode weaknesses are still dominated by front-end acquisition and front-end evidence quality
- not by the current timing model alone

### Known unresolved RX weakness

- There is still at least one important failing unit-test pattern in `CwSignalProcessorTest`:
- wrong preferred tone plus noisy true signal acquisition
- practical shape:
- preferred near `450 Hz`
- actual CW near `700 Hz`
- noisy environment
- Meaning:
- the processor can still be dragged toward a wrong/noisy peak instead of the real single-tone dominant peak

### Observability that already exists

- `CwSignalSnapshot` now exposes more acquisition internals including:
- preferred-window winner
- wide-scan winner
- acquisition winner
- final adopted tone
- `InputDebugActivity` already surfaces these fields and spectrum guidance labels
- The user specifically found the spectrum visualization useful and wants more indicators kept visible in the main workflow when practical.

### Product priorities explicitly deprioritized by the user

- Do not prioritize:
- callsign auto-recognition polish
- phrase completion
- QSO intelligence expansion
- broader UI feature growth
- The user has stated multiple times that if RX decoding is unstable, those higher-level features are not worth optimizing yet.

### Next recommended mainline engineering step

Do not continue with timing-only tuning as the main track.

Instead, the next meaningful B-path should be inside `CwSignalProcessor` and should focus on:

1. stronger dominant-peak confidence scoring
2. clearer noise-vs-true-peak discrimination
3. explicit acquisition stability metrics
4. a short rolling recent-frame buffer
5. later, a second-pass re-decode when tone/WPM stabilize

### Second-pass re-decode plan

- The user agreed this is a worthwhile direction:
- if the start of a transmission is decoded while tone/WPM are still unstable, we should consider replaying a short recent window once acquisition stabilizes
- This should be introduced carefully:
- first in debug or local replay path
- with instrumentation
- before promoting to always-on live path

### Instrumentation that should accompany second-pass work

- re-decode trigger count
- average re-decode duration
- recent trigger frequency
- stable/unstable acquisition state

## 2026-04-28 RX Decode Priority Lock

### What has now converged

- Mainline RX regression is in acceptable shape again for the current branch.
- The following suites are the current guardrail baseline and were treated as the main product truth:
- `CwFixturePipelineRegressionTest`
- `CwUserCaptureCoverageTest`
- `CwLocalAudioFolderRegressionTest`
- `CwFixtureEvaluatorTest`
- We also aligned local recording `(3)` with the confirmed trailing `.-.-.-` period, so that sample now expects `BK.` instead of `BK`.
- RAW-versus-normalized judging has been explicitly separated:
- `expectedRawText` is now the reference for RAW decode quality
- higher-level normalize / phrase / callsign behavior must not dominate RX correctness verdicts

### What is still intentionally red

- A recent full unit-test pass ended with `373 tests completed, 4 failed`.
- Those 4 failures are currently all probe / experimental suites, not the mainline RX regression gate:
- `CwFastPreferredOffsetMatrixRegressionTest`
- `CwPreferredOffsetMatrixRegressionTest`
- `CwTimingModelAbComparisonTest`
- `CwTimingModelStrategyProbeTest`
- Interpretation:
- the mainline path is no longer the main crisis
- the remaining real engineering problem is still acquisition / preferred-offset / dynamic restabilization
- not another round of surface-level normalization work

### Locked priority order

The user explicitly re-confirmed this priority order and we should preserve it unless bench evidence changes:

1. Protect RAW RX decode correctness first.
2. Improve noisy-case robustness second.
3. Treat interferer / cochannel behavior as lower-priority stretch coverage.
4. Keep callsign inference, phrase completion, and other normalization helpers subordinate to RAW decode quality.

Practical rule:

- any feature that makes normalized output prettier but risks degrading RAW decode truth should lose that tradeoff
- RAW decode metrics should always be judged from the raw decode path first

### Current main attack direction

Do not spend the next cycle primarily polishing timing-only A/B logic.

The next mainline work should stay inside `CwSignalProcessor` and focus on:

1. preferred-offset acquisition failures
2. strong-true-peak versus wrong-prior / noise discrimination
3. dynamic retarget / restabilization during speed or tone changes
4. process observability that explains why the tracker chose its winner

The most important still-problematic shapes are:

- `user_qsb_cq_18wpm_600hz`
- `user_speed_sweep_vvv_700hz`
- `user_noise_cq_20wpm_700hz`
- `user_noise_cq_25wpm_700hz`
- `user_noise_cq_30wpm_700hz`

### Secondary work that is still worth doing

- A narrow interpreter regression group is still worth adding for glued short-token structures such as:
- `LEO HW?`
- `JA1ABC FB`
- `BG1XXX SK`
- `FB OM`
- But this is only a guardrail against post-processing damage.
- It must not become the main workstream while acquisition and restabilization are still the bigger issue.

### Fixed rerun set after each small change

After every non-trivial RX algorithm change, rerun this fixed set before trusting the result:

- `CwFixturePipelineRegressionTest`
- `CwUserCaptureCoverageTest`
- `CwLocalAudioFolderRegressionTest`
- `CwFastPreferredOffsetMatrixRegressionTest`
- `CwPreferredOffsetMatrixRegressionTest`
- `CwTimingModelAbComparisonTest`
- `CwTimingModelStrategyProbeTest`

### Short resume hint for the next session

If resuming from this checkpoint:

- do not restart from UI, TX, or normalization ideas
- first inspect the preferred-offset / dynamic-restabilization red probes
- then change acquisition behavior in small steps
- after each step, re-run the fixed guardrail set and check that mainline RAW RX quality did not regress

### 2026-04-28 incremental-work discipline

- We have now explicitly switched to a "small step + full guardrail rerun" rhythm.
- Before the next algorithm edit, the fixed rerun set was executed as a baseline:
- `CwFixturePipelineRegressionTest`
- `CwUserCaptureCoverageTest`
- `CwLocalAudioFolderRegressionTest`
- `CwFixtureEvaluatorTest`
- `CwFastPreferredOffsetMatrixRegressionTest`
- `CwPreferredOffsetMatrixRegressionTest`
- `CwTimingModelAbComparisonTest`
- `CwTimingModelStrategyProbeTest`
- Result at this checkpoint:
- 85 tests completed in the fixed set
- only 4 failures remain
- and those 4 are still the same probe / experiment suites listed above
- Practical meaning:
- mainline guardrails are currently stable enough for narrow acquisition experiments
- every change from here should preserve that invariant before we trust any local improvement
- target-tone confidence
- top-peak vs next-peak separation when practical

### Performance / battery constraint

- The user explicitly asked that any second-pass design be checked for phone-side cost.
- Guidance:
- keep the buffer short, around `1.5s` to `3s`
- trigger only on unstable -> stable transitions
- instrument before enabling widely
- do not assume the extra work is free on-device

### Ground truth on practical test environment

- Daytime testing is often limited to:
- PC playback
- phone microphone receive
- The eventual production scenario is more likely USB-carried rig audio, but current iteration still needs to support speaker-to-mic debug loops.
- The user also wants the app to accept local recording replay for quieter iteration, including Windows-produced `.m4a` files when Android codec support permits.

### Immediate resume instruction

When picking this work back up:

1. preserve the current mainline decode path
2. keep the timing A/B experiment isolated
3. move the next experiment into `CwSignalProcessor`
4. add stronger acquisition confidence observability first
5. only then prototype rolling-buffer + second-pass re-decode

## 2026-04-23 Incremental Notes After This Checkpoint

### Mock P0 status

- The mock-first USB P0 path has now produced both:
- one focused ready-path report
- one broader failure-sweep report
- Confirmed working path:
- `no-device`
- `no-permission`
- `ready`
- short `DTR` DIT playback completes successfully
- Confirmed failure-path semantics in the bench log and copied report flow:
- `usb-serial-open-failed`
- `usb-serial-claim-failed`
- `usb-serial-no-control-interface`
- `usb-serial-target-missing`
- `usb-serial-no-cdc`
- `Progress snapshots` are now confirmed visible in the copied report, which also indicates the latest APK is installed instead of an older build.
- Practical conclusion:
- mock P0 is coherent enough for this phase
- remaining uncertainty has shifted from mock semantics to real hardware behavior
- one small UX gap remains:
- when several failure scenarios are swept inside one session, the copied report header reflects the final current state while earlier failures live in the bench log only

### Audio VOX status

- `Audio VOX Text Adapter` is no longer in the earlier obviously-broken state.
- Two practical fixes landed in the TX audio path:
- tone rendering no longer reapplies a fade-in/fade-out envelope every `20ms` chunk
- `AudioTrack` buffer sizing was corrected and sample rate raised to `48kHz`
- Practical result from on-device feedback:
- inter-symbol spacing is clear
- overall tone is now described as "normal much more"
- This should be treated as:
- usable for continued TX work
- improved, but not yet final-polish / reference-quality voicing

### Launcher icon status

- Adaptive launcher icon clipping was fixed by adding safe inset wrappers for the foreground and monochrome layers.
- Latest debug APK includes that icon safe-zone fix.

### Recommended priority from here

1. Treat mock USB P0 as complete enough unless a new inconsistency appears.
2. Hold Audio VOX steady unless a new major defect appears.
3. Choose between:
- real USB bench when hardware exists
- a small TX observability polish for multi-failure report capture
- or shifting effort back to RX real-input stability

## 2026-04-23 Late-Day TX Wrap-Up

### Audio VOX practical status

- `Audio VOX Text Adapter` is now good enough for continued bench use.
- Latest on-device feedback says:
- tone is much fuller than before
- no urgent need for more voicing tweaks right now
- practical guidance from here:
- stop polishing timbre unless a new defect appears
- keep `Audio VOX` stable and treat it as usable

### TX bench UX status

- The earlier multi-failure report gap was tightened:
- copied reports can now carry the most recent significant USB issue after the route has recovered to `Ready`
- but they no longer duplicate that section when the route is still currently blocked
- TX page scroll preservation was also added around preview/state refresh:
- failure-state transitions should no longer yank the page position as aggressively during bench work
- this part is technically implemented and built
- final on-device comfort verification is still pending one more user pass

### Real-hardware readiness

- Real bench is now reasonable for:
- `Audio VOX`
- simple `USB Serial Keyer` devices that behave like `CDC/ACM` plus `RTS/DTR` control-line keying
- Real bench is still not the same as full CAT/radio control readiness.

### Next-step recommendation

1. If no new TX blocker appears, stop investing further in mock/TX polish.
2. Keep current TX state as a milestone candidate.
3. Shift the main engineering priority back to `RX real-input stability`.

## 2026-04-23 RX Real-Input Stability Follow-Up

### What landed

- `CwSignalProcessor` now has explicit frame-gap recovery for real-input callback discontinuities.
- A long inter-frame gap now:
- synthesizes a defensive `TONE_OFF` when needed
- clears active tone / lock / pending retune carry-over
- prevents stale tone-lock state from bleeding across microphone callback stalls or resume jitter
- Added signal-level observability for this path:
- frame-gap reset count
- last observed frame gap
- reset threshold used for the last gap check
- worst observed frame gap
- last reset timestamp
- `InputDebugActivity` now surfaces those continuity metrics directly inside:
- `Signal State`
- `Signal Health Summary`

### Why this matters

- TX mock/bench work is currently coherent enough for this phase.
- The next likely product risk is no longer abstract TX plumbing, but real RX behavior under:
- microphone callback hiccups
- app resume jitter
- irregular device scheduling
- This change gives us both:
- safer front-end behavior
- and a visible way to confirm when a live-input disruption actually happened

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.signal.CwSignalSnapshotTest --tests org.bi9clt.cwcn.core.eval.CwFrontEndHealthClassifierTest`

## 2026-04-27 RX Decoder Rework Checkpoint

### Current Git Milestones

- `eb0adbf` `milestone: checkpoint rig ui and tone acquisition refactor`
- `e534482` `milestone: speed up fast-cw timing convergence`

### What We Reconfirmed

- `preferred tone` must remain a soft prior only.
- The real dominant peak must be detected from signal evidence.
- The main unresolved bug is no longer “preferred silently rewrites target” in several branches; those hidden fallback paths were cleaned up.
- The biggest remaining decoder weakness is still front-end acquisition under noise, especially wrong-preferred or unstable-peak cases.

### Unit-Test Reality Check

- `CwSignalProcessorTest` still has one important failing case:
- noisy wrong-preferred acquisition
- representative shape: `preferred=450 Hz`, actual signal near `700 Hz`
- failure meaning:
- the front end still lets noise/false peaks beat the real CW peak in some conditions

- `CwUserCaptureCoverageTest` currently passes.
- But that does **not** mean `>20 WPM` is truly good enough.
- Existing thresholds are intentionally loose:
- `20 WPM` and `25 WPM` still pass at relatively modest recall
- `30 WPM` is currently treated more like a boundary/observability case than a strong decode-success target

### Fast-CW A/B Experiment

We built an isolated B-path timing experiment instead of replacing the live path:

- New experimental files:
- [CwAdaptiveTimingModel.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwAdaptiveTimingModel.java)
- [CwAdaptiveTimingModelTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/timing/CwAdaptiveTimingModelTest.java)
- [CwTimingModelAbComparisonTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwTimingModelAbComparisonTest.java)

- A/B conclusion:
- a timing-only rewrite does **not** solve the core problem
- the experimental timing path did not beat the baseline on the fast/noisy fixtures
- especially at `30 WPM`, both paths remain weak, and the B-path was worse in the first comparison run

### Practical Interpretation

- The decoder bottleneck is not just `dot/dash/gap` estimation.
- The deeper bottleneck is still upstream:
- tone acquisition
- single-peak confidence
- noise discrimination
- stable target adoption

## 2026-04-28 RX UT Backlog Refresh

### What the local recordings clarified

- `录音 (2)` is now understood as a true `mid-stream speed shift` sample:
- first round faster
- second round intentionally slower
- this is a stronger test-design signal than treating it as generic noisy fast CW
- `录音 (9)` remains the best current evidence that long-text decode can drift over time without the front end fully collapsing
- `录音 (13)` proved that acquisition observability matters:
- `preferred / wide / acquisition / final adopted` can disagree in meaningful ways

### Why this matters

- Existing synthetic coverage is already decent on static dimensions:
- fixed WPM
- fixed tone
- QSB
- tone sweep
- speed sweep
- USB-like level variation
- What is still under-covered is dynamic behavior inside one continuous stream.

### New test-design priority

Focus next fixture growth on:

1. real-text `fast -> slow` speed-shift fixture
2. long-text gradual timing-drift fixture
3. long-text gradual tone-drift fixture
4. wrong-preferred + strong true peak over long payload
5. multi-round no-reset continuous-stream fixture
6. short-tail non-EOF ending fixture
7. similar-callsign collision fixture

### Reference note

- Detailed backlog and rationale are now in:
- [RxUtBacklog.md](/D:/Workshop/CWCN/RxUtBacklog.md)

### Progress since this note

- The first dynamic backlog tranche is now partially landed:
1. `user_speed_shift_jv3vv_700hz`
2. `user_long_qso_drift_bg1xxx_ja1abc`
3. `user_multi_round_continuous_qso_bi9clt_ja1abc`
4. `user_similar_callsign_collision_bi9cms_bi9clt`
- The next fixture tranche is also now landed:
1. `user_short_tail_qrz_bi3tuk_kn`
2. `user_repetition_fatigue_cq_bi9clt`
3. `user_long_qso_edge_low_500hz`
4. `user_long_qso_edge_high_800hz`
- The corresponding coverage tests in [CwUserCaptureCoverageTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwUserCaptureCoverageTest.java) currently pass.
- The new multi-round continuous fixture already showed an important nuance:
- raw decode can still split tokens like `BI9CLT` / `JA1ABC`
- but normalized-token continuity remains healthy enough to treat this as a useful continuity test rather than a front-end-collapse case
- The newly added short-tail and fatigue cases confirmed a second nuance:
- raw decoder text can already be healthier than normalized interpreter/callsign extraction output
- so some RX regression checks should stay anchored to raw decoder text while others remain anchored to normalized/token continuity
- A first attempt at harsher `460Hz / 840Hz` long-text edge fixtures was intentionally backed off to `500Hz / 800Hz` for now:
- the harsher pair currently measures front-end collapse more than a stable regression boundary
- The harsher pair is now still preserved as dedicated stress probes rather than discarded:
- `user_long_qso_edge_low_460hz_stress`
- `user_long_qso_edge_high_840hz_stress`
- We also corrected a synthetic-fixture modeling mistake in `user_speed_shift_jv3vv_700hz`:
- its `wpmScale` direction had accidentally been authored as `slow -> fast`
- it now correctly models `fast -> slow`
- After that correction, the new speed-shift re-stabilization probe became meaningful again.
- We also widened the crowded-band combination layer with three more user-style CQ fixtures:
- `user_weak_adjacent_cluster_cq_700hz`
- `user_noisy_bursty_adjacent_cluster_cq_700hz`
- `user_cochannel_underlay_proxy_cq_700hz`
- That layer now also includes a first combined hum+noise+adjacent fixture:
- `user_hum_noise_adjacent_cluster_cq_700hz`
- This is useful because it stops us from treating hum, broadband noise, and adjacent occupancy as entirely separate worlds during RX review.
- Important honesty note:
- the last case is only a cochannel proxy because the current fixture engine still cannot render a true second keyed CW sender on the same tone
- it can already do multiple continuous/drifting/bursty carriers, but not yet full dual-Morse overlap
- This means the next fixture wave should keep separating:
- front-end failure
- token segmentation drift
- higher-level semantic drift

### Agreed Next Direction

We agreed to move the main redesign effort back to the front end:

1. Stronger single-peak / dominant-peak confidence logic
2. Dynamic tone acquisition with softer prior bias
3. Dynamic WPM tracking only after front-end confidence becomes reliable
4. Keep timing improvements, but do not expect timing alone to rescue fast/noisy decode

### Important New Product Idea: Second-Pass Re-decode

This idea is explicitly approved for future implementation:

- keep a short rolling audio-frame buffer
- allow provisional first-pass decode while tone/WPM are still stabilizing
- once tone and WPM become stable enough, rerun decode on the recent short window
- expose the result as something like:
- provisional
- confirmed

Why this matters:

- opening characters are often decoded before tone/WPM have settled
- a short second pass can recover earlier text once the front end is confident
- this is more realistic than demanding perfect first-pass decode every time

### Performance / Battery Guidance For Second-Pass Decode

Current decision:

- the feature is considered feasible on phones if constrained carefully
- do **not** continuously double-process all audio
- only re-decode short recent windows
- only trigger on stability transitions, not every frame
- start in debug / replay paths before enabling more broadly

Guardrails to preserve battery:

- buffer about `1.5s ~ 3s`, not a long history
- trigger only when confidence moves from unstable to stable
- add instrumentation:
- re-decode count
- average re-decode duration
- recent trigger frequency

### Recommended Immediate Next Implementation

1. Design a new front-end B-path for `CwSignalProcessor` rather than more timing-only tuning
2. Add explicit peak-confidence observability
3. Add a minimal rolling-frame buffer abstraction for future second-pass replay
4. Keep the A/B timing experiment files unmerged into the live path until a signal-front-end redesign exists
- `.\gradlew.bat assembleDebug`

### Recommended next step from here

1. Use the Debug page with live microphone input and watch whether frame-gap resets appear during:
- start / stop
- app background-resume
- device load spikes
2. If live resets appear too often, the next work should move one layer earlier into audio-input continuity tracking / capture-thread diagnosis.
3. If live resets stay rare but decode still feels unstable, move down-pipeline into timing-model robustness under real jitter.

## 2026-04-23 RX Tone Acquisition Checkpoint

### What just changed

- User reported poor adaptation when the preferred tone frequency is far from the actual received tone.
- `CwSignalProcessor` now keeps the normal preferred-window scan first, then falls back to wide acquisition only while unlocked and only when the preferred window is not reliable.
- Wide acquisition scans the supported 450-850 Hz range with a softer preferred-frequency bias.
- Locked-state behavior remains conservative so nearby/far interferers should not easily pull a stable target away.

### Guardrails added

- Added tests for clean tone acquisition far above and far below the preferred setting.
- Existing signal tests still pass, including preferred-window protection, stronger nearby interferer protection, broadband-noise rejection, frame-gap reset behavior, and front-end health classifier coverage.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.signal.CwSignalProcessorTest --tests org.bi9clt.cwcn.core.signal.CwSignalSnapshotTest --tests org.bi9clt.cwcn.core.eval.CwFrontEndHealthClassifierTest`

### Next likely branch

- Rebuild/reinstall debug APK and retry live RX with deliberate preferred/actual frequency mismatch.
- If target tone now follows the real tone but symbols are still unstable, continue into timing/gap robustness.
- If target tone still sticks near preferred, inspect live `Target Tone`, `tone RMS`, `dominance`, and `isolation` snapshots from the Debug UI.

## 2026-04-24 RX Noise/AGC Fixture Checkpoint

### What just changed

- Added three targeted real-world stress fixtures:
- `weak_broadband_noise_report`
- `near_frequency_narrowband_noise_report`
- `agc_pumping_volume_report`
- The fixture matrix now explicitly covers:
- weak useful signal under broadband noise
- adjacent narrowband/CW-like carrier pressure
- fast volume/QSB/AGC-style pumping
- `drifting_nearby_interferer_directed_report` is now treated as a boundary case where decode may succeed while the final tail state locks to an off-target carrier.
- Fixture evaluation summaries now include a richer `Front-end levels` line with:
- last/target/wideband residual levels
- dominance/isolation
- attack/release thresholds
- noise/signal floor estimates
- tone on/off event counts
- frame-gap reset and worst-gap values

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwFixturePipelineRegressionTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.eval.CwFixtureEvaluationResultTest`

### Next likely branch

- Build/reinstall the next `cwcn-debug.apk`.
- Run the new synthetic fixtures first to confirm baseline summaries are visible.
- Then repeat live phone-speaker microphone tests at low and moderate volume.
- If low-volume failure shows weak `peakToneRms`, low `lockCoverage`, or poor `noiseFloor/signalFloor`, prioritize input calibration/coaching.
- If front-end metrics look healthy but `Timing` dot/dash estimates stretch or symbols collapse, prioritize timing/gap robustness under real mic/near-tone conditions.

## 2026-04-24 RX Debug UI Focus Checkpoint

### What just changed

- Debug RX now defaults to a focused receive-testing view.
- New `RX Focus` panel surfaces the fields most useful during live mic tests:
- source/capture state
- input peak/rms
- preferred/tracked tone and lock
- front-end quality/bottleneck reason
- tone/residual/isolation levels
- tone on/off counts
- frame-gap reset count
- decoded/normalized text and callsign candidates
- Full debug panels are still available through `Show Detailed Panels`, but no longer interrupt the default RX test flow.
- Hidden-by-default detailed panels include device status, ADIF, QSO manual editor, interpreter, decoder, timing, capture, signal detail, module list, and long fixture evaluation output.

### Verification

- `.\gradlew.bat assembleDebug`

## 2026-04-24 Yaesu Native Serial CAT Control Follow-Up

### What just changed

- [SerialCatRigControlAdapter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatRigControlAdapter.java) is no longer only a readiness shell.
- It now attaches the first real family-specific native serial control behavior for `Yaesu-style CAT`.
- Current Yaesu control flow is intentionally minimal:
- `KSxxx;`
- `KPxx;`
- `TX1;`
- `TX0;`
- This is still not full text-to-CW support, but it is the first native CAT TX/PTT control layer in CWCN.
- `Rig Setup` now includes a short smoke-test action for this path:
- `Pulse Serial CAT TX/PTT`
- via:
- [RigSetupActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java)
- [activity_rig_setup.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_rig_setup.xml)
- [YaesuSerialCatBenchChecklist.md](/D:/Workshop/CWCN/YaesuSerialCatBenchChecklist.md) was updated so the bench order is now:
- permission
- probe
- short CAT TX/PTT pulse

### Why this matters

- Native Yaesu work is no longer only “can we open the link?”
- It is now “can we briefly assert and release CAT TX in a controlled way?”
- That is a much better first FT-710 smoke-test surface than jumping straight to full text TX.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.SerialCatRigControlAdapterTest --tests org.bi9clt.cwcn.core.rig.SerialCatProbeTest assembleDebug`

### Next likely branch

1. Use a real Yaesu FT-family rig to validate:
- probe
- short CAT TX/PTT pulse
2. If that works cleanly, continue with `Icom` as the next native control branch.
3. Keep full native text TX separate until the family-level control behavior is proven on hardware.

## 2026-04-24 Icom Native CI-V Control Follow-Up

### What just changed

- [SerialCatRigControlAdapter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatRigControlAdapter.java) now also attaches a minimal `Icom CI-V` native control branch.
- Current scope is intentionally limited to:
- CI-V PTT on
- CI-V PTT off
- short pulse smoke test
- `Rig Setup` now enables the existing `Pulse Serial CAT TX/PTT` action for `Icom CI-V` too, as long as a `CI-V address` is present.
- [IcomCivBenchChecklist.md](/D:/Workshop/CWCN/IcomCivBenchChecklist.md) now includes that pulse step after the CI-V probe.

### Why this matters

- `Icom` is no longer one full maturity step behind `Yaesu`.
- Both families now have:
- serial permission flow
- read/probe validation
- a very short native-control smoke test
- That gives us a much cleaner base for future real-device comparisons.

### Important note

- The current Icom pulse implementation uses the common CI-V `1C 00` PTT on/off framing assumption.
- Treat it as an implementation candidate that still needs confirmation on real hardware.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.SerialCatRigControlAdapterTest --tests org.bi9clt.cwcn.core.rig.SerialCatProbeTest assembleDebug`

### Next likely branch

1. Validate real `Icom` CI-V probe + pulse behavior on hardware.
2. If `Yaesu` and `Icom` both look clean, decide whether to deepen native text TX or keep expanding compatibility/model coverage first.

## 2026-04-24 Developer Mode Frame

### What just changed

- Added a global developer-mode preference:
- [DeveloperModeStore.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/app/DeveloperModeStore.java)
- [HomeActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/home/HomeActivity.java) now has an explicit `Developer Mode` switch.
- In the current first pass:
- normal path keeps:
- `QSO Editor`
- `Logbook`
- `Rig Setup`
- developer path folds away:
- `TX Console`
- `Debug Tools`
- [RigSetupActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java) now follows the same global mode.
- With developer mode off, the screen keeps:
- rig profile selection
- saved defaults/configuration
- With developer mode on, the screen also exposes:
- quick links to TX / Debug
- serial/network probe buttons
- short TX/PTT pulse actions
- verbose engineering summary panels

### Why this matters

- We can now start designing a real user-facing operating flow without throwing away the existing bench/debug tools.
- This also means tonight's Yaesu bench work can still happen cleanly:
- just enable developer mode
- use the same probe/pulse tools

### Verification

- `.\gradlew.bat assembleDebug`

### Next likely branch

1. Use this as the first stable boundary between product UI and engineering UI.
2. After the first Yaesu hardware feedback, decide which controls stay developer-only and which move into the normal rig flow.

## 2026-04-24 Rig Setup User-Flow Pass

### What just changed

- `Rig Setup` is now less like an engineering console in normal mode.
- [activity_rig_setup.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_rig_setup.xml) gained a `Connection Guide` panel.
- [RigSetupActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java) now renders a profile-aware summary of:
- what the selected path is for
- what transport it uses
- what the next normal setup step is
- whether developer tools are currently hidden
- The normal-mode / developer-mode boundary inside `Rig Setup` is tighter now:
- `Back Home` always stays visible
- developer quick actions stay hidden until developer mode is enabled
- protocol-family spinners and serial port hint are now treated as advanced fields
- Home wording also shifted one step toward a product path:
- `Open Rig Setup` is now presented as `Connect Radio`

### Why this matters

- We now have a better base for a real user-facing connection flow without losing tonight's Yaesu bench path.
- With developer mode on, the bench/probe tools are still there.
- With developer mode off, the screen reads much more like "choose and save your rig path" instead of "inspect protocol internals."

### Verification

- `.\gradlew.bat assembleDebug`

### Next likely branch

1. Keep this structure through the first Yaesu real-device pass.
2. Then decide whether the next UI investment goes into:
- a proper `Connect Radio` wizard
- or a first non-debug operating screen

## 2026-04-24 First Formal Operate Screen

### What just changed

- Added the first real non-debug operating screen:
- [OperateActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/operate/OperateActivity.java)
- [activity_operate.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_operate.xml)
- The screen currently focuses on:
- operating draft status
- pinned rig / radio-path status
- next action in the user flow
- normal user buttons for:
- `Connect Radio`
- `Open QSO Editor`
- `Open Logbook`
- developer support buttons for:
- `Open TX Console`
- `Open Debug Tools`
- but only when developer mode is enabled
- Home now exposes this via `Start Operating`.

### Why this matters

- You now have a formal user screen you can actually open on-device while still validating the underlying connection work.
- This gives us a much better surface for real-world UI feedback than staying inside `Home` plus `Rig Setup` only.

### Verification

- `.\gradlew.bat assembleDebug`

### Next likely branch

1. Use the new `Operate` screen during the Yaesu real-device pass.
2. Observe whether the operating summary and rig summary feel clear enough on-device.
3. Then refine either the new screen or the connection path based on that real feedback.

## 2026-04-24 Kenwood Native Probe + Shared Native Serial Adapter

### What just changed

- [SerialCatProbe.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatProbe.java) now supports `Kenwood-style CAT` in addition to the already landed `Yaesu-style CAT` and `Icom CI-V` paths.
- The Kenwood probe stays read-only first and looks for a safe reply from:
- `ID;`
- `FA;`
- `IF;`
- [RigSetupActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java) now exposes Kenwood as a supported serial-probe family in the same UI guidance flow.
- Added:
- [KenwoodSerialCatBenchChecklist.md](/D:/Workshop/CWCN/KenwoodSerialCatBenchChecklist.md)
- [KenwoodSerialCatBenchResults.md](/D:/Workshop/CWCN/KenwoodSerialCatBenchResults.md)
- and linked them from [TestingWorkbench.md](/D:/Workshop/CWCN/TestingWorkbench.md).
- The old `generic-cat` placeholder adapter has been replaced by:
- [SerialCatRigControlAdapter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatRigControlAdapter.java)
- This is still not native TX completion.
- It is the first real shared adapter object for:
- selected serial-CAT profile binding
- family-aware readiness/availability
- future Yaesu/Icom/Kenwood native control attachment

### Why this matters

- Native serial CAT is no longer just a couple of probe helper methods plus a placeholder registry entry.
- We now have one shared transport seam and one shared adapter seam.
- That keeps the next branch focused on family-specific command behavior rather than more infrastructure churn.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.SerialCatProbeTest --tests org.bi9clt.cwcn.core.rig.SerialCatRigControlAdapterTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest assembleDebug`

### Next likely branch

1. Keep `Yaesu`, `Icom`, and `Kenwood` aligned at the same probe-first maturity.
2. Attach the first family-specific native serial control behavior to [SerialCatRigControlAdapter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatRigControlAdapter.java).
3. Do that in the requested order:
- `Yaesu` first
- then `Icom`

## 2026-04-24 Yaesu FT-Series Test Path Checkpoint

### What just changed

- Added a new formal rig profile:
- `Yaesu FT-Series via rigctld`
- This profile is deliberately broader than only `FT-710` and is meant to cover the current nearest-family bench path for:
- `FT-710`
- `FT-891`
- `FT-991A`
- and nearby Yaesu FT rigs already supported by a local `Hamlib rigctld` bridge
- The new profile is marked `Bench-ready` because CWCN already has:
- a real `Hamlib rigctld` TX backend
- a rigctld probe action in `Rig Setup`
- pinned-rig to TX backend auto-selection
- `Rig Setup` now explicitly recommends this route first when the user is benching Yaesu FT rigs today.
- `TX` route guidance also now explicitly says that Yaesu FT rigs should currently start from the rigctld path while native Android Yaesu serial CAT is still pending.
- Added [YaesuRigctldBenchChecklist.md](/D:/Workshop/CWCN/YaesuRigctldBenchChecklist.md) as the concrete operator checklist for this Yaesu-family path.

### Why this matters

- CWCN now has a formal, named Yaesu-family path that can actually be pinned and exercised today.
- This is the shortest credible route from:
- CAT scaffolding
- to:
- real Yaesu-family bench validation
- without pretending that direct native Android Yaesu CAT is already finished.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.tx.CwTxRouteAdvisorTest assembleDebug`

## 2026-04-24 Yaesu Family Bench Guidance Follow-Up

### What just changed

- Added [RigProfileFamilies.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigProfileFamilies.java) as a small family-detection helper so the app can recognize Yaesu-family profiles without hardcoding everything into one screen.
- [RigProfileConfigurationFormatter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigProfileConfigurationFormatter.java) now adds a Yaesu-family note when the current path is:
- Yaesu family
- plus `Hamlib rigctld`
- [HamlibRigctldRigControlAdapter.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/HamlibRigctldRigControlAdapter.java) now gives more practical Yaesu-family availability/probe wording:
- remind the operator to verify rigctld is already bound to the radio
- suggest short `DIT / VVV` style validation first
- [TxActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/tx/TxActivity.java) now gives a Yaesu-specific recovery hint for the `rigctld` backend when the pinned profile is a Yaesu-family route.
- Added [YaesuRigctldBenchResults.md](/D:/Workshop/CWCN/YaesuRigctldBenchResults.md) as the paste target/template for the next real Yaesu-family bench sessions.

### Why this matters

- The app now not only has a Yaesu-family formal path, but also starts speaking the right operational language when that path is selected.
- This should lower friction during the first FT-family bench rounds because:
- `Rig Setup`
- `Probe`
- `TX recovery hint`
- and result logging
- now all point to the same short-test-first workflow.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest --tests org.bi9clt.cwcn.core.rig.HamlibRigctldRigControlAdapterTest --tests org.bi9clt.cwcn.core.tx.CwTxRouteAdvisorTest assembleDebug`

## 2026-04-24 Native Yaesu Serial CAT Probe Start

### What just changed

- Native serial-CAT work has now started for the Yaesu line instead of staying only at rigctld families.
- Added the new serial CAT session layer:
- [SerialCatSession.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatSession.java)
- [SerialCatSessionFactory.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatSessionFactory.java)
- [AndroidUsbCdcAcmSerialCatSession.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/AndroidUsbCdcAcmSerialCatSession.java)
- [AndroidUsbSerialCatSessionFactory.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/AndroidUsbSerialCatSessionFactory.java)
- Added the first native probe helper:
- [SerialCatProbe.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatProbe.java)
- Current behavior:
- only `Yaesu-style CAT` is probed for now
- probe uses a safe read-first approach such as `FA;` / `IF;`
- result distinguishes:
- permission / no-device / no-cdc / no-response / readable response
- [RigSetupActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java) and [activity_rig_setup.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_rig_setup.xml) now include:
- `Request Serial CAT USB Permission`
- `Test Serial CAT Connection`
- serial probe status text
- Added native-serial Yaesu bench docs:
- [YaesuSerialCatBenchChecklist.md](/D:/Workshop/CWCN/YaesuSerialCatBenchChecklist.md)
- [YaesuSerialCatBenchResults.md](/D:/Workshop/CWCN/YaesuSerialCatBenchResults.md)
- [TestingWorkbench.md](/D:/Workshop/CWCN/TestingWorkbench.md) now includes that new native Yaesu serial-CAT path.

### Why this matters

- The app is no longer only saying "Yaesu later".
- We now have the first native-serial Yaesu path that can validate:
- USB CDC/ACM presence
- permission flow
- baud/link sanity
- basic CAT responsiveness
- TX over native Yaesu CAT is still not complete, but the transport/probe seam now exists and is testable.
- This is the exact seam to reuse next for `ICOM CI-V`.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.SerialCatProbeTest assembleDebug`
- `.\gradlew.bat assembleDebug`

## 2026-04-24 Icom CI-V Native Probe Follow-Up

### What just changed

- The native serial-CAT seam is no longer Yaesu-only.
- [SerialCatSession.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatSession.java) now supports raw binary transaction flow as well as ASCII helpers, so:
- Yaesu-style ASCII CAT
- Icom CI-V binary frames
- can share one serial layer
- [RigProfileSettings.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigProfileSettings.java) and [RigSelectionStore.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigSelectionStore.java) now persist:
- `CI-V address hex`
- [RigSetupActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java) and [activity_rig_setup.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_rig_setup.xml) now expose that address in the serial CAT editor.
- [SerialCatProbe.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatProbe.java) now supports:
- `Yaesu-style CAT` probe via safe ASCII read commands
- `Icom CI-V` probe via a safe binary transceiver-ID query
- Added Icom native probe docs:
- [IcomCivBenchChecklist.md](/D:/Workshop/CWCN/IcomCivBenchChecklist.md)
- [IcomCivBenchResults.md](/D:/Workshop/CWCN/IcomCivBenchResults.md)
- [TestingWorkbench.md](/D:/Workshop/CWCN/TestingWorkbench.md) now includes both:
- native Yaesu serial CAT
- native Icom CI-V

### Why this matters

- The "first Yaesu, then directly Icom" sequencing is now reflected in the codebase itself.
- We now have one reusable native-serial seam that already covers:
- Yaesu read/probe validation
- Icom read/probe validation
- The next native family work no longer needs to reinvent:
- USB CDC/ACM session opening
- permission flow
- serial-link probing shape
- This sharply lowers the cost of continuing toward:
- native Icom stabilization
- later native Kenwood

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.SerialCatProbeTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest assembleDebug`

## 2026-04-24 Test Workbench + More rigctld Families

### What just changed

- Added [TestingWorkbench.md](/D:/Workshop/CWCN/TestingWorkbench.md) as the single entry page for current bench/testing markdowns.
- It currently links:
- USB / keyer TX checklist and results
- Yaesu FT-family rigctld checklist and results
- supporting context docs
- Expanded the formal rig catalog again so `rigctld` is no longer only:
- generic
- Yaesu-family
- Added two more `Bench-ready` formal profile families:
- `Icom Family via rigctld`
- `Kenwood Family via rigctld`
- Added small family-detection helpers so:
- profile summary
- rigctld availability/probe wording
- TX recovery hints
- can now speak more directly for:
- Yaesu
- Icom
- Kenwood
- while still sharing the same current `Hamlib rigctld` backend

### Why this matters

- Test docs are now easier to find from one place.
- "Other RIG support" has now moved from abstract future intent to actual formal entry points in the app for:
- Yaesu
- Icom
- Kenwood
- The current strategy remains:
- use `rigctld` as the shortest formal bridge for major families first
- delay native per-family Android CAT implementations until real bench feedback accumulates

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest --tests org.bi9clt.cwcn.core.rig.HamlibRigctldRigControlAdapterTest --tests org.bi9clt.cwcn.core.tx.CwTxRouteAdvisorTest assembleDebug`

### Next likely branch

- Install the latest `cwcn-debug.apk`.
- Use the default RX Focus view for low-volume/moderate-volume phone-speaker tests.
- Only expand detailed panels when a pasted result needs deeper timing/decoder/QSO inspection.

## 2026-04-24 Rig Integration Skeleton Checkpoint

### What just changed

- Formal rig work has started; RX debug can stay mostly frozen unless new field bugs appear.
- Added a new profile-oriented rig layer:
- `RigCapability`
- `RigSupportLevel`
- `RigProfile`
- `RigProfileCatalog`
- Added seed profile families for:
- Audio VOX
- USB serial keyer
- mock USB bench
- serial CAT/PTT
- network CAT
- Bluetooth serial
- Added a new `Rig Setup` screen and a Home entry for it.
- This screen is intentionally still read-mostly:
- transport readiness
- profile family summary
- capability/setup/constraint overview
- recommended next integration order
- It can now also persist the currently pinned rig profile family so later formal RX/TX pages have a stable default route.
- Added [RigIntegrationUiPlan.md](/D:/Workshop/CWCN/RigIntegrationUiPlan.md) to freeze the intended layering and build order.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest`
- `.\gradlew.bat assembleDebug`

### Next likely branch

- Keep Debug and TX bench stable.
- Grow `Rig Setup` from read-only summary into actual selection/state storage.
- Define CAT profile schema before attaching concrete vendor/model support.
- Then start shaping formal RX/TX workflow pages around the selected rig profile instead of around debug tooling.

## 2026-04-24 Rig Setup Persistence + TX Default Bridging

### What just changed

- `Rig Setup` no longer stores only:
- one pinned profile id
- one global shared configuration blob
- It now persists configuration by rig profile family, so switching between:
- `Audio VOX`
- `USB Serial Keyer`
- `Serial CAT`
- `Network CAT`
- `Bluetooth Serial`
- no longer overwrites the other families' saved defaults.
- `RigSetupActivity` was tightened so the page now distinguishes:
- the currently selected profile preview
- the currently pinned default rig path
- the selected profile's own saved configuration snapshot
- Home rig summary now renders the compact saved configuration summary instead of only support-level metadata.
- TX is now partially bridged onto formal rig setup:
- default `WPM`
- default tone frequency
- USB preferred device hint fallback
- USB key-line fallback
- are loaded from the pinned rig profile settings when console-local TX preferences are absent.

### Practical effect

- `Rig Setup` has moved from "informational skeleton" to a real state source.
- The formal rig path now starts influencing the TX console instead of living only in documentation/UI copy.
- This is the first small step toward making:
- `Rig Setup`
- `TX`
- later `RX`
- part of one coherent operator workflow.

### Verification

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest`

### Next likely branch

1. Define concrete CAT setup schema and support fields beyond the current generic placeholders.
2. Let formal RX/TX operating pages consume more of the selected rig profile instead of carrying parallel ad-hoc preferences.
3. Keep Debug tools available, but continue demoting them from the primary user journey.

## 2026-04-24 CAT Schema v1 Checkpoint

### What just changed

- The CAT line is no longer only:
- serial baud / port hint
- network host / port
- A reusable CAT protocol-family layer now exists through:
- `CatProtocolFamily`
- first values:
- `Generic CAT`
- `Yaesu-style CAT`
- `Icom CI-V`
- `Kenwood-style CAT`
- `Hamlib rigctld`
- `Rig Setup` now exposes protocol-family selectors for:
- serial CAT
- network CAT
- Per-profile persisted rig settings now include those CAT-family selections.
- Rig summaries now render the selected CAT family directly, so later screens can show more than just "serial" or "network".

### Why this matters

- The next CAT work no longer starts from a blank config shape.
- We now have a clean seam between:
- transport
- protocol family
- later concrete vendor/model profile
- later concrete adapter implementation
- That should reduce rework when we start attaching:
- Yaesu-like serial CAT
- Icom CI-V specifics
- Kenwood-style serial CAT
- rigctld/network bridge profiles

### Verification

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest`

### Next likely branch

1. Add concrete CAT-oriented rig profile families on top of the new protocol schema.
2. Decide which real adapter path lands first:
- direct serial CAT
- CI-V-style serial CAT
- network `rigctld`
3. Surface the selected rig/protocol family more visibly in formal operating screens, not only in Rig Setup and Home.

## 2026-04-24 Concrete CAT Family Seed Checkpoint

### What just changed

- Added the first concrete CAT-family catalog seeds:
- `Generic Yaesu Serial CAT`
- `Generic Icom CI-V`
- `Generic Kenwood Serial CAT`
- `Generic Hamlib rigctld`
- These profiles are still `PLANNED`, but they now sit above the shared CAT schema instead of everything collapsing into one generic CAT bucket.

### Why this matters

- We now have a cleaner roadmap:
- shared CAT schema
- concrete CAT family profile
- real adapter implementation
- real vendor/model refinement
- That makes the next implementation choice more explicit and lowers the chance that one radio family's needs distort the whole rig layer too early.

### Verification

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest`

### Next likely branch

1. Choose one concrete CAT family as the first implementation target.
2. Add only the missing fields that family truly needs.
3. Keep the other families in catalog/documentation shape until the first adapter path is proven.

## 2026-04-24 CAT Family Defaulting Checkpoint

### What just changed

- `RigProfile` can now carry profile-specific recommended default settings.
- Concrete CAT families now use that mechanism so they no longer open with the same generic CAT defaults.
- First-pass default mapping now includes:
- `Generic Yaesu Serial CAT` -> `Yaesu-style CAT`, `38400 baud`
- `Generic Icom CI-V` -> `Icom CI-V`, `19200 baud`
- `Generic Kenwood Serial CAT` -> `Kenwood-style CAT`, `57600 baud`
- `Generic Hamlib rigctld` -> `Hamlib rigctld`

### Why this matters

- The `Rig Setup` experience now starts feeling like concrete rig-family setup rather than a generic placeholder form.
- More importantly, this gives us a stable bridge from:
- concrete CAT family profile
- to first working adapter
- without adding extra ad-hoc branching in the UI layer.

### Verification

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest`

### Next likely branch

1. Choose one CAT family as the first real backend target.
2. Add only the family-specific setup fields that target really needs.
3. Keep the remaining CAT families at the current "well-modeled placeholder" level until the first adapter lands.

## 2026-04-24 Rig Setup Override-State Checkpoint

### What just changed

- `Rig Setup` now distinguishes:
- recommended defaults from the profile family
- saved overrides for that profile
- Added profile-scoped reset behavior:
- `Restore Recommended Defaults`
- only affects the currently selected profile
- does not disturb other saved rig families
- `RigSelectionStore` now explicitly supports:
- checking whether a profile has saved overrides
- clearing a profile's saved overrides

### Why this matters

- The per-profile persistence model is now visible and understandable in the UI.
- This lowers the risk of continuing CAT-family experiments because a user can always return one family to its recommended baseline quickly.
- More importantly, the setup UX is now mature enough that the next high-value step is no longer more editor polish.

### Verification

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest`

### Next likely branch

1. Freeze `Rig Setup` UX for the moment unless a concrete usability issue appears.
2. Choose the first real CAT backend target.
3. Build the smallest viable backend/session scaffold for that target instead of adding more setup fields first.

## 2026-04-24 Hamlib rigctld Skeleton Checkpoint

### What just changed

- The first real CAT backend scaffold now exists:
- `Hamlib rigctld`
- Added:
- socket-backed session abstraction
- socket-backed session factory
- real `HamlibRigctldRigControlAdapter`
- registry integration so TX can see the backend
- route-advisor guidance for the new backend
- targeted unit coverage
- Current adapter path uses:
- `KEYSPD`
- `CWPITCH`
- `send_morse`
- `PTT`
- against a configured rigctld host/port.

### Why this matters

- The project has now crossed from:
- CAT schema only
- into:
- actual CAT backend implementation skeleton
- This is an important milestone because future CAT work can now iterate on:
- diagnostics
- connection testing
- real bench behavior
- instead of still debating only models and settings.

### Verification

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.HamlibRigctldRigControlAdapterTest --tests org.bi9clt.cwcn.core.tx.CwTxRouteAdvisorTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest`

### Next likely branch

1. Improve operator-visible rigctld failure diagnostics.
2. Add a small manual connection/probe action if needed.
3. After one round of real bench feedback, decide whether the next investment stays in `rigctld` or shifts to direct serial CAT.

## 2026-04-24 rigctld Diagnostics Follow-Up

### What just changed

- TX-side diagnostics for the new `rigctld` backend are now less generic.
- `RigTextTxBackend` now includes the adapter availability/connection note in its final error status when a send fails.
- `TxActivity` now has a dedicated recovery hint for `rig-text:hamlib-rigctld`.

### Why this matters

- The first network-CAT bench pass will now leave better evidence in the TX status text without requiring a larger new probe UI first.
- This should make the next round of pasted results much easier to interpret.

### Verification

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.HamlibRigctldRigControlAdapterTest --tests org.bi9clt.cwcn.core.tx.CwTxRouteAdvisorTest --tests org.bi9clt.cwcn.core.tx.RigTextTxBackendTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest`

## 2026-04-24 rigctld Probe Checkpoint

### What just changed

- `Rig Setup` now includes a lightweight `Test rigctld Connection` action in the network CAT section.
- The probe uses the current editor values directly:
- selected profile
- selected network CAT family
- host
- port
- no save step is required before testing.
- The probe path now asks rigctld for basic rig info through `getInfo()` and reports the first returned line when available.

### Why this matters

- The first real rigctld bench loop is now much safer:
- probe host/port first
- then do short TX
- This reduces the need to use full `send_morse` as the first diagnostic move.

### Verification

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.HamlibRigctldRigControlAdapterTest --tests org.bi9clt.cwcn.core.tx.CwTxRouteAdvisorTest --tests org.bi9clt.cwcn.core.tx.RigTextTxBackendTest --tests org.bi9clt.cwcn.core.rig.RigProfileCatalogTest --tests org.bi9clt.cwcn.core.rig.RigProfileConfigurationFormatterTest`

### Next likely branch

1. Run the first real rigctld probe against an actual endpoint.
2. If probe works, do a very short TX bench through `send_morse`.
3. If probe fails, improve rigctld-specific diagnostics before branching back into other CAT families.

## 2026-04-24 TX Pinned-Rig Default Routing Checkpoint

### What just changed

- `TX Console` now follows the pinned rig path more directly on first entry.
- [TxActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/tx/TxActivity.java) now:
- resolves the pinned rig profile from `RigSelectionStore`
- maps its adapter id to the matching TX backend when that backend exists
- auto-selects that backend instead of always starting on `Local Sidetone`
- falls back safely to `Local Sidetone` when the pinned rig does not currently expose a usable text-to-CW backend in TX
- [activity_tx.xml](/D:/Workshop/CWCN/cwcn-android/app/src/main/res/layout/activity_tx.xml) now includes a compact pinned-rig summary block directly under the backend selector.
- That summary shows:
- pinned rig path and saved defaults
- preferred TX route inferred from the pinned rig
- currently active TX route
- a note when TX is temporarily using a different backend than the pinned rig path

### Why this matters

- `Rig Setup` is now not only a source of defaults, but also the default TX route choice.
- This reduces the feeling that `TX Console` is a separate parallel configuration world.
- It also gives us a clearer seam for the next step:
- tighter pinned-rig status in TX
- or real rig bench feedback against the now-auto-selected route

### Verification

- `.\gradlew.bat assembleDebug`

## 2026-04-24 Splash Entrance Polish

### What just changed

- `SplashActivity` is no longer just a holding page before `Home`.
- Added a dedicated splash theme and launcher background so app start should feel less abrupt and reduce startup flash.
- The splash screen now shows:
- logo
- author callsign
- version
- build timestamp
- build channel
- The screen also now uses a small staged entrance animation instead of everything appearing at once.
- Auto-enter behavior is still preserved, but delayed navigation is now cleaned up explicitly instead of relying on the earlier `onPause` shortcut.

### Why this matters

- We now have a more credible first impression for normal users while still keeping all existing bench/debug paths intact behind the later screens.
- This is a good place to pause UI churn before tonight's Yaesu hardware run.

### Verification

- `.\gradlew.bat assembleDebug`

### Next likely branch

1. Hold `Splash -> Home -> Operate -> Rig Setup` steady through the next real-device pass.
2. Use that pass to decide whether the next UX work belongs in:
- `Operate`
- `Rig Setup`
- or a more formal non-debug TX entry.

## 2026-04-25 FT-710 Native USB Serial Widening

### What just changed

- Real-device bench evidence became much sharper:
- CWCN native serial CAT said `No CDC/ACM serial CAT device is attached`
- but the same phone/cable/radio path was already working in `FT8CN`
- That strongly suggested CWCN's native Android USB serial detection was too narrow rather than the field setup being fundamentally wrong.
- `AndroidUsbSerialCatSessionFactory` now widens its native USB serial discovery:
- probe `CP210x` first
- keep `CDC/ACM` fallback
- return a shared serial session wrapper for either path
- Added a small internal USB serial mini-layer instead of importing the entire FT8CN serial stack at once.
- Current scope is intentionally conservative:
- `FT-710`-motivated `CP210x`
- plus existing `CDC/ACM`
- not yet the full FTDI / CH34x / Prolific family spread

### Why this matters

- The next FT-710 phone bench no longer depends on the USB device presenting as pure `CDC/ACM`.
- This should move the native Yaesu path one layer forward:
- from immediate "no device"
- to either:
- permission flow
- serial open/probe
- or real CAT reply/no-reply evidence

### Verification

- `.\gradlew.bat assembleDebug`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.SerialCatProbeTest --tests org.bi9clt.cwcn.core.rig.SerialCatRigControlAdapterTest`

### Next likely branch

1. Install the fresh debug APK and repeat the FT-710 native serial CAT bench on the same phone/cable pair.
2. If native detection now advances past "no device", use the exact new failure/success text to decide whether the next work is:
- Yaesu read/write behavior
- FT-710 write-heavy / no-read strategy
- or more USB driver coverage beyond `CP210x`.

## 2026-04-25 FT-710 Permission Crash Hardening

### What just changed

- User then reported a more severe field problem:
- tapping `Request Serial CAT USB Permission`
- could make the app become unresponsive / repeatedly stop
- Hardened the native serial CAT path so probe failures should now degrade into status text instead of process death.
- [UsbSerialMiniProbe.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/UsbSerialMiniProbe.java) no longer uses the risky reflective constructor path for the mini `CP210x` driver.
- [AndroidUsbSerialCatSessionFactory.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/AndroidUsbSerialCatSessionFactory.java) now:
- wraps availability / permission / open-session probe failures
- logs probe crashes
- skips broken candidates instead of letting one driver-construction failure kill the whole path
- [RigSetupActivity.java](/D:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java) now hardens:
- `Request Serial CAT USB Permission`
- `Test Serial CAT Connection`
- `Pulse Serial CAT TX/PTT`
- so worker-thread failures should surface as UI status text rather than uncaught exceptions
- Also added regression coverage so runtime exceptions in the serial session path now return readable failure results instead of escaping.

### Verification

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.rig.SerialCatProbeTest --tests org.bi9clt.cwcn.core.rig.SerialCatRigControlAdapterTest assembleDebug`
- Fresh APK built:
- [cwcn-debug.apk](/D:/Workshop/CWCN/cwcn-android/app/build/outputs/apk/debug/cwcn-debug.apk)

### Next likely branch

1. Reinstall the fresh debug APK on the same phone used beside the `FT-710`.
2. Tap `Request Serial CAT USB Permission` again.
3. Observe whether the behavior now becomes:
- Android permission dialog
- a stable on-screen status message
- or a different failure text instead of app death
4. Paste back the exact new status text if it still does not connect.

## 2026-04-28 Crowded-Band Asymmetry Checkpoint

### What just converged

- Added paired crowded-band fixtures for asymmetric adjacent occupancy:
- `user_left_adjacent_occupancy_cq_700hz`
- `user_right_adjacent_occupancy_cq_700hz`
- Added matching coverage in:
- [CwCrowdedBandCoverageTest.java](/D:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/audio/CwCrowdedBandCoverageTest.java)

### What the paired results mean

- The current front end does **not** behave symmetrically when most adjacent occupancy sits below versus above the target tone.
- Current observed shape:
- left-heavy occupancy tends to keep tracking nearer the target region, but text recall degrades more
- right-heavy occupancy more readily locks high and can follow the stronger upper interferer, while the CQ skeleton may still remain more legible
- This means:
- do not encode fake `left == right` expectations into regression tests
- keep the paired case as an `observability + no-collapse` boundary
- treat the asymmetry itself as a real algorithm clue for later acquisition work

### Verified focused suite

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwUserCaptureCoverageTest --tests org.bi9clt.cwcn.core.audio.CwCrowdedBandCoverageTest --tests org.bi9clt.cwcn.core.audio.CwEdgeToneStressProbeTest --tests org.bi9clt.cwcn.core.audio.CwSpeedShiftRestabilizationProbeTest`

### Best next crowded-band branch

1. extend the same left/right occupancy idea to a longer QSO payload
2. combine asymmetric occupancy with a mid-stream WPM shift
3. add `wrong preferred + crowded band` as a `probe/stress` matrix before trying to promote it into strict coverage

## 2026-04-28 Same-Tone Dual-Sequence Probe

### What just changed

- The synthetic fixture engine can now render a second independently keyed Morse interferer, not just continuous/drifting/bursty carriers.
- First same-tone keyed probe landed:
- `user_same_tone_dual_sequence_target_priority_700hz`
- The target branch is `CQ CQ DE BI9CLT BI9CLT PSE K`
- The interfering branch is a different same-tone keyed sequence:
- `VVV VVV DE JA1ABC JA1ABC BK`

### Why this matters

- Using two different same-tone sequences gives us branch visibility that the earlier cochannel proxy did not.
- If future decode starts favoring one branch, we will be able to see whether it followed the `CQ/BI9CLT` side or the `VVV/JA1ABC` side.

### Current result

- This is currently an `observability/probe` case, not a success regression.
- The front end can keep a healthy same-tone lock near `700Hz`, but downstream timing/character recovery still collapses badly under true keyed cochannel overlap.
- In other words:
- lock is not the same as branch discrimination
- same-tone dual-keyed copy is still an open algorithm problem

### Amplitude-matrix follow-up

- Added two first same-tone amplitude variants:
- `user_same_tone_dual_sequence_target_dominant_700hz`
- `user_same_tone_dual_sequence_interferer_dominant_700hz`
- Important result:
- even when the target branch is made clearly stronger, the current decoder still does not recover the intended `CQ / BI9CLT` sequence reliably
- the front end locks cleanly at the right tone, but the downstream text path still smears/collapses
- practical interpretation:
- the next real improvement is not just "more lock"
- we need an actual branch-selection or branch-stability strategy after lock, otherwise stronger-signal preference alone is not yet expressing itself in decoded text

### Verified focused suites

- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwCrowdedBandCoverageTest`
- `.\gradlew.bat testDebugUnitTest --tests org.bi9clt.cwcn.core.audio.CwUserCaptureCoverageTest --tests org.bi9clt.cwcn.core.audio.CwCrowdedBandCoverageTest --tests org.bi9clt.cwcn.core.audio.CwEdgeToneStressProbeTest --tests org.bi9clt.cwcn.core.audio.CwSpeedShiftRestabilizationProbeTest`

## 2026-04-28 Recording (2) Acquisition Note

### What was just verified

- `CwLocalAudioFolderRegressionTest` fixture labels were repaired back to the real `录音...` names, so local-audio regression is usable again.
- After that repair, the only new red case was the experimental `录音 (2)` acquisition assertion.
- Current observed shape for `录音 (2)` remains:
- `effectiveTrackedToneFrequencyHz ~= 660`
- but `preferredWindowWinner / wideScanWinner / acquisitionWinner` still stick at `450`
- and `finalAdoptedSource` is still `SEARCH_FALLBACK`

### What we learned

- A simple stronger absolute-edge penalty was **not** the right fix:
- it did not clear `录音 (2)`
- and it degraded `录音 (8)` observability
- So this branch should proceed via better acquisition-process observability first, not broader score punishment.

### Current stable test stance

- Keep `录音 (2)` in regression as a guard that it eventually recovers off the low edge.
- Do **not** yet hard-gate `acquisitionWinner != 450` until we expose more of the candidate/runner-up decision path.
