# Probe Artifact Policy

Last updated: 2026-05-20

## Scope

This policy covers local probe outputs generated while iterating on:

- RX diagnostics
- replay analysis
- continuity / bootstrap / SQL sweeps
- developer-only trace experiments

Typical examples are files written into `cwcn-android/` root such as:

- `*.log`
- `*.txt`

## Default Rule

Files generated as one-off or iterative probe outputs should not be tracked.

They are now ignored by default through:

- [`.gitignore`](/D:/Workshop/CWCN/.gitignore)

Specifically:

- `cwcn-android/*.log`
- `cwcn-android/*.txt`

## What Should Be Preserved

If a probe run produces a result that matters long-term, preserve the conclusion instead of the raw dump.

Preferred landing places are:

- [RxArchitectureResetPlan.md](/D:/Workshop/CWCN/RxArchitectureResetPlan.md)
- [OperateUxDirection.md](/D:/Workshop/CWCN/OperateUxDirection.md)
- a focused regression/probe test in `app/src/test/...`

The durable artifact should usually be:

- a conclusion
- a threshold/rule
- a regression assertion
- a compact comparison table

not a full raw console dump.

## When Raw Artifacts May Be Kept

Raw probe files may still be kept locally when they help with short-horizon iteration, for example:

- comparing two nearby RX patches
- checking whether a regression was reintroduced
- re-reading one dirty case across several trial runs

But they should remain:

- local
- disposable
- outside normal git history

## Promotion Rule

Promote a probe result into tracked history only when at least one of these is true:

- it changes an architectural conclusion
- it becomes part of a release/regression gate
- it explains a non-obvious product fallback decision
- it is needed to justify a new permanent test

In those cases, prefer promoting:

- a short markdown summary
- or a deterministic automated test

instead of committing the raw `.log/.txt` directly.

## Practical Workflow

Recommended workflow going forward:

1. Run probes freely and let outputs stay in `cwcn-android/` root.
2. If the run is only exploratory, do nothing further.
3. If the run yields a stable conclusion, write that conclusion into a tracked doc or test.
4. If a raw dump truly needs to be shared later, create a curated summary first instead of committing the whole dump by default.
