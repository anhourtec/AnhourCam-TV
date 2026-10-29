# 2026-10-29: Phase 4 grid editor

- `:layouts`: 11 grid templates on a 60×60 grid, camera per tile from any NVR, swap/clear/rename;
  growing a grid fills new tiles with the next unused cameras, shrinking keeps choices.
- Saved on the TV (`layouts.json`), reopens the last layout. First layout: first 4 cameras, 2×2.
- Live view of saved layouts across both NVRs (`GatewayPool`); tiles reconcile, so changing one
  camera restarts one stream and moves restart none; tiles beyond the decoders wait their turn.
- On-TV editor with custom dialogs (icons, NVR-grouped camera picker, visual grid-size picker),
  scaled tile labels, OK = fullscreen/retry/choose, hold OK = menu, Back = leave fullscreen.
- Phone editor: TV corner card with address + code; page with grid previews, numbered preview,
  "NVR · channel" per tile, rename, save → TV updates at once. Maintainer used it from a phone.
- Explained the decoder limit (this TV: 4 live tiles).
- Still images for tiles beyond the live decoders (key frame → software decode → cached bitmap),
  shown with a "Still image" badge and behind the loader while connecting. Verified on 3×3, then
  the maintainer's 2×2 restored.
- Named layouts on TV (dialog, ◀ ▶ at the edge) and phone (pills, new/default/delete); a test
  caught that deleting the default left none (fixed). Cut-off tile messages fixed (onMeasure).
