(ns sharetribe.flex-cli.api.client
  (:require ["stream" :refer [Readable]]
            [util]
            [cognitect.transit :as t]
            [clojure.core.async :as async :refer [go]]
            [chalk]
            [goog.object]
            [sharetribe.flex-cli.config :as config]
            [sharetribe.flex-cli.cli-info :as cli-info]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.view :as v]
            [sharetribe.flex-cli.async-util :refer [->chan <? go-try]]))

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
        {:keys [status response original-text]} res
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
      (error-page (cond->
                      [:span "API call failed. Status: " (str status)
                       ", reason: " (or (-> response :errors first :title) original-text "Unspecified")]
                    ;; Enable for debugging
                    #_#_(-> response :errors first :details) (conj (str ", details: " (-> response :errors first :details)))
                    true (conj :line))))))

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

(def transit-reader (t/reader :json))
(def transit-writer (t/writer :json))

(defn- to-query-param [v]
  (clj->js v))

(defn- construct-url [url query-params]
  (let [url (js/URL. url)]
    (doseq [[k v] query-params]
      (.set (.-searchParams url) (name k) (to-query-param v)))
    url))

(defn- content-type [response]
  (try
    (util/MIMEType. (-> response .-headers (.get "content-type")))
    (catch js/Error _e
      nil)))

(defn- parse-text [response]
  (go
    (try
      {:success true
       :type "text/plain"
       :content (<? (->chan (.text response)))}
      (catch js/Error _e
        {:success false}))))

(defn- parse-transit [response]
  (go
    (try
      {:success true
       :type "application/transit+json"
       :content (t/read transit-reader (<? (->chan (.text response))))}
      (catch js/Error _e
        {:success false}))))

(defn- parse-zip [response]
  (go
    {:success true
     :content (.fromWeb Readable (.-body response))}))

(defn- parse-body
  "Takes response and parses the body based on response content type header.

  Returns a channel with map of:
  - success (boolean)
  - content (parsed content, only if success)
  "
  [response]
  (let [essence (when-let [ct ^js (content-type response)]
                  (.-essence ct))]
    (case essence
      ("text/plain" nil) (parse-text response) 
      "application/transit+json" (parse-transit response)
      "application/zip" (parse-zip response))))

(defn- handle-response [accept response req]
  (go-try
    (let [{:keys [success content type]} (<? (parse-body response))
          ;; Check if parsed content type matches with what we expected
          accepted? (= accept type)]
      (if (.-ok response)
        ;; If response was ok, we assume that parsing was
        ;; successful and returned content type was what we
        ;; expected.
        content
        (handle-error
         req
         (cond-> {:status (.-status response)
                  :status-text (.-statusText response)}

           ;; Assoc :response only if parsing was
           ;; successful and response content type
           ;; was what we expected. If we for
           ;; example use Accept:
           ;; application/transit+json, but get
           ;; back 500 with text/plain, we don't
           ;; assoc it to response
           (and accepted? success) (assoc :response content)

           (= "text/plain" type)
           (assoc :original-text content)))))))

(defn- do-request [client path method query body opts]
  (go-try
     (let [accept (or (::accept opts) "application/transit+json")
           res (<? (->chan (js/fetch
                            (construct-url (str (config/value :api-base-url) path) query)
                            (clj->js
                             (cond->
                                 {:method method
                                  :headers (cond-> {"Authorization" (str "Apikey " (::api-key client))
                                                    "User-Agent" user-agent
                                                    "Accept" accept}
                                             (::content-type opts) (assoc "Content-Type" (::content-type opts)))}
                               body (assoc :body body))))))]

       (<? (handle-response accept res {:client client
                                        :path path
                                        :query query})))))

(defn do-get
  ([client path query] (do-get client path query nil))
  ([client path query opts]
   (do-request client path "GET" query nil opts)))

(defn do-post [client path query body]
  (do-request client path "POST" query (t/write transit-writer body) {::content-type "application/transit+json"}))

(defn do-multipart-post [client path query form-data]
  (do-request client path "POST" query form-data nil))

(defn new-client [api-key]
  {::api-key api-key})
