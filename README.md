# Amazon Invite Helper (Android)

A local-only Android accessibility helper for Amazon invitation-only product pages.

## What v1 does

- Stores a queue of Amazon UK product URLs or ASINs.
- Opens each queued item in the Amazon Shopping app.
- Scrolls the product page looking for invitation state text.
- If it sees **Request invite**, it can tap it automatically.
- If it sees **Invitation requested**, it records the item and moves on.
- If it sees **Available for you to buy**, it marks the item AVAILABLE, vibrates, and pauses by default.
- It deliberately never searches for or presses **Add to Basket**, **Buy Now**, checkout, payment, or order controls.
- Floating overlay provides progress, Pause/Resume, and Stop while Amazon is open.
- Also includes a button for the filtered Pokémon/Amazon UK search page.

## Queue input

One per line:

```text
B0XXXXXXXX
https://www.amazon.co.uk/dp/B0XXXXXXXX
Pitch Black Booster Display | https://www.amazon.co.uk/dp/B0XXXXXXXX
https://amzn.to/xxxxx
```

Short links are opened directly by Amazon; the app does not resolve them itself.

## First run

1. Install the APK.
2. Open **Amazon Invite Helper**.
3. Tap **Enable accessibility service** and enable Amazon Invite Helper.
4. Add your product links/ASINs.
5. Tap **Start / Resume**.
6. Amazon opens and the overlay shows progress.

## Important implementation note

Amazon can change its UI wording or accessibility tree. The scanner currently recognises several variants of:

- Request invite / Request invitation
- Invitation requested
- Available for you to buy / invited to purchase

If Amazon changes those strings, update them in `InviteAccessibilityService.scanTree()`.
