# Threads, refreshes, and clocks

The game reads its state on one thread, the game thread (Minecraft calls it "Render thread").
Web requests, the backend socket, and timers finish on other threads.
These rules keep the two apart, so a late answer can't change what a frame is drawing,
or land after the account it was for has signed out.
They live in `com.seqwawa.seq.client`.

## Hand results to the game thread

State the game reads changes only on the game thread.
Background work hands its result back with `ClientThread.execute(task)`,
or with `ClientThread.executor()` where an `Executor` is wanted (`whenCompleteAsync` and the like).
The queue runs at the start of the next client tick, at most 50 ms later.

Don't use `mc.execute` for this:

- Called on the game thread, it runs the task at once, so a callback can re-enter the code that started it.
- Minecraft drops its queued tasks when you leave a server. A lost hand-back leaves a "loading" flag stuck for good.

For a delay, use `ClientThread.after(delay, task)`, not `CompletableFuture.delayedExecutor`,
whose tasks run on a JDK timer thread.

Development builds log, once per place, code that should run on the game thread but doesn't.
Run a release build with `-Dseq.threadChecks=true` for the same.

## Fetch and keep a value: `Refreshable<T>`

Anything the mod fetches, keeps, and fetches again uses `Refreshable<T>`, instead of its own
`loading` flag, last-attempt time, and generation counter. It covers:

- **Throttling.** `request()` fetches unless a fetch is running or the last began too recently:
  the refresh interval after a success, the retry interval after a failure.
  The first request always fetches.
- **Asking again.** `markStale()` makes the next request fetch at once; called while a fetch runs,
  it applies after it, since that fetch's answer may predate the change.
  `force()` fetches now unless a fetch is running, and changes nothing when refused.
- **Failure.** The last good value stays, and `state().failure()` says the refresh failed.
  `clearOnFailure()` drops the value instead, where an old answer is worse than none.
- **Reset.** `reset()` forgets the value and drops the running fetch's answer when it arrives.
  The fetch's `Ticket` stops being current, so a fetch that checks it won't sign in for a session that ended.
- **Deadline.** `deadlineMs` fails a fetch that runs too long, on the next request.
- **Cached values.** `seed(value)` shows a value kept from an earlier run until a fetch replaces it.
  It never overwrites a fresher one.
- **Waiting.** `settled()` completes once no fetch is running, for a command that reports how it went.
- **Local changes.** `mutate(change)` changes the value now, for a change the server confirmed outside a fetch:
  a pushed profile, a save. A fetch running then may have been answered before it, so the change is made again
  to that fetch's answer, and an older answer can't undo it.
- **A lost connection.** `cancel()` drops the running fetch's answer and keeps the value.
- **Some failures clear.** `clearOnFailure(when)` drops the value only for the failures `when` accepts,
  such as "signed out" or "not a member", and keeps it for the rest.
- **Buttons.** `isDue()` says whether a request would fetch now, for a refresh button.

Not everything fits. Princess raid stats order a refresh and a raid record by when each was asked,
so they keep their own logic, and only hand the backend's answers to the game thread.

Readers on any thread see one consistent `State`: the value, whether there is one, a version that
changes with it, whether a fetch is running, and the last failure.
Everything else belongs to the game thread.

## Sessions

What one sign-in of one account reads and shows lives in that account's `Session`
(`com.seqwawa.seq.session`): Leaf quests, guild raid progress, Princess stats, the raid-profile
sign-in, the Leaf event stream and its popups, the war planner and queue, the party finder, and
the backend socket the connection opened while it was current.
`SessionManager` always has a current session, a signed-out one while signed out, and ends it in
one way: `end(reason)`, for an account switch, /seq logout, Clear session or a rejected token.

- **Ending stops what it owned.** An answer that arrives later lands in an object nobody reads,
  and the ended session's token refuses without signing in. Its socket closes, and what that
  socket still delivers is dropped, except a Treasury OUT reply, which settles the request it names. A step that throws is logged and the
  rest still run, so the next session always starts.
- **Ask your own session.** Work that belongs to a session takes its token from `Session.token`,
  or sends with `ApiClient.forSession`, not the auth service, and hands results to its own session's objects. `getInstance()` answers
  with whichever session is current by the time it is called. A screen that acts for one account,
  such as the War Planner or raid-profile setup, closes once the session it opened in has ended.
- **A rejected token** ends only the sign-in that still holds it. A refusal that lands after
  its sign-in already ended, such as an old socket's after an account switch, ends nothing.

`end(reason)` runs on the game thread, the connection's rejections included.

## The backend connection

`ConnectionManager` keeps where the connection is in one state (idle, signing in, connecting,
open, closing, waiting), changed only by its `step(event)`, on the game thread. The player's
Connect and Disconnect, the sign-in's answer, each socket event, the timer and the end of the
socket's session are its events. Each try at connecting has a socket of its own; an event from a
socket that was replaced, or that this client closed, changes nothing. Every close is handled in
one place, which decides whether to reconnect, and runs a Connect the player asked for meanwhile;
a Connect held for a close or the sign-in backoff belongs to the session it was asked in, and that
session's end drops it. A socket still connecting when its session ends is cut short, and the next
waits for its close. One already closing when its session ends closes for no one: what the backend
said to it applies to no account. The exception is the end its own refused token caused, after which
it belongs to the next session and signs in afresh.
Other code hears what the backend sends through `ConnectionManager.listen(type, listener)`,
on the game thread.

## Clocks

- Code that throttles, waits, or gives up after a while takes a `LongSupplier` of milliseconds:
  `Clocks::monotonicMillis` in the game. It never jumps when the system clock is set.
  It starts near zero, so don't use zero to mean "never".
- Code that shows or sends a moment takes a `java.time.Clock`: `Clock.systemUTC()` in the game.
- Both come in through the constructor, so a test can move time. Neither reads the system clock itself.

## Disk

Reading, writing, and parsing the mod's files runs on `Background.disk()`, one thread, so two writes
to one file land in order and neither the game thread nor a web request waits on the disk.

## Tests

Pass a `TickQueue` where the game passes `ClientThread.executor()`, and call `runDue()` when the
test wants the game thread to catch up. Nothing it is handed runs before then, which is how the
game behaves too.
