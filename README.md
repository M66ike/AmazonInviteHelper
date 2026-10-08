# Amazon Invite Helper v1.4.1

This build deliberately returns to the proven **v1.4 single-account flow**. The v1.5 automatic account-switching code has been removed.

## Changes from v1.4
- Adds separate **REQUESTED NOW** and **ALREADY REQUESTED** results.
- Keeps/strengthens **PURCHASED BEFORE** detection, including Amazon's **“Thanks for shopping with us”** and **“limit purchases to one per customer”** wording.
- Status scanning no longer relies on full-page accessibility jumps. It uses small controlled scroll steps and checks each viewport twice before moving on.
- Strong status text is accepted when it is just outside the visible viewport to handle Amazon accessibility bounds lagging behind the visual page.
- Expands recognised invitation-request wording.
- **Copy list (names + links)** exports `Product name | URL` when the name is known; that format can already be pasted back into **Add products**.
- Retains v1.4 Quick Add, other-seller handling, and the rule that the helper never presses Add to Basket or Buy Now.

The helper never presses Add to Basket, Buy Now, checkout, or payment controls.
