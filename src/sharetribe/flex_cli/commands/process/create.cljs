(ns sharetribe.flex-cli.commands.process.create
  (:require [clojure.set :as set]
            [clojure.core.async :as async :refer [go <!]]
            [chalk]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.tempelhof.tx-process :as tx-process]
            [sharetribe.flex-cli.commands.process.exception-util :as process.exception-util]))

(defn format-process-exists-error [data]
  [:span process.exception-util/error-arrow
   " Process already exists: "
   (-> data api.client/api-error :details :process-name name)])

(defmethod exception/format-exception :process.create/api-call-failed [_ _ data]
  (case (:code (api.client/api-error data))
    :invalid-templates (process.exception-util/format-invalid-templates-error data)
    :tx-process-already-exists (format-process-exists-error data)
    (api.client/default-error-format data)))

(defmethod exception/format-exception :process.create/missing-templates [_ _ {:keys [notifications]}]
  [:span
   (map (fn [{:keys [name template]}]
          [:span process.exception-util/error-arrow
           " Template " (.bold chalk (clojure.core/name template))
           " not found for notification " (.bold chalk (clojure.core/name name))
           :line])
        notifications)])

(declare create-process)

(def cmd {:name "create"
          :handler #'create-process
          :desc "create a new transaction process"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "PROCESS_NAME"
                  :missing "--process is required"}
                 {:id :path
                  :long-opt "--path"
                  :required "LOCAL_PROCESS_DIR"
                  :missing "--path is required"}]})

(defn- ensure-process-dir! [path]
  (when-not (io-util/process-dir? path)
    (exception/throw! :command/invalid-args
                      {:command :push
                       :errors ["--path should be a process directory"]})))

(defn- ensure-templates! [tx-process templates]
  (let [process-tmpl-names (->> tx-process :notifications (map :template) set)
        template-names (set (map :name templates))
        extra-tmpls (set/difference template-names process-tmpl-names)
        missing-templates (remove (fn [n]
                                    (contains? template-names (:template n)))
                                  (:notifications tx-process))]
    (doseq [t extra-tmpls]
      (io-util/ppd-err [:span
                        (.bold.yellow chalk "Warning: ")
                        "template exists but is not used in the process: "
                        (.bold chalk (name t))]))
    (when (seq missing-templates)
      (exception/throw! :process.create/missing-templates {:notifications missing-templates}))))

(defn create-process [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name path]} params

         _ (ensure-process-dir! path)

         process-str (io-util/load-file (io-util/process-file-path path))
         templates (io-util/read-templates path)

         tx-process (tx-process/parse-tx-process-string process-str)
         _ (ensure-templates! tx-process templates)

         query-params {:marketplace marketplace}
         body-params {:name (keyword process-name)
                      :definition process-str
                      :templates templates}

         res (try
               (<? (do-post api-client "/processes/create" query-params body-params))
               (catch js/Error e
                 (throw
                  (api.client/retype-ex e :process.create/api-call-failed))))]

     (io-util/ppd [:span
                   "Process " (-> res :data :process/name name)
                   " successfully created."]))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process list -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "process create -m bike-soil --process new-process --path test-process")
  )
