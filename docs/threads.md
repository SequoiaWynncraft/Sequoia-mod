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

Readers on any thread see one consistent `State`: the value, whether there is one, a version that
changes with it, whether a fetch is running, and the last failure.
Everything else belongs to the game thread.

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
