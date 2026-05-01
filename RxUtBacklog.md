# RX UT Backlog

Last updated: 2026-05-01

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
- The synthetic mirror fixture now exists as `user_speed_shift_jv3vv_700hz`.
- Its current hybrid/default-interpreter behavior is better than the old loose gate suggested:
- raw decoder continuity is strong (`48` chars with the expected two-round skeleton intact)
- but normalized/token recall is still only around `0.76` because the interpreter currently glues `JV3VV PAGE` into `JV3VVPAGE`
- So this case should currently guard:
- front-end continuity
- raw decoder structure
- and "no major interpreter regression"
- rather than pretending the remaining bottleneck is still front-end timing collapse.

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

## 2026-05-01 Normalize 收敛方向

### 背景判断

- 当前 `Normalize` 已经不只是“把解码结果整理一下”。
- 它同时承担了：
- 原始可见文本稳定化
- token 级规范化
- 受损 `RST / control` 结构恢复
- 粘连 token 拆分
- 呼号修补 / remembered callsign 升级
- 主呼号选择
- hint 推导

- 这导致 `normalizedText` 已经开始带有较强“猜词”和“重写”倾向。
- 对 RX 主线来说，这个方向风险偏高，因为 RAW 本应尽量保留现场，Normalize 只该做窄而明确的结构恢复。

### 当前准备保留的能力

- `stabilizeVisibleRawText`
- 非侵入的标点/残余清理
- 很窄的结构恢复：
- `DE`
- `BK / KN / K`
- `UR / RST / 5NN / 599 / ENN`
- 基于上下文的受损 `report/control` 恢复
- 保守的呼号碎片合并
- 重复呼号 run 归一
- `?` 邻域的不确定呼号合并

### 当前准备收缩或删除的能力

- 语义英文改写：
- `TU/TNX -> THANKS`
- `AGN -> AGAIN`
- `PSE/PLS -> PLEASE`
- `CALLING -> CALL`
- `CALL-SIGN -> CALLSIGN`
- 泛化的 keyword 强拆：
- prefix split
- suffix split
- bridge split
- 宽松的呼号修补候选扩散
- 任何试图从“看起来干净的 token”里猜出隐藏操作词的逻辑

### 风险最高的现有点

1. `splitKeywordSuffixedToken(...)`
- 会把看起来完整的 token 强行拆成 `CALLSIGN + FB/SK`
- 对包含 `SK/FB` 尾部的真实呼号或脏 token 都有副作用

2. `splitKeywordPrefixedToken(...)`
- 会把前缀命中的 token 强行解释成结构词开头
- 容易把本来应保留的 RAW token 过早“语义化”

3. `splitKeywordBridgedToken(...)`
- 会在 token 中间寻找 bridge keyword
- 覆盖面最广，也最容易误拆

4. `deriveCallsignRepairVariants(...)`
- 候选扩散太宽时，会把“恢复”推成“猜测”

5. `normalizeToken(...)` 中的英文语义重写
- 这类重写更适合下游解释层，不适合直接侵入 `normalizedText`

### 拟执行的瘦身顺序

1. 先删除 `splitKeywordSuffixedToken(...)`
2. 再评估并删除 `splitKeywordPrefixedToken(...)`
3. 再评估并删除 `splitKeywordBridgedToken(...)`
4. 再收掉 `normalizeToken(...)` 中的语义英文改写
5. 最后再收窄 `deriveCallsignRepairVariants(...)`

### 当前执行进度

- 已完成：
- 删除 `splitKeywordSuffixedToken(...)`
- 将 `prefix split` 收窄为只允许结构词：
- `QRZ / CQ / DE / UR / BK / KN / 5NN / 599 / ENN / TU / TNX / 73`
- 将 `bridge split` 收窄为只允许结构词：
- `DE / QRZ / UR / BK / KN / 5NN / 599 / ENN / TU / TNX / 73 / CQ`

