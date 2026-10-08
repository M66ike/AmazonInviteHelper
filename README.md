# Amazon Invite Helper v1.1

Android helper for Amazon UK invitation-only product pages.

## What it does
- Opens queued Amazon product links in the Amazon Shopping app.
- Scrolls the product page and looks for invitation state text.
- Taps **Request invite** when enabled.
- Recognises **Invitation requested, thanks!** and moves on.
- Recognises strong account-specific **available for you to buy/purchase** wording and can pause on that product.
- Never taps Add to Basket, Buy Now or checkout controls.
- Shows product names in the queue instead of raw URLs.
- Adds items while browsing through **Amazon → Share → Add to Invite Helper** and returns you to Amazon.
- If Amazon does not provide a title in the share data, the helper attempts to capture the product title when the product is first opened.

## v1.1 fixes
- Fixed false AVAILABLE result caused by the sentence `If invited to purchase...` on already-requested products.
- Explicit **Invitation requested** now takes priority over availability checks.
- Availability detection now uses only stronger account-specific wording.
- Added quiet Share-sheet receiver for adding items while browsing.
- Added automatic product-name capture.

## Accessibility
The app uses Android Accessibility to read the visible Amazon Shopping screen, scroll, and press the Request invite control. Android may require **Allow restricted settings** for a sideloaded APK before the Accessibility service can be enabled.
