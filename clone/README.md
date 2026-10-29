# Duplicate archive

This folder stores duplicate or historical copies of the site so the root of the repository stays as the single source of truth.

Active website files:
- root `index.html`
- root `pay.html`
- root `maintenance.html`
- root `server.js`

Duplicate/historical copies kept here:
- `clone/giva/` -> duplicate of the older site copy
- `clone/givastore/` -> duplicate of the older site copy
- `clone/givastore-admin-3/` -> admin app duplicate
- `clone/Backup/` -> archived backup materials

Rule:
- Do not deploy from this folder unless intentionally testing a duplicate build.
- Production flow should use the files in the repository root.
