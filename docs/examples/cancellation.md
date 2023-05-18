# Cancellation

This example shows how long running effects can be made cancellable,
either by triggering the same effect again before it has completed,
or explicitly by, say, clicking a button. We illustrate this with a simple
HTTP GET request to [The Bored API](https://www.boredapi.com/).

## State

The state holds the most recently fetched activity and the loading state,
which indicates whether a request is currently running.

```scala mdoc:js:shared
final case class State(
    activity: Option[Activity] = None,
    loading: Boolean = false
)
```

The JSON API response is modeled using a case class with a circe decoder instance.

```scala mdoc:js:shared
import io.circe._
import io.circe.generic.semiauto._

case class Activity(activity: String)
object Activity {
  implicit val codec: Decoder[Activity] = deriveDecoder
}
```

## Actions

```scala mdoc:js:shared
sealed trait Action
case object GetRandomActivity extends Action
case class SetActivity(activity: Option[Activity]) extends Action
case class SetLoading(loading: Boolean) extends Action
case object Cancel extends Action
```

## Store

The interesting bit is the contruction of the store.
We use a `Supervisor` to fork safely the long running effect onto a new fiber.
That fiber is held in a `Ref` where we can retrieve it for cancellation at any time.
Another interesting detail is how we maintain the loading state.
To this end, we have an additional `SignallingRef` counting the number of running
effects. We increment the counter before every data fetch and
decrement it regardless of outcome by wrapping the effect in a `Resource#surround`.
We then subscribe to changes to this signal and set the loading state
to `true` whenever the counter is greater than zero, and to `false`
otherwise.

```scala mdoc:js:shared
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import cats.effect.std.Supervisor
import scala.concurrent.duration._
import fs2.concurrent.SignallingRef

object Store {

  def apply[F[_]](implicit
      F: Async[F]
  ): Resource[F, ff4s.Store[F, State, Action]] = for {
    supervisor <- Supervisor[F]

    // Since there is at most one running effect a single `Ref` suffices.
    // In case of multiple cancellable effects running at the same time,
    // we could use a `MapRef` and index the fibers by a cancellation token.
    fiberRef <- F.ref[Option[Fiber[F, Throwable, Unit]]](None).toResource

    // the number of currently running effects
    runningCount <- SignallingRef(0).toResource

    // an auxiliary resource for incrementing the counter while an effect is running
    incRunning = Resource.make(runningCount.update(_ + 1))(_ =>
      runningCount.update(_ - 1)
    )

    store <- ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case SetActivity(activity) => _.copy(activity = activity) -> none
        case SetLoading(loading)   => _.copy(loading = loading) -> none
        case Cancel => (_, fiberRef.get.flatMap(_.foldMapM(_.cancel)).some)
        case GetRandomActivity =>
          state =>
            (
              state.copy(activity = none),
              supervisor
                .supervise(
                  incRunning.surround(
                    F.sleep(
                      1.second // pretend that this is really long running
                    ) *>
                      ff4s
                        .HttpClient[F]
                        .get[Activity]("https://www.boredapi.com/api/activity")
                        .flatMap(activity =>
                          store.dispatch(SetActivity(activity.some))
                        )
                  )
                )
                .flatMap(fiber =>
                  fiberRef
                    .getAndSet(fiber.some)
                    .flatMap(
                      _.foldMapM(_.cancel)
                    ) // cancel running request, if any, and store fiber of new request
                )
                .some
            )
      }
    }

    _ <- runningCount.discrete
      .map(_ > 0)
      .changes
      .evalMap(loading => store.dispatch(SetLoading(loading)))
      .compile
      .drain
      .background

  } yield store

}
```

## View

```scala mdoc:js:shared
object View {

  def apply[F[_]](implicit dsl: ff4s.Dsl[F, State, Action]) = {
    import dsl._
    import dsl.html._

    useState { state =>
      div(
        button(
          "Get activity",
          onClick := (_ => GetRandomActivity.some)
        ),
        button("Cancel", onClick := (_ => Cancel.some)),
        if (state.loading) div("loading...")
        else div(s"${state.activity.map(_.activity).getOrElse("")}")
      )
    }
  }

}
```

## App

The boilerplate for `ff4s.App` and `ff4s.IOEntryPoint` is omitted.

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
