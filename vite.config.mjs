import fs from "node:fs";
import path from "node:path";
import { defineConfig } from "vite";

// Dev server for the Scala.js demos.
//
// FF4S_DEMO  selects the demo (default: examples).
// FF4S_SCALA selects the Scala version whose bundle is served (default: 3).
//
// Each Scala version links into its own crossTarget (target/scala-2.13,
// target/scala-3.x.y), so the bundle is served from the matching directory
// under the fixed URL /scalajs/ rather than referenced by path in index.html.

const demos = {
  examples: { dir: "examples", bundle: "examples-fastopt" },
  todomvc: { dir: "todo-mvc", bundle: "todomvc-fastopt" },
};

const demoName = process.env.FF4S_DEMO || "examples";
const demo = demos[demoName];
if (!demo) {
  throw new Error(
    `Unknown FF4S_DEMO "${demoName}"; expected one of: ${Object.keys(demos).join(", ")}`,
  );
}

const scala = process.env.FF4S_SCALA || "3";
// Scala 2 crossTarget uses the binary version (scala-2.13), Scala 3 the full one.
const scalaDirVersion = scala.startsWith("2.")
  ? scala.split(".").slice(0, 2).join(".")
  : scala;
const isScalaDir = (name) =>
  name === `scala-${scalaDirVersion}` ||
  name.startsWith(`scala-${scalaDirVersion}.`);

const root = path.resolve(demo.dir);
const targetDir = path.join(root, "target");
const bundleUrl = "/scalajs";

const isBundleMain = (file) =>
  path.basename(file) === "main.js" &&
  path.basename(path.dirname(file)) === demo.bundle &&
  path.dirname(path.dirname(path.dirname(file))) === targetDir &&
  isScalaDir(path.basename(path.dirname(path.dirname(file))));

// Several directories can match, e.g. "3" with a stale scala-3.x.y left over
// from before a Scala upgrade. Serve the most recently linked one.
function currentBundleDir() {
  let entries;
  try {
    entries = fs.readdirSync(targetDir, { withFileTypes: true });
  } catch {
    return undefined;
  }
  let best;
  let bestTime = -Infinity;
  for (const entry of entries) {
    if (!entry.isDirectory() || !isScalaDir(entry.name)) continue;
    const dir = path.join(targetDir, entry.name, demo.bundle);
    try {
      const time = fs.statSync(path.join(dir, "main.js")).mtimeMs;
      if (time > bestTime) {
        best = dir;
        bestTime = time;
      }
    } catch {
      // not linked for this version
    }
  }
  return best;
}

export default defineConfig({
  root,
  server: {
    host: "127.0.0.1",
    port: 8080,
    strictPort: true,
  },
  plugins: [
    {
      name: "scala-js-bundle",
      configureServer(server) {
        server.middlewares.use(bundleUrl, (req, res) => {
          const dir = currentBundleDir();
          const file =
            dir && path.join(dir, decodeURIComponent(req.url.split("?")[0]));
          if (
            !file ||
            !file.startsWith(dir + path.sep) ||
            !fs.existsSync(file)
          ) {
            res.statusCode = 404;
            res.setHeader("Content-Type", "text/plain");
            res.end(
              `No Scala ${scala} ${demo.bundle} bundle under ${targetDir}. ` +
                `Is the matching watch recipe running?\n`,
            );
            return;
          }
          res.setHeader(
            "Content-Type",
            file.endsWith(".map") ? "application/json" : "text/javascript",
          );
          res.setHeader("Cache-Control", "no-store");
          fs.createReadStream(file).pipe(res);
        });

        server.watcher.add(
          path.join(targetDir, "scala-*", demo.bundle, "main.js"),
        );
        server.watcher.on("all", (event, file) => {
          if ((event === "add" || event === "change") && isBundleMain(file)) {
            server.ws.send({ type: "full-reload" });
          }
        });

        server.httpServer?.once("listening", () => {
          server.config.logger.info(
            `  Serving ${demoName} with the Scala ${scala} bundle`,
          );
        });
      },
    },
  ],
});
