# Design prompt — Legum Lex · Client App · Invoices (list + statement detail + pay)

> Paste into **claude.ai/design** (Lexcore project `a02d20c2-c2ca-45fe-8add-cc199ac68d33`).
> After generation, implement with the `lexcore-design-to-compose` skill loop.

---

**Context & house style (match exactly — this is an existing system, not a fresh one).**
Legum Lex client app for an Indian legal practice, built in the **Lexcore** language
already used across this project's files (Client Home, Cases / Matter Detail, Agreement
Check, Notifications). **Inherit the exact fonts, palette, spacing and components from
those existing files** — do not invent a new aesthetic. The invoice screen should read
as the same app, just a money surface.

- **Type (match the sibling Lexcore files exactly):** a **serif** for screen titles,
  section headers and **monetary figures** (Lora-style); a **humanist sans** for all
  UI / body / buttons / chips / labels (Source Sans 3-style); a **monospace** for
  figures-as-data — invoice numbers, dates, line totals, balance, transaction IDs.
- **Palette (CSS vars — these are the project's tokens):**
  `--ink:#1c1917; --ink-m:#57534e; --ink-f:#a8a29e; --paper:#faf9f7; --p1:#f5f2ee;
  --p2:#ede9e4; --b:#e7e2db; --bs:#d4cdc5; --claret:#7d1930; --claret-m:#be2e4a;
  --claret-s:#fdf2f4; --claret-border:#f4d9df; --verdant:#1a5c3a; --verdant-m:#22864a;
  --verdant-s:#f0faf4; --amber:#92400e; --amber-m:#b45309; --amber-s:#fffbeb;
  --slate:#1e3a5f; --slate-m:#34598a; --slate-s:#eff4fb; --violet:#4c1d95;
  --violet-s:#f5f3ff;`
  Soft shadow only; **14px card radius**; **1px hairline borders**.
- **Icons:** Material Symbols Outlined.
- **Inherited patterns:** list-first density; **status/severity on the leading edge**
  (3px stripe + tinted icon tile); serif section headers; mono figures; chips;
  **412px-wide phone device frame** with a 9:41 status bar; app-wide header (centered
  Legum mark, optional back chevron, right-side notifications bell w/ unread badge);
  5-tab bottom nav (Home / Cases / Chamber / **Money** / More) — Money tab active here.

**Money is the emotional core of this screen.** The single most important element is
the **Balance due** — large, in the serif or mono, impossible to miss. Everything else
(line items, taxes, history) is supporting evidence. The screen should feel like a
**dignified statement of account from a chamber**, not a SaaS billing dashboard.

## Status model (drive the leading-edge severity colour from this)
Invoice status, derived from the Perfex code + due date:
- **Overdue** → critical → **claret** stripe / `--claret-s` tile / claret tint
- **Unpaid** (due soon / not yet due) → warning → **amber-m** stripe / `--amber-s` tile
- **Partial** (part-paid, balance remaining) → info → **slate-m** stripe / `--slate-s`
- **Paid** → positive → **verdant-m** stripe / `--verdant-s` tile
- **Draft** / **Cancelled** → neutral → **ink-f** / `--p2`, muted, no CTA

---

## Deliver — ONE self-contained HTML file, device frames side by side

### A. Invoices list screen
A dense, scannable ledger of the client's invoices. Each row:
- leading severity stripe + small icon tile (per status above);
- **invoice number** (mono) + **matter/project name** (sans, muted) beneath;
- **issue date** (mono) and **due date** with a relative hint ("due in 4 days" /
  "5 days overdue" in the severity colour);
- right edge: **amount** (serif/mono) with a status chip (Unpaid / Partial / Paid /
  Overdue) — Paid shows the total struck-through-calm, balance shown for Partial.
States required, each as its own device frame:
1. **Loaded (typical)** — a mix of one Overdue, one Unpaid-due-soon, one Partial, two Paid.
2. **Empty** — no invoices yet; dignified, quiet verdant check, "Nothing outstanding."
3. **Loading skeleton** — shimmer in the real row shape.
4. **Error / offline** — inline retry block.

### B. Invoice statement (detail) screen — the centrepiece
Render as a **statement / letterhead sheet** inside the app frame. Top to bottom:
1. **Letterhead header** — the Legum firm mark, "TAX INVOICE", **invoice number**
   (mono), **issue date** and **due date** (mono, with overdue/ due-soon hint).
2. **Status pill** — large, the severity colour; for Partial show "Partially paid".
3. **Matter block** — the linked **case / project** name (tappable, opens the matter),
   plus the billed-to client line.
4. **Balance hero** — the **Balance due** as the dominant figure (e.g. `₹3,000.00`),
   with **Total** and **Amount paid** as smaller mono sub-figures beneath. When fully
   paid, the hero becomes a calm verdant "Paid in full" with the paid date.
5. **Action stack** — primary CTA **`Pay ₹3,000`** (claret, full-width) that opens the
   in-app Razorpay sheet; when the balance is less than the full total it reads
   **`Pay ₹X partially`**. Secondary ghost actions: **View in browser**, **Share
   payment link**. Hide all CTAs entirely for Paid / Draft / Cancelled.
6. **Amount summary** — Subtotal, Tax, Discount, Adjustment, **Total** (emphasised),
   Amount paid, **Balance due** — each a label (sans) + figure (mono) row, hairline
   separators, Total and Balance set apart.
7. **Line-item breakdown** — an **expandable** section ("View line items"); each item:
   description (sans, strong) + optional long description (muted), `qty × rate` (mono)
   and **amount** (mono) on the right; unit shown when present.
8. **Payments ledger** — past payments: **date** (mono), **method** (e.g. "Razorpay",
   chip), **transaction id** (mono, truncated), **amount** (mono, verdant). Empty when
   none yet.
9. **Terms & notes** — small serif/sans blocks when present.
10. **Firm footer** — chamber name / quiet reassurance line.

States required, each as its own device frame:
1. **Loaded — Unpaid/Overdue** (balance due, full pay CTA, line items collapsed).
2. **Loaded — Partial** (slate status, "Pay ₹X partially" CTA, one prior payment in ledger).
3. **Loaded — Paid in full** (verdant hero, no CTA, payments ledger populated, calm).
4. **Paying** — the Pay CTA in an in-progress state (spinner / "Opening secure
   checkout…", button disabled) while the Razorpay sheet loads.
5. **Loading skeleton** — statement-shaped shimmer (header, hero, summary rows).
6. **Error / offline** — inline retry block (whole-screen only for auth failure).

**Also include:** a small **status → colour/icon legend**; the header shown in its
back + bell configuration; the **Pay button** in its three forms (full / partial /
disabled-paying). A short **readme panel** (like the other project files) explaining
block ordering, the balance-hero decision, and the severity mapping.

**Output:** Lexcore-style HTML, Google-fonts loaded to match the sibling files
(serif / humanist-sans / monospace) + Material Symbols Outlined, every state drawn as
a 412px device frame. Faithful enough to drop into the project as **`Client Invoice.html`**.
