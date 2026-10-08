# Amazon Invite Helper v1.2

Android accessibility helper for checking a user-maintained queue of Amazon product pages.

## v1.2 changes
- Detects **Thanks for shopping with us** / purchased-state wording and records **Already purchased** instead of Available to buy.
- Availability checks only use strong, visible invitation wording.
- Prevents duplicate advance callbacks which could skip alternate queue items.
- Adds a short page-settle delay before scanning a newly opened product.
- Fresh Start runs begin from item 1; Resume continues the current item.
- Automatically returns to Amazon Invite Helper when the queue completes or is stopped.
- **Stop when an item is available to buy** defaults to OFF.
- Generic Amazon share labels such as **Share Item** are ignored; the product title is captured from the product page where possible.

The helper never presses Add to Basket, Buy Now or checkout controls.
