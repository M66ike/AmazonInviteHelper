# Amazon Invite Helper v1.4.2

This build keeps the proven **v1.4 single-account flow**. Automatic account switching remains removed.

## Changes from v1.4.1
- High-confidence status phrases are now checked across the **whole current Amazon accessibility tree**, not rejected because a WebView node reports incorrect screen bounds.
- Adds an Android direct-text-search fallback for key states such as **“Invitation requested”**, **“Thanks for shopping with us”**, purchase-limit wording, and invitation-ready wording.
- Builds a normalized whole-page text string as another fallback, allowing a status sentence split across multiple accessibility nodes to be recognised.
- Clickable controls such as **Request invite**, Add to Basket/Buy Now detection, and seller controls still require a genuinely visible node before the helper acts on them.
- The overlay can show a short diagnostic such as `text: Invitation requested` while scanning.
- Retains separate **REQUESTED NOW** and **ALREADY REQUESTED**, **PURCHASED BEFORE**, smaller scroll steps, names + links Copy List, Quick Add and other-seller handling.

The helper never presses Add to Basket, Buy Now, checkout, or payment controls.
