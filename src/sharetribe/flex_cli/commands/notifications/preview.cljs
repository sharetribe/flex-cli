(ns sharetribe.flex-cli.commands.notifications.preview
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.process-util :as process-util]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.context-validation :as context-validation]
            [chalk]
            [tmp]
            [open]))

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

(defn bold [str]
  (.bold chalk str))

(defmethod exception/format-exception :notifications.preview/api-call-failed [_ _ data]
  (case (:code (api.client/api-error data))
    :invalid-template (process-util/format-invalid-template-error data)
    (api.client/default-error-format data)))

(defn load-context! [path]
  (let [context-str (io-util/load-file path)]
    (-> context-str
        context-validation/parse!
        context-validation/validate!)
    context-str))

(defn open-tmp-file! [{:keys [subject html]}]
  (let [file (tmp/fileSync (clj->js {:keep true
                                     :prefix "preview-"
                                     :postfix ".html"}))

        ;; Inject subject as a <title> tag to the HTML
        head (str "<head><title>" subject "</title></head>")
        content (str/replace html #"^<html>" (str "<html>" head))]
    (io-util/save-file (.-name file) content)
    (println "Opening preview HTML in" (.-name file))
    (open (.-name file))))

(defn preview [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [template context]} params
         tmpl (io-util/read-template template)
         template-context (when context
                            (load-context! context))
         body (cond-> {:template tmpl}
                template-context (assoc :template-context template-context))
         res (try
               (<? (do-post api-client
                            "/notifications/preview"
                            {:marketplace marketplace}
                            body))
               (catch js/Error e
                 (throw
                  (api.client/retype-ex e :notifications.preview/api-call-failed))))
         {:keys [subject html text] :as tmpl} (:data res)]
     (println (str (bold "Template: ") (name (:name tmpl))
                   (bold "\nSubject: ") subject
                   (bold "\nText:\n") text
                   "\n---"))
     (open-tmp-file! {:subject subject :html html}))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "notifications preview -m bike-soil --template test-process/templates/booking-request-accepted --context test-process/sample-context.json")
  )
