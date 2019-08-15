(ns sharetribe.flex-cli.api.client
  (:require [ajax.core :as ajax]
            [clojure.core.async :as async :refer [put! chan <! go]]
            [com.cognitect.transit.types :as ty]
            [chalk]
            [sharetribe.flex-cli.config :as config]
            [sharetribe.flex-cli.cli-info :as cli-info]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.view :as v]))

(defn bold [str]
  (.bold chalk str))

(def error-arrow (.red chalk "\u203A"))

(defn error-page
  "Component for a 'error-page'. Page consists of sections that are separated
  with line breaks. Each line starts with a red arrow and small indent."
  [& sections]
  [:span
   (interleave
    (repeat [:span " " error-arrow " "])
    (v/interpose-some :line sections))
   :line ;; add one line break at the end to get some extra space
   ])

(defmethod exception/format-exception :api/error [_ _ {:keys [req res]}]
  (let [{:keys [status response]} res
        marketplace (-> req :query :marketplace)
        api-key (-> req :client ::api-key)]
    (case status
      500 (error-page [:span "API call failed. Reason: Internal server error." :line])
      401 (error-page
           [:span "Error: Access denied" :line]
           [:span
            "Failed to access marketplace "
            (bold marketplace)
            " with API key ending with ..."
            (bold
             (->> api-key
                  reverse
                  (take 4)
                  reverse
                  (apply str)))
            :line]
           [:span "Use " (bold (str cli-info/bin " login")) " to relogin if needed." :line])
      (error-page [:span "API call failed. Status: " (str status) ", reason: " (or (-> response :errors first :title) "Unspecified") :line]))))

(defn- handle-error
  "Handles error response from API by wrapping it in :api/error
  exception and returning the exception.

  This namespace provides default formatter for :api/error to format
  common API errors. If a command needs to do command specific
  formatting for an error, the command handler can catch the exception
  and handle it as it wishes."
  [req res]
  (exception/exception
   :api/error
   {:req req
    :res res}))

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
                         (handle-error {:client client
                                        :path path
                                        :query query}
                                       response))))
      :format (ajax/transit-request-format)
      :response-format (ajax/transit-response-format)})
    c))

(defn do-post [client path query body]
  ;; TODO
  )

(defn new-client [api-key]
  {::api-key api-key})
