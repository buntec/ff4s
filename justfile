# Serve the examples at http://127.0.0.1:8080/ (e.g. `just serve-examples 2.13`)
serve-examples scala="3":
    FF4S_DEMO=examples FF4S_SCALA='{{scala}}' npm exec vite -- --config vite.config.mjs

# Recompile the examples on every source change (e.g. `just watch-examples 2.13`)
watch-examples scala="3":
    sbt '++ {{scala}}' '~examples/fastLinkJS'

# Serve TodoMVC at http://127.0.0.1:8080/
serve-todomvc scala="3":
    FF4S_DEMO=todomvc FF4S_SCALA='{{scala}}' npm exec vite -- --config vite.config.mjs

# Recompile TodoMVC on every source change
watch-todomvc scala="3":
    sbt '++ {{scala}}' '~todoMvc/fastLinkJS'

# Install the JavaScript dependencies (Vite) from package-lock.json
install:
    npm ci

# Run the ff4s test suite (in jsdom) for the given Scala version
test scala="3": install
    sbt '++ {{scala}}' ff4s/test