- 已确认：
- 上述收窄后，`CwInterpreterCallsignRecoveryTest`
- 以及 `CwConversationSemanticsTest`
- 仍保持通过

- 下一步：
- 评估 `normalizeToken(...)` 中的语义英文改写是否应继续留在 `normalizedText`
- 优先检查：
- `TU/TNX -> THANKS`
- `AGN -> AGAIN`
- `PSE/PLS -> PLEASE`
- `CALLING -> CALL`
- `CALL-SIGN -> CALLSIGN`

### 2026-05-01 进一步进展

- 已完成：
- `normalizeToken(...)` 不再把以下 token 直接改写进 `normalizedText`：
- `TU/TNX -> THANKS`
- `AGN -> AGAIN`
- `PSE/PLS -> PLEASE`
- `CALLING -> CALL`
- `CALL-SIGN -> CALLSIGN`

- 当前做法：
- `normalizedText` 尽量保留原始缩写
- 语义分类 / hint / QSO phase 改为通过单独的 semantic-token 识别层理解这些缩写

- 已确认通过的窄回归：
- `CwInterpreterCallsignRecoveryTest`
- `CwInterpreterRawCopyFocusTest`
- `CwConversationSemanticsTest`

- 下一步收尾：
- 清理 `FixtureLibrary` / eval synthetic tests 中仍硬编码 `AGAIN / PLEASE / THANKS` 作为 normalized 预期的案例
- 让 fixture/eval 口径与新的 normalize 边界保持一致

### 执行原则

- 一次只做一刀，避免同时改太多层
- 每一刀都用窄 UT 验证，不直接大范围改主线
- 目标不是“让更多 case 看起来变绿”
- 目标是让 `RAW -> Normalize -> Semantics` 的职责重新变清楚

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

## 2026-04-30 Immediate Next Work

Branch decision after recording `(8)` front-end probes:

- Stop using recording `(8)` as a direct RX-tuning target.
- Treat it as source / recording-chain evidence, not as a clean decoder truth source.
- Keep the new local-audio probes for observability, but move active engineering back to controllable synthetic tests.

Next execution order:

1. Add a synthetic `weak valley / merged tone` front-end regression in [CwSignalProcessorTest.java](/d:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java).
2. Use that case to study whether current `release` behavior is too sticky around shallow valleys.
3. Tighten mainline red-line assertions for clean / stable / 30WPM cases before any front-end behavior change.
4. Prefer future work on controllable synthetic distortion and cleaner fixtures over further tuning against noisy phone-to-PC recordings.

Status update after the narrow release-timing pass:

- `weak valley / merged tone` synthetic regressions are now in place, and the current merge boundary is effectively between valley amplitudes `2400` and `2700`.
- The narrow RX code change so far is only the `TONE_OFF` confirmation window using `frameEndTimestampMs - silenceStartedAtMs`, intended to stop one-frame-oversticking on weak valleys.
- Next immediate cleanup is to lock the same-frame `TONE_OFF` boundary with stable UT assertions:
  - `FRAME_SIZE / 16` tail should already emit `TONE_OFF` inside the transition frame.
  - `FRAME_SIZE / 8` tail should still need the next frame.
- After that, continue with red-line assertion tightening for clean / stable / 30WPM coverage before any further RX behavior change.

## 2026-04-30 Pipeline Fact Check

Confirmed by code-path inspection:

- The live RX/decode chain is:
  `CwSignalProcessor.process(frame)` -> `List<CwToneEvent>` ->
  `CwHybridTimingModel.process(toneEvent)` -> `List<CwTimingEvent>` ->
  `CwDecoder.process(timingEvent)` -> decoded text / interpreter / QSO state.
- [CwToneEvent.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/signal/CwToneEvent.java) carries only:
  `type`, `timestampMs`, `peakAmplitude`, `rmsAmplitude`, `toneDurationMs`.
