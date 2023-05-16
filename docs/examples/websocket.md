# WebSocket

In this example we are going to the demonstrate the use of WebSocket
connections in ff4s apps.

We are going to use the following state and action encoding:

```scala mdoc:js:shared
final case class State(
    userInput: Option[String] = None,
    serverResponse: Option[String] = None
)

sealed trait Action
case class SetUserInput(input: Option[String]) extends Action
case object Send extends Action
case class SetServerResponse(text: String) extends Action
```

The interesting bit is the store:

```scala mdoc:js:shared
import cats.effect._
import cats.effect.implicits._
import cats.effect.std._
import cats.syntax.all._
import fs2.Stream

object Store {

  def apply[F[_]](implicit F: Async[F]) = for {

    // queue for outbound WS messages
    sendQ <- Queue.unbounded[F, String].toResource

    store <- ff4s.Store[F, State, Action](State()) { _ =>
      _ match {
        case SetUserInput(input) => _.copy(userInput = input) -> none
        case Send => state => state -> state.userInput.map(sendQ.offer)
        case SetServerResponse(text) => _.copy(serverResponse = text.some) -> none
      }
    }

    // establish websocket connection
    _ <- ff4s
      .WebSocketClient[F]
      .bidirectionalText(
        "wss://ws.postman-echo.com/raw/",
        _.evalMap(r => store.dispatch(SetServerResponse(r))),
        Stream.fromQueueUnterminated(sendQ)
      )
      .background

  } yield store

}
```

The view is simple:

```scala mdoc:js:shared
import org.scalajs.dom

object View {

  def apply[F[_]](implicit dsl: ff4s.Dsl[F, State, Action]) = {

    import dsl._
    import dsl.html._

    useState { state =>
      div(
        cls := "flex flex-col",
        input(
          tpe := "text",
          cls := "text-center m-1 rounded font-light shadow",
          placeholder := "type something here...",
          onInput := ((ev: dom.Event) =>
            ev.target match {
              case el: dom.HTMLInputElement =>
                if (el.value.nonEmpty) Some(SetUserInput(el.value.some))
                else Some(SetUserInput(none))
              case _ => None
            }
          )
        ),
        button(
          tpe := "button",
          onClick := (_ => Send.some),
          "Send"
        ),
        span(
          cls := "text-center",
          s"Server response:  ${state.serverResponse.getOrElse("")}"
        )
      )
    }
  }

}
```

```scala mdoc:js:invisible
class App[F[_]](implicit F: Async[F]) extends ff4s.App[F, State, Action] {
  override val store = Store[F]
  override val view = View[F]
  override val rootElementId = node.getAttribute("id")
}
new ff4s.IOEntryPoint(new App, false).main(Array())
```
