(ns sharetribe.flex-cli.core
  (:require [clojure.string :as str]
            [sharetribe.flex-cli.parse :as parse]
            [sharetribe.flex-cli.commands :as commands]))

(defn done [status]
  (when-let [status (:exit-status status)]
    (.exit js/process status)))

(defn done-dev [status]
  (println "")
  (println "[dev] Done.")
  (when-let [status (:exit-status status)]
    (println "[dev] Exit status:" status)))

(defn main* [cli-args done-fn]
  (-> cli-args
      (parse/parse commands/commands)
      (commands/handle done-fn)))

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

(defn ^:dev/after-load after-load
  "Callback function that is run after code hot loading. Prompts
  command-line args and will call the main function with given args."
  []
  (println "")
  (println (str "[" (.toISOString (js/Date.)) "] Code reloaded")))

(comment
  ;; Evaluate main-dev-str to run the CLI with arguments
  (main-dev-str "process -m bike-soil")

  )
