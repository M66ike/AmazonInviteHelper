# Amazon Invite Helper v1.3

Android accessibility helper for checking a user-maintained queue of Amazon product pages.

## v1.3 changes
- Detects **Thanks for shopping with us** / purchased-state wording and records **Already purchased** instead of Available to buy.
- Availability checks only use strong, visible invitation wording.
- Prevents duplicate advance callbacks which could skip alternate queue items.
- Adds a short page-settle delay before scanning a newly opened product.
- Fresh Start runs begin from item 1; Resume continues the current item.
- Automatically returns to Amazon Invite Helper when the queue completes or is stopped.
- **Stop when an item is available to buy** defaults to OFF.
- Generic Amazon share labels such as **Share Item** are ignored; the product title is captured from the product page where possible.

The helper never presses Add to Basket, Buy Now or checkout controls.


## v1.3 automatic collection
When **Automatically add product pages I open in Amazon** is enabled, the accessibility service waits for a real Amazon product-detail page. It does not collect search-result cards that you merely scroll past. When a product page is opened it briefly invokes Amazon's Share action, selects **Add to Invite Helper**, captures the product's shared Amazon URL and title, and returns to Amazon. The queue de-duplicates shared URLs. Automatic collection is suspended while a queue scan is running.
