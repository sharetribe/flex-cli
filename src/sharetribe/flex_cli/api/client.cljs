(ns sharetribe.flex-cli.api.client
  (:require [ajax.core :as ajax]
            [clojure.core.async :as async :refer [put! chan <! go]]
            [chalk]
            [goog.object]
            [sharetribe.flex-cli.config :as config]
            [sharetribe.flex-cli.cli-info :as cli-info]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.view :as v]))

(def user-agent
  "User agent string with version and platform information

  Example: flex-cli/0.4.0 (darwin x64; Node.js v12.10.0)
  "
  (str "flex-cli/" cli-info/version
       " ("
       (.-platform js/process) " " (.-arch js/process) "; "
       "Node.js " (.-version js/process)
       ")"))

(defn bold [str]
  (.bold chalk str))

(def error-arrow (.bold.red chalk "\u203A"))

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

(defn default-error-format [data]
  (let [{:keys [req res]} data
        {:keys [path]} req
        {:keys [status response]} res
        marketplace (-> req :query :marketplace)
        api-key-suffix (->> req
                            :client
                            ::api-key
                            reverse
                            (take 4)
                            reverse
                            (apply str))]
    (cond
      (= 500 status)
      (error-page [:span "API call failed. Reason: Internal server error." :line])

      (and (= 401 status) (= "/current_admin/show" path))
      (error-page
       [:span "Error: Access denied" :line]
       [:span "Failed to verify API key ending with ..." (bold api-key-suffix) :line]
       [:span "Check your API key and use " (bold (str cli-info/bin " login")) " to relogin." :line])

      (= 401 status)
      (error-page
       [:span "Error: Access denied" :line]
       [:span "Failed to access marketplace " (bold marketplace) " with API key ending with ..."
        (bold api-key-suffix)
        :line]
       [:span "Use " (bold (str cli-info/bin " login")) " to relogin if needed." :line])

      :else
      (error-page [:span "API call failed. Status: " (str status) ", reason: " (or (-> response :errors first :title) "Unspecified") :line]))))

(defmethod exception/format-exception :api/error [_ _ data]
  (default-error-format data))

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

(defn retype-ex
  "Takes exception `e` and if it is an :api/error, retypes it to a more
  specific type `new-type`."
  [e new-type]
  (if (= :api/error (exception/type e))
    (exception/exception new-type (exception/data e))
    e))

(defn api-error
  "Takes API error exception data `ex-data` and returns an API error
  from the response map."
  [ex-data]
  (-> ex-data :res :response :errors first))

(defn do-get [client path query]
  (let [c (chan)]
    (ajax/ajax-request
     {:uri (str (config/value :api-base-url) path)
      :method :get
      :params query
      :headers {"Authorization" (str "Apikey " (::api-key client))
                "User-Agent" user-agent}
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
  (let [c (chan)]
    (ajax/ajax-request
     {:uri (str (config/value :api-base-url) path)
      :method :post
      :url-params query
      :params body
      :headers {"Authorization" (str "Apikey " (::api-key client))
                "User-Agent" user-agent}
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

(defn do-multipart-post [client path query ^js form-data]
  (let [c (chan)]
    (ajax/ajax-request
     {:uri (str (config/value :api-base-url) path)
      :method :post
      :url-params query
      :params form-data
      :headers {"Authorization" (str "Apikey " (::api-key client))
                "User-Agent" user-agent}
      :handler (fn [[ok? response]]
                 (put! c
                       (if ok?
                         response
                         (handle-error {:client client
                                        :path path
                                        :query query}
                                       response))))
      :format {:write (fn [^js form-data] (.getBuffer form-data))
               :content-type (goog.object/get (.getHeaders form-data) "content-type")}
      :response-format (ajax/transit-response-format)})
    c))

(defn new-client [api-key]
  {::api-key api-key})
