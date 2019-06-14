(ns sharetribe.tempelhof.process-validation
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [sharetribe.tempelhof.spec]
            [sharetribe.flex-cli.exception :as exception]))

(expound/defmsg :tx-process.transition/name ":name should be a keyword that uniquely names the transition in the process. Example: :transition/request")
(expound/defmsg :tx-process.transition/to ":to should be a keyword that references a state in the process. Example: :state/requested")
(expound/defmsg :tx-process.transition/from ":from should be a keyword that references a state in the process. It can be left out if this is an initial transition. Example: :state/requested")

(expound/defmsg :tx-process.transition/action "Transition's action should be defined as an action description map. Example: {:name :action/action-name :config {:config-property \"configuration value\"}}")


(defmethod exception/format-exception :tx-process/invalid-process [_ _ {:keys [tx-process spec]}]
  (let [printer (expound/custom-printer {:print-specs? false
                                         :theme :figwheel-theme})
        explain-data (s/explain-data spec tx-process)
        validation-errors (with-out-str (printer explain-data))]
    (str "The process description is not valid\n\n"
         validation-errors)))

(defn validate!
  "Validates a v3 process map. Throws an exception if the process is
  invalid. Returns the process unmodified when it is valid."
  [tx-process]
  (when-not (s/valid? :tempelhof/tx-process tx-process)
    (exception/throw! :tx-process/invalid-process
                      {:tx-process tx-process
                       :spec (s/spec :tempelhof/tx-process)}))
  tx-process)