- Therefore the downstream timing/decoder path does **not** directly consume:
  `effectiveTrackedToneFrequencyHz`, `targetToneFrequencyHz`, `finalAdoptedFrequencyHz`,
  `acquisitionWinnerFrequencyHz`, or any other front-end tone-frequency snapshot field.
- Those frequency fields are currently:
  - used for observability / UI / fixture evaluation / health classification
  - and for the **next-frame behavior inside the front-end itself**
  - but not passed as an explicit input into timing classification or Morse decoding.

Implication:

- If a case shows `target/final` vs `effectiveTracked` divergence, that divergence by itself does **not**
  prove a decoder-path regression.
- The real danger is only when front-end tone-selection state changes future `CwSignalProcessor`
  behavior enough to alter emitted `CwToneEvent` timing/duration structure.
- So the next optimization step should focus on whether a front-end tone-state change modifies
  emitted `TONE_ON/TONE_OFF` boundaries, not on the snapshot frequency labels alone.

Synthetic follow-up result:

- Added a narrow synthetic replay probe in
  [CwSignalProcessorTest.java](/d:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/signal/CwSignalProcessorTest.java)
  for two scenarios where `target/finalAdopted` has already moved but `effectiveTracked` still lags:
  - `release-retune`
  - `step-sweep`
- Replaying the same frames with per-frame forced `TRK` vs per-frame forced `EFF` shows:
  - the actual emitted `CwToneEvent` timeline is closer to `EFF` replay than to `TRK` replay
  - in both synthetic cases, forcing `TRK` creates extra `TONE_OFF/TONE_ON` churn that the actual pipeline does not emit
- Working interpretation:
  - in these lag scenarios, `effectiveTracked` currently matches the real emitted event structure better than
    `target/finalAdopted`
  - so any future RX optimization must be careful not to force `target/final` directly into the event-emission path
    without proving it improves `CwToneEvent` boundaries rather than increasing churn

## 2026-05-01 RAW Channel Principle

This is now a hard baseline principle for RX evaluation and interpreter cleanup:

- `RAW` must only describe what the original decode stream actually emitted.
- `RAW` must not do token merge, token split, semantic rewrite, or human-friendly cleanup beyond minimal visible rendering.
- If we want to make text easier for humans to read, that belongs to `Normalize`.
- `Normalize` may improve readability and recover structure, but it should preserve information rather than silently rewriting the original channel.
- The intended responsibility chain is:
  - `RAW`: observed stream
  - `Normalize`: readable structural recovery
  - `Semantics`: meaning / QSO interpretation

### Narrow follow-up for `RAW_COPY_FOCUS`

- `normalizedText` may do minimal readability cleanup, but should still preserve the observed token form whenever possible.
- Classification may use a separate helper token path if needed, but display copy must not be rewritten just to satisfy classification.
- `RAW_COPY_FOCUS` callsign detection should stay more conservative than mainline semantic recovery.
- If a token looks like ambiguous residue such as `73B`, prefer `FREE_TEXT` over aggressive callsign promotion.

### 2026-05-01 Normalize 收敛补记

- Mainline `Normalize` should follow the same direction where possible:
  - preserve visible token form in `normalizedText`
  - move borderline interpretation into semantic classification / hint logic
- First narrow example now landed:
  - `73B`-style closing residue should contribute `73 closing` semantics
  - but should no longer be silently rewritten into visible `73` in `normalizedText`
- Working rule:
  - if a token still carries useful original surface information, prefer preserving that surface form
  - and let semantics explain it, rather than overwriting it in the display channel

### 2026-05-01 Visible Rewrite Audit

Current highest-risk `Normalize` behavior is no longer the old English semantic rewrite.
It is the remaining set of rules that still overwrite the visible token itself.

#### Direct visible-token rewrites still active

1. `5NN / ENN -> 599`
- Code path:
  - `normalizeToken(rawToken)` in `CwInterpreter`
- Behavior:
  - observed `5NN` or `ENN` does not remain visible in `normalizedText`
  - it is rewritten into `599`
