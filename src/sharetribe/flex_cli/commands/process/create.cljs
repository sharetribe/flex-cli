(ns sharetribe.flex-cli.commands.process.create
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-multipart-post]]
            [sharetribe.tempelhof.tx-process :as tx-process]
            [sharetribe.flex-cli.process-util :as process-util]))

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

(defn create-process [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name path]} params

         _ (process-util/ensure-process-dir! path)

         process-str (io-util/load-file (io-util/process-file-path path))
         templates (io-util/read-templates path)

         tx-process (tx-process/parse-tx-process-string process-str)
         _ (process-util/ensure-templates! tx-process templates)

         query-params {:marketplace marketplace}
         body-params (process-util/to-multipart-form-data
                      {:name process-name
                       :definition process-str
                       :templates templates})

         res (try
               (<? (do-multipart-post api-client "/processes/create" query-params body-params))
               (catch js/Error e
                 (throw
                  (api.client/retype-ex e :process.util/new-process-api-call-failed))))]

     (io-util/ppd [:span
                   "Process " (-> res :data :process/name name)
                   " successfully created."]))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process list -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "process create -m bike-soil --process new-process --path test-process")
  )
