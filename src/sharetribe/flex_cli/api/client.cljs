(ns sharetribe.flex-cli.api.client
  (:require [ajax.core :as ajax]
            [clojure.core.async :as async :refer [put! chan <! go]]
            [com.cognitect.transit.types :as ty]
            [sharetribe.flex-cli.config :as config]
            [sharetribe.flex-cli.exception :as exception]))

(defmethod exception/format-exception :api/error [_ _ {:keys [status status-text response]}]
  (if (= 500 status)
    (str "API call failed. Reason: Internal server error.")
    (str "API call failed. Reason: " (or (-> response :errors first :title) "Unspecified"))))

(defn- handle-error
  "Handles error response from API by wrapping it in :api/error
  exception and returning the exception.

  This namespace provides default formatter for :api/error to format
  common API errors. If a command needs to do command specific
  formatting for an error, the command handler can catch the exception
  and handle it as it wishes."
  [response]
  (exception/exception
   :api/error
   (select-keys response [:status :status-text :failure :response])))

(extend-type ty/TaggedValue
  IPrintWithWriter
  (-pr-writer [tagged-value writer _]
    (case (.-tag tagged-value)
      "f" (-write writer (str (.-rep tagged-value) "M"))
      (-write writer (.toString tagged-value)))))

(defn do-get [client path query]
  (let [c (chan)]
    (ajax/ajax-request
     {:uri (str (config/value :api-base-url) path)
      :method :get
      :params query
      :headers {"Authorization" (str "Apikey " (::api-key client))}
      :handler (fn [[ok? response]]
                 (put! c
                       (if ok?
                         response
                         (handle-error response))))
      :format (ajax/transit-request-format)
      :response-format (ajax/transit-response-format)})
    c))

(defn do-post [client path query body]
  ;; TODO
  )

(defn new-client [api-key]
  {::api-key api-key})
