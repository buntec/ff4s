# Concepts

ff4s takes inspiration from [Elm](https://elm-lang.org/) and [Redux](https://redux.js.org/).
In short,

* All state is global and immutable and can be updated only by dispatching *actions*.
* Actions are coordinated by a global *store*. Some actions are pure state updates,
some trigger side-effects (e.g., fetching data from the back-end), and some do both.
* The *view* (what gets rendered to the DOM) is a pure function of the state.
* The store exposes the state as a `fs2.Signal[F, State]` so we can subscribe to
changes to (parts of) the state that we are interested in and dispatch
actions in response.

More concretely, every ff4s app will have these ingredients:

* A type `State`, usually encoded as a case class, holding the entire UI state.
* A type `Action`, usually encoded as an ADT, describing the set of possible actions.
* A `ff4s.Store[F, State, Action]` constructed by assigning actions
to state updates and effects.
* A declarative description of the UI based on the current state using
the built-in DSL for HTML markup.

If this sounds too abstract, then take a look at the examples and you will
quickly get the hang of it!
