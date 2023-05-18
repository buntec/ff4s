# WebSockets

Managing the life cycle of a WebSocket connection can be tricky.
In ff4s we benefit from abstractions in Cats Effect and fs2
to achieve this with ease and safety.

In this example we are connecting to a simple WS server that
echos back any message we send to it.
We only show the common case where the lifetime of
the connection coincides with the lifetime of the app.

## State

The state holds the user's input and the most recent server response.

```scala mdoc:js:shared
final case class State(
    userInput: Option[String] = None,
    serverResponse: Option[String] = None
)
```

## Action

The action encoding is straightforward:

```scala mdoc:js:shared
sealed trait Action
case class SetUserInput(input: Option[String]) extends Action
case object Send extends Action
case class SetServerResponse(response: String) extends Action
```

## Store

We use a Cats Effect `Queue` to hold outgoing messages.
The connection itself runs on a separate fiber safely
tied to the lifetime of the store using `.background`.
The `ff4s.WebsocketClient` is a wrapper around the more
powerful `http4s` client and intended for simple use-cases
such as this one.

```scala mdoc:js:shared
import cats.effect._
import cats.effect.implicits._
import cats.effect.std._
import cats.syntax.all._
import fs2.Stream

object Store {

  def apply[F[_]](implicit F: Async[F]) = for {
    sendQ <- Queue.unbounded[F, String].toResource

    store <- ff4s.Store[F, State, Action](State()) { _ =>
      _ match {
        case SetUserInput(input) => _.copy(userInput = input) -> none
        case Send => state => state -> state.userInput.map(sendQ.offer)
        case SetServerResponse(res) => _.copy(serverResponse = res.some) -> none
      }
    }

    _ <- ff4s
      .WebSocketClient[F]
      .bidirectionalText(
        "wss://ws.postman-echo.com/raw/",
        _.evalMap(res => store.dispatch(SetServerResponse(res))),
        Stream.fromQueueUnterminated(sendQ)
      )
      .background

  } yield store

}
```

## View

There isn't much to say about the view.

```scala mdoc:js:shared
import org.scalajs.dom

object View {

  def apply[F[_]](implicit dsl: ff4s.Dsl[F, State, Action]) = {
    import dsl._
    import dsl.html._

    useState { state =>
      div(
        input(
          tpe := "text",
          placeholder := "your message here...",
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                if (el.value.nonEmpty) Some(SetUserInput(el.value.some))
                else Some(SetUserInput(None))
              case _ => None
            }
          )
        ),
        button(
          "Send",
          disabled := state.userInput.isEmpty,
          onClick := (_ => Send.some)
        ),
        state.serverResponse.fold(empty)(res => div(s"Server response: $res"))
      )
    }
  }

}
```

## App

The boilerplate construction of `ff4s.App` and `ff4s.IOEntryPoint` is omitted.

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
