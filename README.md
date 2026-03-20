> [!WARNING]
> Beware AI slop: this was authored by GitHub Copilot CLI (Claude Opus 4.6 model).

# Mill `testCached` Flaky Sandbox-Escape Bug

## Summary

When running multiple `testCached` tasks in parallel (e.g. `__.testCached`),
the filesystem sandbox checker sporadically rejects writes from one task as
violations of another task's sandbox:

```
Writing to out/bar/test/testCached.dest/worker-2/claim not allowed during
execution of `baz.test.testCached`.
Normal `Task`s can only write to files within their `Task.dest` folder, only
`Task.Command`s can write to other arbitrary files.
```

The error is **flaky** — it depends on thread pool thread creation timing.

## Reproducing

```bash
./repro.sh      # runs clean + __.testCached in a loop (usually triggers on first try)
```

Or manually:
```bash
./mill clean && ./mill __.testCached   # repeat until failure
```

Key ingredients:
- Multiple modules running `testCached` in parallel
- Each module needs **multiple test classes** (not just multiple methods) so that
  `fork.async` is used for parallel test workers (`filteredClassCount > 1`)
- Tests should take non-trivial time (~500ms) so workers overlap across modules

## Root Cause

`ThreadPool.async()` in `ExecutionContexts.scala` does not propagate `os.checker`
when submitting work to the thread pool. It sets `os.dynamicPwdFunction` and
`SystemStreams` but omits the checker:

```scala
// ExecutionContexts.scala — async() method
val runnable = new PriorityRunnable(
  priority = priority,
  run0 = () => {
    val result = NonFatal.Try(logger.withPromptLine {
      os.dynamicPwdFunction.withValue(() => makeDest()) {  // ← sets pwd
        mill.api.SystemStreamsUtils.withStreams(logger.streams) {  // ← sets streams
          t(logger)
          // ← os.checker is NOT set here!
        }
      }
    })
    promise.complete(result)
  }
)
executor.execute(runnable)  // bypasses ThreadPool.execute() which DOES propagate checker
```

Compare with `ThreadPool.execute()` which correctly captures and propagates the checker:

```scala
def execute(runnable: Runnable): Unit = {
  val submitterChecker = os.checker.value  // ← captures checker
  executor.execute(new PriorityRunnable(
    0,
    () =>
      os.checker.withValue(submitterChecker) {  // ← propagates to child
        // ...
      }
  ))
}
```

### Why InheritableThreadLocal Makes This Flaky

Scala 3's `DynamicVariable` uses `InheritableThreadLocal` (not `ThreadLocal`).
This means:

1. Thread pool threads inherit `os.checker` from whichever task was active on
   the parent thread **at thread creation time**
2. `InheritableThreadLocal` copies happen only once — at `Thread` construction
3. After a `withValue` block, the value reverts to the inherited one
4. If thread T was created while task A's `ExecutionChecker` was active, T
   permanently inherits A's checker as its "default"
5. When T later runs an `async` runnable for task B (which doesn't set
   `os.checker`), writes to B's `Task.dest` are checked by A's checker → failure

The flakiness comes from `ThreadPoolExecutor` lazily creating threads: whether
a thread inherits checker A vs `Checker.Nop` depends on exact timing.

## Workaround

Use `testForked` instead of `testCached`. `testForked` is a `Task.Command`,
and `ExecutionChecker` skips all checks for commands:

```scala
// GroupExecution.scala
if (!isCommand && ...) { /* check */ }
```

## Suggested Fix

In `ThreadPool.async()`, capture and propagate `os.checker` like `execute()` does:

```scala
def async[T](dest: Path, key: String, message: String, priority: Int)(t: Logger => T)(using
    ctx: mill.api.TaskCtx
): Future[T] = {
  // ...
  val submitterChecker = os.checker.value  // ← add this
  val runnable = new PriorityRunnable(
    priority = priority,
    run0 = () => {
      val result = NonFatal.Try(logger.withPromptLine {
        os.checker.withValue(submitterChecker) {  // ← add this wrapper
          os.dynamicPwdFunction.withValue(() => makeDest()) {
            mill.api.SystemStreamsUtils.withStreams(logger.streams) {
              t(logger)
            }
          }
        }
      })
      promise.complete(result)
    }
  )
  executor.execute(runnable)
  promise.future
}
```

## Environment

- Mill 1.1.3
- Scala 3.x (uses `InheritableThreadLocal` in `DynamicVariable`)
- JDK 25
- Linux
