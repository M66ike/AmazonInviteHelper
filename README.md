# Amazon Invite Helper v1.5

Android accessibility helper for maintaining and checking a queue of Amazon invitation products.

## v1.5 changes
- **Multi-account checking:** discover Amazon accounts from **You → Switch Accounts** and save them by email address.
- Account rows are always re-found by their **email text**. The helper never assumes the next account is the next row, so Amazon can reorder the account list after each switch.
- The account finder and switcher scroll the Switch Accounts list when required.
- Discovered accounts appear as tick boxes in the helper so you can choose which accounts are included in a run.
- A run checks the complete product queue for one selected account, then switches to the next selected account and repeats. This minimises account changes while still storing a separate result for every product/account combination.
- **REQUESTED NOW** means the helper pressed Request invite during this run and then saw Amazon's confirmation.
- **ALREADY REQUESTED** means the confirmation was already present before the helper pressed anything.
- **PURCHASED BEFORE** recognises Amazon's existing purchased wording plus the **“Thanks for shopping with us … limit purchases to one per customer”** screen.
- **Copy list (names + links)** exports `Product name | URL` when a name is known, or just the URL otherwise. The same mixed format can be pasted directly back into the Add products box.
- Keeps v1.4 Quick Add: long-press an Amazon result → Share → More → Add to Invite Helper.
- Keeps normal sale / other-seller detection and does not press any purchase controls.
- **Stop when available** remains OFF by default.

## Safety boundary
The helper can request invitations. It never presses **Add to Basket**, **Buy Now**, checkout, payment, or order-confirmation controls.
