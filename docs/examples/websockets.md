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
case class State(
    userInput: Option[String] = None,
    serverResponse: Option[String] = None
)
```

## Action

```scala mdoc:js:shared
enum Action:
  case Send
  case SetServerResponse(response: String)
  case SetUserInput(input: Option[String])
```

## Store

We use a Cats Effect `Queue` to hold outgoing messages.
The connection itself runs on a separate fiber safely
tied to the lifetime of the store using `.background`.
The `ff4s.WebsocketClient` is a wrapper around the more
powerful `http4s` client and intended for simple use-cases
such as this one.

```scala mdoc:js:shared
import cats.effect.*
import cats.effect.implicits.*
import cats.effect.std.*
import cats.syntax.all.*
import fs2.Stream

object Store:

  def apply[F[_]](using F: Async[F]) = for
    sendQ <- Queue.unbounded[F, String].toResource

    store <- ff4s.Store[F, State, Action](State()): _ =>
      case (Action.SetUserInput(input), state) =>
        state.copy(userInput = input) -> F.unit
      case (Action.Send, state) =>
        state -> state.userInput.foldMapM(sendQ.offer)
      case (Action.SetServerResponse(res), state) =>
        state.copy(serverResponse = res.some) -> F.unit

    _ <- ff4s
      .WebSocketClient[F]
      .bidirectionalText(
        "wss://ws.postman-echo.com/raw/",
        _.evalMap(res => store.dispatch(Action.SetServerResponse(res))),
        Stream.fromQueueUnterminated(sendQ)
      )
      .background
  yield store
```

## View

```scala mdoc:js:shared
import org.scalajs.dom

trait View:
  self: ff4s.Dsl[State, Action] =>

  import html.*

  val view =
    useState: state =>
      div(
        input(
          tpe := "text",
          placeholder := "your message here...",
          onInput := ((ev: dom.Event) =>
            val target = ev.target.asInstanceOf[dom.HTMLInputElement]
            if target.value.nonEmpty then
              Some(Action.SetUserInput(target.value.some))
            else Some(Action.SetUserInput(None))
          )
        ),
        button(
          "Send",
          disabled := state.userInput.isEmpty,
          onClick := (_ => Action.Send.some)
        ),
        state.serverResponse.fold(empty)(res => div(s"Server response: $res"))
      )
```

## App

The boilerplate construction of `ff4s.App` and `ff4s.IOEntryPoint` is omitted.

```scala mdoc:js:invisible
class App[F[_]](using F: Async[F]) extends ff4s.App[F, State, Action] with View:
  override val store = Store[F]
  override val rootElementId = node.getAttribute("id")

new ff4s.IOEntryPoint(new App, false).main(Array())
```
