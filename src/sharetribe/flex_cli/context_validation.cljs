(ns sharetribe.flex-cli.context-validation
  (:require [clojure.spec.alpha :as s]
            [phrase.alpha :as phrase :refer [defphraser]]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.phrasing :as phrasing]
            [sharetribe.flex-cli.context-spec :as context-spec]
            [chalk]))

(defmethod exception/format-exception :context/invalid-context [_ _ exception-data]
  (phrasing/format-validation-exception exception-data))

(defmethod exception/format-exception :context/parse-error [_ _ data]
  (str "Failed to parse the context: " (:error data)))

(defn parse! [context-str]
  (js->clj (try
             (.parse js/JSON context-str)
             (catch js/Error e
               (exception/throw! :context/parse-error {:error e})))
           :keywordize-keys true))

(defn validate!
  "Validates a parsed email template context. Throws an exception if the
  context is invalid. Returns the context unmodified when it is
  valid."
  [context]
  (when-not (s/valid? :context/transaction-transition context)
    (exception/throw! :context/invalid-context
                      {:data context
                       :spec (s/spec :context/transaction-transition)}))
  context)
