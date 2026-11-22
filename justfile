# Install the JavaScript dependencies (Vite) from package-lock.json
install:
    npm ci

# Recompile the examples on every source change
watch-examples:
    sbt '~examples/fastLinkJS'

# Serve the examples at http://127.0.0.1:8080/ (run `just install` first)
serve-examples:
    npm exec vite -- --config vite.config.mjs