- Risk:
  - this loses the operator-style surface form
  - it is a display rewrite, not just semantic understanding

2. damaged report residue -> `599`
- Code path:
  - `normalizeToken(rawToken, rawParts, index)`
  - `isLikelyDamagedReportToken(...)`
  - `isReportResidueContext(...)`
- Typical examples:
  - `?NN`
  - `5N?`
  - `UR?NN`
  - compact tail forms that are later structurally recovered into the report path
- Behavior:
  - when context looks like a report exchange, the visible token is rewritten into `599`
- Risk:
  - this is a stronger rewrite than plain readability cleanup
  - it replaces uncertain residue with a clean canonical report value

3. damaged control residue -> `BK / KN / K`
- Code path:
  - `normalizeDamagedControlToken(...)`
  - `bestEffortControlNormalization(...)`
- Typical examples:
  - `B`
  - `EB`
  - `?`
- Behavior:
  - when context looks like handoff / over, the visible token is rewritten into a clean control token
- Risk:
  - same issue as report residue recovery:
  - the display channel stops showing the observed residue and starts showing the inferred control word

#### Current test and eval coupling

- Interpreter tests currently lock this behavior as expected output:
  - `UR?NN B -> UR 599 BK`
  - `UR ?NN EB -> UR 599 BK`
  - `UR 5NNEB -> UR 599 BK`
  - `UR 5 NN B K -> UR 599 BK`
- There is also an explicit token-level assertion that recovered short residues are marked as `normalizedFromRaw`.
- Fixture/eval tests currently treat this as recovery pressure evidence:
  - `?NN->599`
  - `EB->BK`
  - summary wording like `Heavy best-effort normalization`

#### Working interpretation

- These rules were originally useful for:
  - preserving QSO-phase understanding
  - keeping report / handoff hints alive
  - improving fixture scoring on damaged copy
- But under the new `RAW -> Normalize -> Semantics` boundary, they are now the clearest examples of `Normalize` rewriting visible text too aggressively.

#### Recommended next narrowing order

1. first review `5NN / ENN -> 599`
- likely easiest to separate:
  - visible form stays `5NN / ENN`
  - semantic layer still understands it as report

2. then review damaged report residue -> `599`
- likely needs a two-channel answer:
  - preserve observed residue in visible text
  - keep report semantics / hints through classification

3. last review damaged control residue -> `BK / KN / K`
- same treatment as above, but slightly more context-sensitive

#### Guardrail for future changes

- If a rule exists mainly to improve interpretation or scoring, prefer moving it into:
  - semantic classification
  - hint generation
  - fixture/eval recovery accounting
- Do not silently spend visible-token fidelity unless the readability gain is clearly worth the information loss.

#### 2026-05-01 First narrowing landed

- `5NN / ENN` no longer need to be silently rewritten into visible `599` in mainline `normalizedText`.
- `report` semantics are now preserved through the semantic-token path instead:
  - report hints still fire
  - `UR 5NN` and `R 5NN` still behave like report sequences semantically
- Current intended boundary after this pass:
  - clean operator-style report forms like `5NN` remain visible
  - truly damaged report residue such as `?NN` may still be recovered more aggressively for now
- Verified with narrow interpreter regression:
  - `CwInterpreterCallsignRecoveryTest`
  - `CwInterpreterRawCopyFocusTest`

#### 2026-05-01 Second narrowing landed

- damaged report residue no longer needs to be silently rewritten into visible `599` in all cases.
- Current behavior after this pass:
  - clean operator report forms stay visible:
    - `5NN`
    - `ENN`
  - damaged visible residue such as `?NN` may now remain visible in `normalizedText`
    while still contributing report semantics when the token is classified in report context
  - QSO draft extraction still canonicalizes report-value tokens back to `599` for
    `rstSentCandidate` / `rstRcvdCandidate`
