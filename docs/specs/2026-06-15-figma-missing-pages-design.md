# Figma — Generate Missing Pages + De-duplicate (Design Spec)

> Companion to `2026-06-15-horserace-gap-analysis-roadmap-design.md` (§3 "Missing Figma Pages").
> Scope of this spec: add the 8 missing Figma pages and pick a canonical for duplicate frames.
> **Approved by user 2026-06-15.** Figma file: `PKuWrDKNcURIsVth3BwRuj`.

## Decisions (agreed)
- **Scope:** generate all 8 missing pages **+** de-duplicate the redundant frames.
- **Placement:** new Figma page **"New Pages v2"** (Page 1 already has 42 frames).
- **De-dup:** pick a **canonical** per duplicate group; **do NOT delete** the originals (non-destructive — user removes manually after review).
- **Fidelity:** hi-fi, matching the **Equine Elite** design system. Desktop 1280px (mobile out of scope, consistent with existing frames).
- **Review flow:** generate **one page at a time**, screenshot it, **wait for user approval before the next** ("hỏi ý kiến trước khi sửa").

## Conventions (every page)
- In-app pages use **Sidebar (Aside) + TopNavBar** (like `DashboardLayout`); the public Tournament page uses the HomePage top nav.
- Reuse Equine Elite components: cards/KPI, tables with search/filter, forms, status badges, primary/secondary buttons — matched from the template frame.
- Figma file uses hardcoded styles (no Figma variables); generation reads style from the referenced template frame.

## The 8 new pages

| # | Page | Role | Template frame | Key sections |
|---|---|---|---|---|
| 1 | Account / Profile Settings | all | Dashboard + form | profile + edit; change email (OTP); change password; notification prefs; security/sessions |
| 2 | Spectator Wallet | Spectator | Finances-Owner (106:521) | balance + wallet; deposit (mock gateway); withdraw; transaction ledger; payout receipts |
| 3 | Notifications Center | all | Dashboard + list | read/unread list; filter by type (invite/reward/result/approval); mark read; deep-link |
| 4 | Admin — Registration Approval | Admin | Tournament Orchestration (1:5156) | pending queue; applicant/horse detail + docs; approve/reject + reason; eligibility checklist |
| 5 | Admin — Horse List Management | Admin | User & Role Mgmt (1:5438) | master list (search/filter); horse detail; enable/disable/remove; bulk/export |
| 6 | Admin — Jockey List Management | Admin | User & Role Mgmt (1:5438) | roster (search); jockey detail (license, record); suspend/activate; link to assignments |
| 7 | Owner — Confirm Horse Participation | Horse Owner | Race Schedule (1:511) + owner dashboard | eligible upcoming races; horse + assigned jockey/race; confirm; withdraw/decline; status |
| 8 | Tournament — Public Listing & Detail | Spectator | HomePage + Spectator Hub (1:3886) | tournament list (active/upcoming/past); detail (overview, prize pool, rounds); schedule; participants; standings |

## De-duplication (pick canonical, keep originals)
For each group: screenshot both, recommend the keeper, confirm with user; if they differ materially, build one merged version on "New Pages v2". Never delete originals.
- Jockey Dashboard: `1:306` ↔ `342:2269`
- Referee Dashboard: `1:3375` ↔ `342:2567`
- User & Role Management: `1:5438` ↔ `403:2`
- Referee results/violations flow: `1:6000` / `1:6654` / `1:4852` / `1:6386` → consolidate into one workflow

## Order of generation
1 Profile Settings → 2 Wallet → 3 Notifications → 4 Registration Approval → 5 Horse List → 6 Jockey List → 7 Confirm Participation → 8 Tournament Listing → then de-dup review.

## Out of scope
Mobile variants; wiring to code (frontend implementation is a later roadmap phase); deleting any existing frame.
