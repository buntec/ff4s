import path from "node:path";
import { defineConfig } from "vite";

const examplesRoot = path.resolve("examples");
const generatedMainPattern = path.join(
  examplesRoot,
  "target",
  "**",
  "examples-fastopt",
  "main.js",
);

const isGeneratedMain = (file) =>
  file.endsWith(`${path.sep}examples-fastopt${path.sep}main.js`);

export default defineConfig({
  root: examplesRoot,
  server: {
    host: "127.0.0.1",
    port: 8080,
    strictPort: true,
  },
  plugins: [
    {
      name: "scala-js-full-reload",
      configureServer(server) {
        server.watcher.add(generatedMainPattern);
        server.watcher.on("all", (event, file) => {
          if (
            (event === "add" || event === "change") &&
            isGeneratedMain(file)
          ) {
            server.ws.send({ type: "full-reload" });
          }
        });
      },
    },
  ],
});
