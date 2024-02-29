# Cancellation

This example shows how long running effects can be made cancellable,
either by triggering the same effect again before it has completed,
or explicitly by, say, clicking a button. We illustrate this with a simple
HTTP GET request to [The Bored API](https://www.boredapi.com/).

## State

The state holds the most recently fetched activity and the loading state,
which indicates whether a request is currently running.

```scala mdoc:js:shared
case class State(
    activity: Option[Activity] = None,
    loading: Boolean = false
)
```

The JSON API response is modeled using a case class with a circe decoder instance.

```scala mdoc:js:shared
import io.circe.*
import io.circe.generic.semiauto.*

case class Activity(activity: String)

object Activity:
  given Decoder[Activity] = deriveDecoder
```

## Actions

```scala mdoc:js:shared
enum Action:
  case Cancel
  case GetRandomActivity
  case SetActivity(activity: Option[Activity])
  case SetLoading(loading: Boolean)
```

## Store

The interesting bit is the construction of the store.
By wrapping the data fetching effect with `store.withCancellationKey`, we can cancel it using `store.cancel`.
By wrapping it with `store.withRunningState`, we can observe whether it is running using `store.runningState`.
Finally, we keep the `loading` state in sync by subscribing to changes of `store.runningState`.

```scala mdoc:js:shared
import cats.effect.*
import cats.effect.implicits.*
import cats.syntax.all.*
import scala.concurrent.duration.*

object Store:

  private val cancelKey = "activity"
  private val loadingKey = "loading"

  def apply[F[_]](using
      F: Async[F]
  ): Resource[F, ff4s.Store[F, State, Action]] =
    for
      store <- ff4s.Store[F, State, Action](State()): store =>
        case (Action.SetActivity(activity), state) =>
          state.copy(activity = activity) -> F.unit
        case (Action.SetLoading(loading), state) =>
          state.copy(loading = loading) -> F.unit
        case (Action.Cancel, state) => state -> store.cancel(cancelKey)
        case (Action.GetRandomActivity, state) =>
          (
            state.copy(activity = none),
            store
              .withCancellationKey(cancelKey)(
                store.withRunningState(loadingKey)(
                  F.sleep(
                    1.second // pretend that this is really long running
                  ) *>
                    ff4s
                      .HttpClient[F]
                      .get[Activity]("https://www.boredapi.com/api/activity")
                      .flatMap(activity =>
                        store.dispatch(Action.SetActivity(activity.some))
                      )
                )
              )
          )

      _ <- store
        .runningState(loadingKey)
        .discrete
        .evalMap(loading => store.dispatch(Action.SetLoading(loading)))
        .compile
        .drain
        .background
    yield store
```

## View

```scala mdoc:js:shared
trait View:
  self: ff4s.Dsl[State, Action] =>

  val view =

    import html._

    useState: state =>
      div(
        button(
          "Get activity",
          onClick := (_ => Action.GetRandomActivity.some)
        ),
        button("Cancel", onClick := (_ => Action.Cancel.some)),
        if (state.loading) div("loading...")
        else div(s"${state.activity.map(_.activity).getOrElse("")}")
      )
```


```scala mdoc:js:invisible
class App[F[_]](using F: Async[F]) extends ff4s.App[F, State, Action] with View:
  override val store = Store[F]
  override val rootElementId = node.getAttribute("id")

new ff4s.IOEntryPoint(new App, false).main(Array())
```
