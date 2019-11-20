(ns sharetribe.flex-cli.commands.notifications.preview
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [go <! >! chan put!]]
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

(def port 3000)
(def preview-server (atom nil))
(def preview-template (atom nil))

(defn bold [str]
  (.bold chalk str))

(defmethod exception/format-exception :notifications.preview/api-call-failed [_ _ data]
  (case (:code (api.client/api-error data))
    :invalid-template (process-util/format-invalid-template-error data)
    (api.client/default-error-format data)))

(defn inject-title
  "Inject subject as a <title> tag to the HTML"
  [{:keys [subject html]}]
  (let [head (str "<head><title>" (goog.string/htmlEscape subject) "</title></head>")]
    (str/replace html #"^<html>" (str "<html>" head))))

(defn format-template [{:keys [subject text] :as template}]
  (str (bold "Template: ") (name (:name template))
       (bold "\nSubject: ") subject
       (bold "\nText:\n") text
       "\n---"))

(defn update-preview!
  "Update the template preview

  - Fetch new preview
  - Update response to the preview-template atom
  - Print out the text template"
  [opts]
  (let [{:keys [api-client marketplace template context]} opts
        query-params {:marketplace marketplace}
        body-params (cond-> {:template (io-util/read-template template)}
                      context (assoc :template-context (io-util/load-file context)))]
    (println "Fetching a new preview...")
    (go-try
     (let [res (try
                 (<? (do-post api-client
                              "/notifications/preview"
                              query-params
                              body-params))
                 (catch js/Error e
                   (throw
                    (api.client/retype-ex e :notifications.preview/api-call-failed))))
           tmpl (:data res)]
       (println (format-template tmpl))
       (reset! preview-template tmpl)))))

(defn create-request-handler [opts]
  (let [first-request (atom true)]
    (fn [^js req ^js res]
      (if (= "/" (.-url req))
        (go
          (when-not @first-request
            (<! (update-preview! opts)))
          (reset! first-request false)
          (set! (.-statusCode res) 200)
          (doto res
            (.setHeader "Content-Type" "text/html")
            (.write (inject-title @preview-template))
            (.end)))
        (do (set! (.-statusCode res) 404)
            (.end res))))))

(defn preview [params ctx]
  (let [{:keys [api-client marketplace]} ctx
        {:keys [template context]} params
        opts {:api-client api-client
              :marketplace marketplace
              :template template
              :context context}
        server (.createServer http (create-request-handler opts))
        url (str "http://localhost:" port)]
    (when @preview-server
      (.close @preview-server))
    (reset! preview-server server)
    (go
      (<! (update-preview! opts))
      (.listen server port #(println "Opening preview at" url))
      (open url))
    (chan)))

(comment
  (sharetribe.flex-cli.core/main-dev-str "notifications preview -m bike-soil --template test-process/templates/booking-request-accepted --context test-process/sample-context.json")
  )
