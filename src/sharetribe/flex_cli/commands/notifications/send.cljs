(ns sharetribe.flex-cli.commands.notifications.send
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.process-util :as process-util]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]
            [sharetribe.flex-cli.exception :as exception]))

(declare send)

(def cmd {:name "send"
          :handler #'send
          :desc "send a preview of an email template to the logged in admin"
          :opts [{:id :template
                  :long-opt "--template"
                  :desc "path to a template directory"
                  :required "TEMPLATE_DIR"
                  :missing "--template is required"}
                 {:id :context
                  :long-opt "--context"
                  :desc "path to an email rendering context JSON file"
                  :required "CONTEXT_FILE_PATH"}]})

(defmethod exception/format-exception :notifications.send/api-call-failed [_ _ data]
  (case (:code (api.client/api-error data))
    :invalid-template (process-util/format-invalid-template-error data)
    (api.client/default-error-format data)))

(defn send [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [template context]} params
         tmpl (io-util/read-template template)
         body (cond-> {:template tmpl}
                context (assoc :template-context (io-util/load-file context)))
         res (try
               (<? (do-post api-client
                            "/notifications/send"
                            {:marketplace marketplace}
                            body))
               (catch js/Error e
                 (throw
                  (api.client/retype-ex e :notifications.send/api-call-failed))))
         {:keys [admin-email] :as tmpl} (:data res)]
     (println "Sent a preview to" admin-email))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "notifications send -m bike-soil --template test-process/templates/booking-request-accepted --context test-process/sample-context.json")
  )