- Companion state-machine adjustment:
  - `Closing / acknowledgement` and `73 closing` hints are now trusted by phase derivation,
    so visible forms like `73B` do not need to be rewritten back into bare `73`
    just to keep `CLOSING / COMPLETED` phase transitions working.
- Verified with narrow regression:
  - `CwInterpreterCallsignRecoveryTest`
  - `CwInterpreterRawCopyFocusTest`
  - `CwConversationSemanticsTest`

#### 2026-05-01 Normalize audit: structure helpers still worth reviewing

- Current Normalize work in `CwInterpreter` is now mostly structural rather than semantic rewrite.
- The active rule families are:
  - short fixed-token merge:
    - examples: `5 NN -> 5NN`, `B K -> BK`, `T U -> TU`, `7 3 -> 73`
  - compound-chain split:
    - examples: `UR5NNBK -> UR 5NN BK`, `R5NNTU73BK -> R 5NN TU 73 BK`
  - `...DE` boundary split:
    - examples: `BI9CLTDE BG7YOZ -> BI9CLT DE BG7YOZ`
  - callsign-fragment merge / repeated-run split:
    - examples: `BG7 YOZ -> BG7YOZ`, `BI9CLTBI9CLT -> BI9CLT BI9CLT`
  - lexical cleanup:
    - trailing punctuation trim
    - `CALL SIGN -> CALLSIGN`

- Keep in mind:
  - these are easier to justify than `599/BK` semantic rewrites because they mainly add spacing or grouping
  - but some of them can still cross the line if they delete visible residue or duplicate unseen content

- First concrete suspect now tightened:
  - leading-edge noise before `DE` should not be silently dropped in Normalize
  - example:
    - before: `?DE BG7YOZ ... -> DE BG7YOZ ...`
    - now intended: `? DE BG7YOZ ...`

- Next suspicious buckets to review, in order:
  1. repeated-callsign run expansion that may over-complete a truncated suffix
  2. `CALL SIGN -> CALLSIGN` style lexical fusion that changes operator-visible form
  3. bridge/prefix splitting inside long glued chains where a true callsign might still be mis-cut

## Cross-Track Priority Snapshot

This file remains RX-test-centric, but we need one short project-wide ordering note so future work does not drift.

### Current repo reality

- RX core is the deepest and most differentiated asset right now:
  - front-end signal tracking
  - timing / decoder / interpreter / QSO stack
  - rich fixture + local-audio regression coverage
- TX rig integration is already further along than it may look at first glance:
  - formal `Rig Setup`
  - `Home` and `Operate` shell screens
  - USB serial keyer TX bench path
  - serial CAT bench/probe path
  - Hamlib `rigctld` network backend skeleton
- Normal-user operating UX is still shallow:
  - `Operate` is mostly a status shell
  - RX live operating page is not yet formalized
  - TX console remains bench-oriented
- RX hardware-input coverage is still narrow:
  - current `RxAudioSource` family is:
    - microphone
    - local file
    - synthetic fixture
  - there is not yet a formal external-radio RX input path such as dedicated USB-audio rig capture

### Recommended priority order

1. RX timing/raw observability and `word-gap` misclassification mitigation

- Why first:
  - this affects the trustworthiness of the entire RX chain
  - it is more leverage-heavy than more `Normalize` cleanup right now
  - it helps explain and de-risk future UI behavior instead of just polishing output text
- Preferred scope:
  - expose raw timing trace more clearly
  - preserve ambiguity when gap classification is not trustworthy
  - reduce self-reinforcing drift from gap-driven estimate updates

2. Formal user-facing RX operating screen

- Why second:
  - current RX power is mostly trapped in debug tooling
  - a real app needs a non-debug path for everyday decode + draft review
  - this converts core algorithm work into user-visible product value
- Goal:
  - promote the useful parts of `InputDebugActivity` into a simpler operating RX page
  - keep deep observability behind developer mode

3. Real radio integration path that normal users can actually use

