(ns sharetribe.flex-cli.commands)

(def commands
  {:sub-cmds
   [{:name "process"
     :handler ::process}]})

(defn parse-error [parse-result]
  (doseq [e (-> parse-result :data :errors)]
    (println e))
  {:exit-status 1})

(defn command-not-found [parse-result]
  (println "Command not found:" (-> parse-result :data :arguments first))
  {:exit-status 1})

(defn error [parse-result]
  (case (:error parse-result)
    :parse-error (parse-error parse-result)
    :command-not-found (command-not-found parse-result)))

(defn handle [parse-result done]
  (let [result
        (cond
          (:error parse-result) (error parse-result))]
    (done result)))
