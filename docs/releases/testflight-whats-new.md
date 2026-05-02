Summary

- Fixed the conversation composer hiding Send behind Cancel while a turn is active.
- Added Copy to the iOS user-message long-press menu.
- Updated pet sprite animation timing so idle pets breathe slower and action states play a short burst before returning to idle.

What to test

- Active-turn composer: start a conversation turn, type into the composer while the turn is still active, and confirm the outer action changes to Send instead of staying as Cancel. Clear the text and confirm Cancel returns.
- Message actions: long-press a user message in the iOS conversation timeline. The menu should include Copy, Edit Message, and Fork From Here, and Copy should place the message text on the clipboard.
- Pet animation pacing: enable a pet and watch the idle loop. It should animate at a slower, less frantic pace. Trigger action states such as active turn, waiting, review/input-needed, failed, and dragging; each action should play briefly, then return to the idle loop.
