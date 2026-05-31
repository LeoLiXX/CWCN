# Testing Workbench

This page is the single entry for current CWCN testing and bench-validation documents.

## Current test tracks

### USB / Keyer TX

- Checklist: [TxUsbBenchChecklist.md](/D:/Workshop/CWCN/TxUsbBenchChecklist.md)
- Results: [TxUsbBenchResults.md](/D:/Workshop/CWCN/TxUsbBenchResults.md)
- Use when testing:
- `USB Serial Keyer`
- `Mock USB Serial Keyer`
- `RTS / DTR` key-line behavior

### Yaesu FT-family via rigctld

- Checklist: [YaesuRigctldBenchChecklist.md](/D:/Workshop/CWCN/YaesuRigctldBenchChecklist.md)
- Results: [YaesuRigctldBenchResults.md](/D:/Workshop/CWCN/YaesuRigctldBenchResults.md)
- Use when testing:
- `FT-710`
- `FT-891`
- `FT-991A`
- nearby Yaesu FT rigs exposed through `Hamlib rigctld`

### Yaesu native serial CAT

- Checklist: [YaesuSerialCatBenchChecklist.md](/D:/Workshop/CWCN/YaesuSerialCatBenchChecklist.md)
- Results: [YaesuSerialCatBenchResults.md](/D:/Workshop/CWCN/YaesuSerialCatBenchResults.md)
- Use when testing:
- first native `Yaesu-style CAT` probe path
- USB CDC/ACM serial permission / link / probe behavior
- `FA;` / `IF;` read-only CAT response checks

### Icom native CI-V

- Checklist: [IcomCivBenchChecklist.md](/D:/Workshop/CWCN/IcomCivBenchChecklist.md)
- Results: [IcomCivBenchResults.md](/D:/Workshop/CWCN/IcomCivBenchResults.md)
- Use when testing:
- first native `Icom CI-V` probe path
- USB CDC/ACM serial permission / link / probe behavior
- `CI-V address` validation and read-only CI-V response checks

### Kenwood native serial CAT

- Checklist: [KenwoodSerialCatBenchChecklist.md](/D:/Workshop/CWCN/KenwoodSerialCatBenchChecklist.md)
- Results: [KenwoodSerialCatBenchResults.md](/D:/Workshop/CWCN/KenwoodSerialCatBenchResults.md)
- Use when testing:
- first native `Kenwood-style CAT` probe path
- USB CDC/ACM serial permission / link / probe behavior
- safe ASCII `ID;` / `FA;` / `IF;` response checks

## Supporting context

- Project status / checkpoint:
- [CurrentProgress.md](/D:/Workshop/CWCN/CurrentProgress.md)
- [ContextCheckpoint.md](/D:/Workshop/CWCN/ContextCheckpoint.md)
- RX bench pending limitations:
- [RxBenchKnownLimitations.md](/D:/Workshop/CWCN/RxBenchKnownLimitations.md)
- Rig integration direction:
- [RigIntegrationUiPlan.md](/D:/Workshop/CWCN/RigIntegrationUiPlan.md)
- Overall engineering plan:
- [CodingPlan.md](/D:/Workshop/CWCN/CodingPlan.md)

## Recommended reading order

1. Start from the checklist for the route you are actually testing.
2. Paste results into the paired results file immediately after each meaningful run.
3. If the route is unclear, read:
- [RigIntegrationUiPlan.md](/D:/Workshop/CWCN/RigIntegrationUiPlan.md)
- [CurrentProgress.md](/D:/Workshop/CWCN/CurrentProgress.md)

## Next families likely to join this page

- `Icom` via `rigctld`
- `Kenwood` via `rigctld`
- later:
- direct serial CAT family benches
- Bluetooth serial bench notes