- Why third:
  - rig setup and transport abstractions already exist
  - TX-side USB/CAT bench work is ahead of RX-side product flow
  - the next value step is not more bench knobs, but one or two truly usable end-to-end rig paths
- Suggested order inside this bucket:
  - first stabilize one practical TX path
    - USB serial keyer or one serial CAT family
  - then define the concrete RX capture route for a real radio
    - built-in mic fallback is fine
    - dedicated USB-audio / external capture can follow once the product path is clear

4. `Normalize` cleanup and over-rewrite reduction

- Why not first:
  - still important
  - but we already removed the most invasive semantic rewrites
  - remaining issues are mostly presentation-boundary quality, not the biggest current product blocker

5. Broader workflow polish

- examples:
  - richer logbook UX
  - QSO confirmation polish
  - more profile families
  - wider radio/vendor mapping

### Practical next milestone suggestion

If we want one milestone that best converts engineering effort into product progress, it should be:

- stabilize RAW timing observability around gap ambiguity
- then build the first formal RX operating page on top of that cleaner boundary
- then bind one real rig route as the recommended user path

## Executable Milestones

This is the recommended next build order after the current `RAW fidelity / Normalize boundary` cleanup round.

### P1. Make RAW timing trustworthy and inspectable

Goal:

- make `RAW` timing decisions easier to inspect
- reduce hidden `WORD_GAP -> LETTER_GAP` drift
- keep uncertainty visible instead of silently flattening it

Scope:

1. add a clearer raw-timing observability surface

- expose recent timing events in a compact developer-facing summary:
  - `DIT`
  - `DAH`
  - `INTRA_SYMBOL_GAP`
  - `LETTER_GAP`
  - `WORD_GAP`
  - `UNKNOWN`
- include enough metadata to reason about misclassification:
  - event duration
  - current dot estimate
  - ratio to dot estimate
  - ratio to intra-gap estimate when relevant

Likely files:

- [CwTimingEvent.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingEvent.java)
- [CwTimingSnapshot.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingSnapshot.java)
- [InputDebugActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)

2. mark gap ambiguity more explicitly

- preferred direction:
  - introduce a timing-level ambiguity concept for borderline long gaps
- if we do not want a new enum immediately:
  - at least add a developer-facing ambiguity flag / score
  - or a `soft word-gap promoted` marker at decoder level

Likely files:

- [CwTimingModel.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingModel.java)
- [CwAdaptiveTimingModel.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwAdaptiveTimingModel.java)
- [CwDecoder.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/decoder/CwDecoder.java)

3. reduce self-reinforcing estimate pollution

- be more conservative about letting `LETTER_GAP` update:
  - `dotEstimate`
  - `intraGapEstimate`
- especially for long or borderline gaps that may actually be word boundaries

Likely files:

- [CwTimingModel.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwTimingModel.java)
- [CwAdaptiveTimingModel.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/timing/CwAdaptiveTimingModel.java)

4. lock this with targeted UT and probe fixtures

- do not start with broad golden-text tests
- instead add tests that specifically verify:
  - a long-ish gap can stay visibly ambiguous
  - borderline word-gap cases do not over-pull the dot estimate
  - decoder soft-space promotion is visible as promotion, not confused with native `WORD_GAP`

Suggested success criteria:

- we can explain a bad glued-text case from timing logs alone
- timing estimate drift caused by long gaps becomes easier to detect
- we stop hiding timing uncertainty behind clean-looking text output

### P2. Build the first formal RX operating page

Goal:

- graduate the useful part of RX from `Debug` into a normal-user path
- keep deep probes in developer mode

Scope:

1. define a simple operating RX screen

- likely new screen:
  - `RxOperateActivity` or equivalent
- should show only:
  - input state
  - tone lock / readiness
  - live raw text
  - normalized text
  - draft summary
  - manual review / confirm actions

2. reuse core pipeline, do not fork logic

- reuse:
  - signal processor
  - timing model
  - decoder
  - interpreter
  - QSO state machine
