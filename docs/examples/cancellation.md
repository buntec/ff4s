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
  implicit val decoder: Decoder[Activity] = deriveDecoder
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
By wrapping the data fetching effect with `store.withCancellationKey`, we can cancel it using `store.cancel`.
By wrapping it with `store.withRunningState`, we can observe whether it is running using `store.runningState`. 
Finally, we keep the `loading` state in sync by subscribing to changes of `store.runningState`.

```scala mdoc:js:shared
import cats.effect._
import cats.effect.implicits._
import cats.syntax.all._
import scala.concurrent.duration._

object Store {

  private val cancelKey = "activity"
  private val loadingKey = "loading"

  def apply[F[_]](implicit
      F: Async[F]
  ): Resource[F, ff4s.Store[F, State, Action]] = for {

    store <- ff4s.Store[F, State, Action](State()) { store =>
      _ match {
        case SetActivity(activity) => _.copy(activity = activity) -> none
        case SetLoading(loading)   => _.copy(loading = loading) -> none
        case Cancel                => _ -> store.cancel(cancelKey).some
        case GetRandomActivity =>
          state =>
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
                          store.dispatch(SetActivity(activity.some))
                        )
                  )
                )
                .some
            )
      }
    }

    _ <- store
      .runningState(loadingKey)
      .discrete
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
