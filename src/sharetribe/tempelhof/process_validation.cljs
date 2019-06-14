(ns sharetribe.tempelhof.process-validation
  (:require [clojure.spec.alpha :as s]
            [expound.alpha :as expound]
            [sharetribe.tempelhof.spec]
            [sharetribe.flex-cli.exception :as exception]))

(defmethod exception/format-exception :tx-process/invalid-process [_ _ {:keys [tx-process spec]}]
  (let [printer (expound/custom-printer {:print-specs? false
                                         :theme :figwheel-theme})
        explain-data (s/explain-data spec tx-process)]
    (with-out-str
      (printer explain-data))))

(defn validate!
  "Validates a v3 process map. Throws an exception if the process is
  invalid. Returns the process unmodified when it is valid."
  [tx-process]
  (when-not (s/valid? :tempelhof/tx-process tx-process)
    (exception/throw! :tx-process/invalid-process
                      {:tx-process tx-process
                       :spec (s/spec :tempelhof/tx-process)}))
  tx-process)