- avoid building a second RX stack just for UI

3. move engineering-only details behind developer mode

- keep in debug only:
  - fixture evaluation
  - full token dump
  - probe-only metrics
  - batch regression helpers

Likely files:

- [OperateActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/operate/OperateActivity.java)
- [InputDebugActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/debug/InputDebugActivity.java)
- new RX activity/layout files under `ui/operate` or a sibling `ui/rx`

Suggested success criteria:

- a user can enter the app, pick rig/input path, and see live decode without opening Debug
- the page is understandable without knowledge of pipeline internals
- draft correction and confirmation remain available

### P3. Bind one recommended real-rig route end to end

Goal:

- stop treating rig integration as only bench tooling
- ship one concrete, supportable route that we can recommend to users

Recommended path order:

1. choose one TX/control route to bless first

- most practical candidates from current codebase:
  - USB serial keyer
  - one serial CAT family
  - Hamlib `rigctld` network route

2. define the matching RX capture story for that route

- important:
  - TX/control readiness is ahead of RX capture readiness right now
  - we should explicitly decide what the user uses for RX audio:
    - phone microphone fallback
    - local wired audio path
    - later dedicated USB-audio / external capture route

3. make `Rig Setup` and `Operate` point to that route as the preferred user journey

- reduce "bench-first" messaging on the normal path
- keep exotic probes inside developer mode

Likely files:

- [RigSetupActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/rig/RigSetupActivity.java)
- [RigRegistry.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/RigRegistry.java)
- [UsbSerialKeyerRigControlAdapter.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/UsbSerialKeyerRigControlAdapter.java)
- [SerialCatRigControlAdapter.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/SerialCatRigControlAdapter.java)
- [HamlibRigctldRigControlAdapter.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/rig/HamlibRigctldRigControlAdapter.java)

Suggested success criteria:

- we can name one route as "recommended first real-world setup"
- a user can set it up from `Home -> Rig Setup -> Operate`
- TX/control and RX observation are both covered by one coherent workflow

## Recommended immediate next task

If we only start one concrete task next, it should be:

- `P1.1 + P1.3`

Meaning:

- first add clearer raw timing observability
- then tighten how borderline long gaps feed back into timing estimates

Why this first:

- it supports later RX UI work
- it is easier to validate than a big user-UI jump
- it resolves an upstream ambiguity instead of polishing downstream text symptoms

## 2026-05-01 P1.3 Closure And Priority Shift

### What just closed

- `P1.1` raw timing observability is now landed enough to explain borderline gap behavior directly from timing traces:
  - duration
  - dot estimate
  - ratio to dot estimate
  - ratio to intra-gap estimate
- `P1.3` gap-feedback mitigation is now landed in a deliberately narrow form:
  - ambiguous long `LETTER_GAP` feedback suppression now applies only in fast-timing context
  - this keeps the useful protection for fast glued-text cases
  - and avoids over-freezing normal mid-speed word/letter learning
- The local probe full-suite `OutOfMemoryError` is also closed:
  - root cause was not general heap shortage
  - root cause was one heavy probe test preloading all WAV fixtures twice at class init
  - fix direction was lazy per-recording load, not blanket heap inflation

### Verification status

- targeted timing regression passed:
  - [CwTimingModelTest.java](/d:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/timing/CwTimingModelTest.java)
  - [CwAdaptiveTimingModelTest.java](/d:/Workshop/CWCN/cwcn-android/app/src/test/java/org/bi9clt/cwcn/core/timing/CwAdaptiveTimingModelTest.java)
- the previously exposed pipeline regression also returned to green:
  - `humanCompactFollowupFixtureUsesRememberedCallsignRoles`
- full unit suite is now green again:
  - `451 tests completed`

### Working interpretation

- This means the immediate `P1.1 + P1.3` round is no longer the main blocker.
- The timing branch is not "finished forever", but it is now in a stable enough state that the next highest-value move should shift back toward product surface.
- In practical terms:
  - keep timing work narrow and evidence-driven from here
  - do not reopen broad `Normalize` cleanup as the default next theme
  - start spending effort on a formal RX operating path

