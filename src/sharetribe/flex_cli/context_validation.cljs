(ns sharetribe.flex-cli.context-validation
  (:require [clojure.spec.alpha :as s]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.context-spec :as context-spec]))

(defn format-exception [{:keys [context spec]}]
  (let [problems (-> (s/explain-data spec context)
                     :cljs.spec.alpha/problems)]
    (str "Invalid context JSON: " problems)))

(defmethod exception/format-exception :context/invalid-context [_ _ data]
  (format-exception data))

(defn validate!
  "Validates a parsed email template context. Throws an exception if the
  context is invalid. Returns the context unmodified when it is
  valid."
  [context]
  (when-not (s/valid? :context/transaction-transition context)
    (exception/throw! :context/invalid-context
                      {:context context
                       :spec (s/spec :context/transaction-transition)}))
  context)
