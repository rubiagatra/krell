(ns krell.gen
  (:require [cljs.compiler.api :as comp-api]
            [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [krell.assets :as assets]
            [krell.net :as net]
            [krell.util :as util])
  (:import [java.io File]))

(defn write-index-js
  "Write the Krell index.js file which bootstraps the Krell application.
  See resources/index.js"
  [opts]
  (let [source   (slurp (io/resource "index.js"))
        out-file (io/file "index.js")]
    ;; TODO: just writing this out to the top level, can we allow this to be
    ;; in a different location?
    (when (or (not (.exists out-file))
              (not (string/starts-with?
                      (-> out-file io/reader line-seq first)
                      "/* Generated by Krell")))
      (util/mkdirs out-file)
      (spit out-file
        (-> source
          (string/replace "$KRELL_OUTPUT_TO" (:output-to opts))
          (string/replace "$KRELL_OUTPUT_DIR" (:output-dir opts)))))))

(defn contents-equal? [f content]
  (= (slurp f) content))

(defn write-if-different [^File f content]
  (when-not (and (.exists f)
                 (contents-equal? f content))
    (spit f content)))

(defn write-repl-js
  "Write out the REPL support code. See resources/krell_repl.js"
  [repl-env opts]
  (let [source   (slurp (io/resource "krell_repl.js"))
        out-file (io/file (:output-dir opts) "krell_repl.js")]
    (util/mkdirs out-file)
    (write-if-different out-file
      (-> source
        (string/replace "$KRELL_SERVER_IP" (net/get-ip))
        (string/replace "$KRELL_SERVER_PORT" (-> repl-env :options :port str))
        (string/replace "$CLOSURE_BASE_PATH"
          (str (.getPath (io/file (:output-dir opts) "goog")) "/"))))))

(defn write-assets-js
  "Write out the REPL asset support code."
  [assets opts]
  (let [out-file (io/file (:output-dir opts) "krell_assets.js")]
    (util/mkdirs out-file)
    (write-if-different out-file (assets/assets-js assets))))

(defn export-dep [dep]
  (str "\""dep "\": require('" dep "')" ))

(defn krell-npm-deps-js
  "Returns the JavaScript code to support runtime require of bundled modules."
  [node-requires]
  (str
    "module.exports = {\n"
    "  krellNpmDeps: {\n"
    (string/join ",\n" (map (comp #(str "    " %) export-dep) node-requires))
    "  }\n"
    "};\n"))

(defn write-krell-npm-deps-js
  [node-requires opts]
  (let [out-file (io/file (:output-dir opts) "krell_npm_deps.js")]
    (util/mkdirs out-file)
    (write-if-different out-file (krell-npm-deps-js node-requires))))

(defn goog-require-str [sym]
  (str "goog.require(\"" (comp-api/munge sym) "\");"))

(defn krell-main-js
  "Return the source for build dependent entry point. See resources/main.dev.js
  and resources/main.prod.js"
  [opts]
  (let [source (slurp
                 (if (= :none (:optimizations opts))
                   (io/resource "main.dev.js")
                   (io/resource "main.prod.js")))]
    (-> source
      (string/replace "$KRELL_MAIN_NS" (str (munge (:main opts))))
      (string/replace "$CLOSURE_DEFINES" (json/write-str (:closure-defines opts)))
      (string/replace "$CLJS_PRELOADS"
        (string/join "\n" (map goog-require-str (:preloads opts)))))))