## 2026-05-01 P2 Starting Point Snapshot

### Current UI reality

- [OperateActivity.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/ui/operate/OperateActivity.java) is still mostly an operating-status shell.
- It shows:
  - active draft summary
  - rig-path summary
  - next-action text
- It does not yet expose a real everyday RX surface for:
  - live raw text
  - live normalized text
  - tone-lock readiness
  - draft correction from the normal operating path

### Current data-boundary reality

- `Operate` can already read the persisted draft path through:
  - [LocalLogRepository.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/log/LocalLogRepository.java)
  - [QsoDraftSnapshot.java](/d:/Workshop/CWCN/cwcn-android/app/src/main/java/org/bi9clt/cwcn/core/qso/QsoDraftSnapshot.java)
- That means it already has access to:
  - `phase`
  - callsign/report/name/QTH candidates
  - `normalizedText`
  - hints / ready-for-confirmation / manual-review state
- But there is still no small shared runtime RX session snapshot that a normal operating screen can subscribe to for:
  - live `RAW` text
  - live decoder/interpreter preview before draft save
  - live tone-lock / readiness summary outside Debug

Implication:

- the first real `P2` enabler is not only layout work
- it is adding a lightweight shared RX-session snapshot boundary so `Operate` does not need to embed all of `InputDebugActivity`

### Why this now matters more than more RX core cleanup

- The RX stack is already deep enough that product value is now bottlenecked by presentation, not only by core decode internals.
- `InputDebugActivity` remains the place where the real RX power is visible today.
- That is useful for engineering, but it is still the wrong entry point for normal operation.

### Recommended next execution order

1. Turn `OperateActivity` into the first minimal RX operating page instead of a status-only shell.
2. Reuse the existing RX pipeline and draft/log stores rather than building a second UI-only decode path.
3. Promote only the user-facing essentials from debug:
   - live raw text
   - normalized text
   - draft snapshot
   - tone-lock / readiness summary
4. Keep fixture replay, timing probes, and deep diagnostics inside developer mode.

### MVP boundary for the first RX page

- Good first slice:
  - input/rig readiness header
  - live RX text area
  - normalized/draft review area
  - lightweight action row
- Not first-slice work:
  - full debug charts
  - all fixture tooling
  - broad `Normalize` refactor
  - another large timing-model branch

#### 2026-05-01 Third narrowing landed

- damaged control residue no longer needs to be silently rewritten into visible `BK / KN / K`.
- Current behavior after this pass:
  - damaged visible control residue such as `B`, `EB`, `?` may remain visible in `normalizedText`
  - when context still strongly looks like report handoff / over, these residues can continue to classify as `CONTROL`
    so phrase hints like `Turn handoff / over` remain available
  - QSO phase derivation now also trusts the interpreter hint path for turn handoff,
    instead of requiring visible `BK / KN / K` text in every case
- Intended boundary after this pass:
  - visible text preserves observed residue
  - semantic / hint / QSO layers may still infer handoff meaning from context
- Follow-up alignment to keep:
  - fixture `expectedNormalizedText` for damaged human residue cases should now preserve visible residue too
  - evaluator `recovery pressure` should continue to mean visible token rewrite pressure, not semantic inference pressure
- Current cleanup batch:
  - a first fixture-library sweep has started moving `5NN`-based visible baselines away from legacy visible `599`
  - focused pipeline regressions around damaged residue, split short tokens, compact report tails, remembered uncertain closings,
    and compact/fully-glued ack-closing chains were narrowed so they validate normalized/QSO intent instead of over-demanding raw prettiness
- Verified with narrow regression:
  - `CwInterpreterCallsignRecoveryTest`
  - `CwInterpreterRawCopyFocusTest`
  - `CwConversationSemanticsTest`
