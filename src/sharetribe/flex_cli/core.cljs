(ns sharetribe.flex-cli.core
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.args-parse :as args-parse]
            [sharetribe.flex-cli.command-defs :as command-defs]
            [sharetribe.flex-cli.command-exec :as command-exec]
            [sharetribe.flex-cli.exception :as exception]))

(defn done [status]
  (when-let [status (:exit-status status)]
    (.exit js/process status)))

(defn done-dev [status]
  (println "")
  (println "[dev] Done.")
  (when-let [status (:exit-status status)]
    (println "[dev] Exit status:" status)))

(defn print-error [e]
  (binding [*print-fn* *print-err-fn*]
    (println (exception/format-msg e))))

(defn error [e done-fn]
  (print-error e)
  (done-fn {:exit-status 1}))

(defn set-exception-handlers!
  "Set global exception handlers for uncaught exceptions and unhandled
  promise rejections."
  [callback-fn]
  (doto js/process
    (.removeAllListeners "uncaughtException")
    (.removeAllListeners "unhandledRejection")
    (.on "uncaughtException" (fn [err _] (callback-fn err)))
    (.on "unhandledRejection" (fn [reason _] (callback-fn reason)))))

(defn main* [cli-args done-fn]
  (set-exception-handlers! #(error % done-fn))

  (go
    (try
      (<!
       (-> cli-args
           (args-parse/parse command-defs/commands)
           (command-exec/execute command-defs/commands)))
      (done-fn {:exit-status 0})
      (catch js/Error e
        (error e done-fn)))))

(defn main
  "Main entrypoint for the CLI"
  [& cli-args]
  (main* cli-args done))

(defn main-dev
  [& cli-args]
  (main* cli-args done-dev))

(defn main-dev-str
  ([] (main-dev-str nil))
  ([cli-args-str]
   (if cli-args-str
     (apply main-dev (str/split cli-args-str " "))
     (main-dev))))

(defn ^:dev/after-load after-load []
  (set-exception-handlers! #(error % done-dev))

  (println "")
  (println (str "[" (.toISOString (js/Date.)) "] Code reloaded")))

(comment
  ;; Evaluate main-dev-str to run the CLI with arguments
  (main-dev-str "process -m bike-soil")

  (main-dev-str "process --path test-process")
  (main-dev-str "process --path test-process --transition transition/request")
  (main-dev-str "process --path test-process --transition transition/accept")
  )
