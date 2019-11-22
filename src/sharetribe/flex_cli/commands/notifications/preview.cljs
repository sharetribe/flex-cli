(ns sharetribe.flex-cli.commands.notifications.preview
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [go <! >! chan put! close!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.process-util :as process-util]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]
            [sharetribe.flex-cli.exception :as exception]
            [chalk]
            [open]
            [http]
            [goog.string]))

(declare preview)

(def cmd {:name "preview"
          :handler #'preview
          :desc "render a preview of an email template"
          :opts [{:id :template
                  :long-opt "--template"
                  :desc "path to a template directory"
                  :required "TEMPLATE_DIR"
                  :missing "--template is required"}
                 {:id :context
                  :long-opt "--context"
                  :desc "path to an email rendering context JSON file"
                  :required "CONTEXT_FILE_PATH"}]})

;; Some random port where the preview server is opened. Should not
;; clash with other common Flex tooling.
(def port 3535)

(def preview-server-url (str "http://localhost:" port))
(def preview-server (atom nil))

(defn close-preview-server! [server]
  (let [c (chan)]
    (go
      (when server
        (println "Closing preview server")
        (.close server #(close! c))
        (<! c)))))

(defn start-preview-server!
  "Start a new preview server

  Closes and waits for the existing channel before starting listening
  for requests with the new one. Returns a channel that closes when
  the new server is listening for requests."
  [request-handler]
  (println "Starting preview server")
  (let [listening-chan (chan)
        server (.createServer http request-handler)]
    (go
      (<! (close-preview-server! @preview-server))
      (reset! preview-server server)
      (.listen server port #(close! listening-chan))
      (<! listening-chan))))

(defn inject-title
  "Inject subject as a <title> tag to the HTML"
  [{:keys [subject html]}]
  (let [head (str "<head><title>" (goog.string/htmlEscape subject) "</title></head>")]
    (str/replace html #"^<html>" (str "<html>" head))))

(defn format-template [{:keys [subject text] :as template}]
  (str (.bold chalk "Template: ") (name (:name template))
       (.bold chalk "\nSubject: ") subject
       (.bold chalk "\nPlain text content:\n") text
       "\n---\n"
       "See " preview-server-url " for the HTML preview. "
       "Refresh the browser to reload the template and render a new preview. "
       "Type <Ctrl>+C to quit."))

(defn fetch-preview!
  "Fetch a template preview"
  [opts]
  (let [{:keys [api-client marketplace template context]} opts
        query-params {:marketplace marketplace}
        body-params (cond-> {:template (io-util/read-template template)}
                      context (assoc :template-context (io-util/load-file context)))]
    (println "================================================")
    (println "Fetching a new preview...")
    (go
      (try
        (let [res (<? (do-post api-client
                               "/notifications/preview"
                               query-params
                               body-params))]
          (println "Preview fetched\n")
          {:template (:data res)
           :error nil})
        (catch js/Error e
          {:template nil
           :error e})))))

(defn handle-error [error]
  (let [data (exception/data error)
        {:keys [out msg]} (case (:code (api.client/api-error data))
                            :invalid-template
                            {:msg "Invalid template. See terminal output for details."
                             :out (process-util/format-invalid-template-error data)}

                            {:msg "Error in API call. See terminal output for details."
                             :out (api.client/default-error-format data)})
        body-style "font-size: 32px; color: red"
        error-html (str "<html><body style=\"" body-style "\"><p>" msg "</p></body></html>")]
    (io-util/ppd-err out)
    (io-util/ppd-err (str "Fix the errors above and refresh "
                          preview-server-url
                          " in the browser to continue. Type <Ctrl>+C to quit."))
    {:status 400
     :content-type "text/html"
     :body error-html}))

(defn handle-success [template]
  (println (format-template template))
  {:status 200
   :content-type "text/html"
   :body (inject-title template)})

(defn create-preview-request-handler [opts]
  (let [response (fn [^js res opts]
                   (let [{:keys [status content-type body]} opts]
                     (set! (.-statusCode res) status)
                     (when content-type
                       (.setHeader res "Content-Type" content-type))
                     (when body
                       (.write res body))
                     (.end res)))]
    (fn [req res]
      (if (= "/" (.-url req))
        (go
          (let [{:keys [template error]} (<! (fetch-preview! opts))]
            (response res (if error
                            (handle-error error)
                            (handle-success template)))))
        (response res {:status 404})))))

(defn preview [params ctx]
  (let [{:keys [api-client marketplace]} ctx
        {:keys [template context]} params
        done-chan (chan)
        opts {:api-client api-client
              :marketplace marketplace
              :template template
              :context context}]
    (.on js/process "SIGINT" (fn [_]
                               (go
                                 (<! (close-preview-server! @preview-server))
                                 (close! done-chan))))
    (go
      (<! (start-preview-server! (create-preview-request-handler opts)))

      (println "Opening preview HTML in a browser")
      (open preview-server-url)

      (<! done-chan)
      (println "Preview server closed"))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "notifications preview -m bike-soil --template test-process/templates/booking-request-accepted --context test-process/sample-context.json")
  )
