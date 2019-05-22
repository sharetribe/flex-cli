(ns sharetribe.flex-cli.core
  (:require [readline]
            [clojure.string :as str]))

(defn main
  "Main entrypoint for the CLI"
  [& cli-args]
  (println "Welcome to Flex CLI")
  (println "CLI args: " cli-args))

(defonce waiting-answer? (atom false))

(defn create-readline! []
  (doto (.createInterface readline #js {:output js/process.stdout
                                        :input js/process.stdin})
    (.setPrompt "flex> ")
    (.on "line" #(when @waiting-answer?
                   (let [cli-args (str/split % " ")]
                     (apply main cli-args)
                     (reset! waiting-answer? false))))))

(defonce rl (create-readline!))

(defn ^:dev/after-load after-load
  "Callback function that is run after code hot loading. Prompts
  command-line args and will call the main function with given args."
  []
  (println "")
  (println (str "[" (.toISOString (js/Date.)) "] Code reloaded"))
  (.prompt rl)
  (reset! waiting-answer? true))
