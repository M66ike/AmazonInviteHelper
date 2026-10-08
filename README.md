# Amazon Invite Helper v1.4

Android accessibility helper for maintaining and checking a queue of Amazon invitation products.

## v1.4 changes
- **Quick Add from search results:** long-press an Amazon product to open its preview; the helper automatically follows **Share → More → Add to Invite Helper**.
- Captures the product title before sharing, saves the Amazon shared link, de-duplicates by title/link, then closes the share panel and returns to the results.
- Merely scrolling past search results does not add anything.
- Queue scanning recognises normal **Add to basket / Buy Now** pages sold by a third-party seller and records **Other seller / normal sale** instead of hanging on the item.
- Seller name is recorded when Amazon exposes it to Accessibility.
- Existing invitation states remain: Requested, Available to Buy, Already Purchased, No invitation control, and errors.
- **Stop when available** remains OFF by default.
- Uses the supplied ninja/Amazon artwork as the Android launcher icon.

The helper never presses Add to Basket, Buy Now, checkout, or payment controls.
