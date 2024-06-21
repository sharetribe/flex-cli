(ns sharetribe.flex-cli.commands.process.create-or-push
  (:require [clojure.set :as set]
            [clojure.core.async :as async :refer [go <!]]
            [chalk]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-multipart-post]]
            [sharetribe.tempelhof.tx-process :as tx-process]
            [sharetribe.flex-cli.process-util :as process-util]))

(declare create-or-push-process)

(def cmd {:name "create-or-push"
          :handler #'create-or-push-process
          :desc "create or push a process file to the remote"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :desc "name of the process that should be created or updated"
                  :required "PROCESS_NAME"
                  :missing "--process is required"}
                 {:id :path
                  :long-opt "--path"
                  :desc "path to the directory with the new process files"
                  :required "LOCAL_PROCESS_DIR"
                  :missing "--path is required"}]})

(defn create-or-push-process [params ctx]
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
                       :templates templates})]

     (try (let [res
                (<? (do-multipart-post api-client "/processes/create-version" query-params body-params))]

            (if (= :no-changes (-> res :meta :result))
              (io-util/ppd [:span "No changes"])
              (io-util/ppd [:span
                            "Version " (-> res :data :process/version str)
                            " successfully saved for process " (-> res :data :process/name name)])))
          (catch js/Error pushE
            (if (= :tx-process-not-found (-> pushE ex-data :data :res :response :errors first :code))
              (try
                (let [res (<? (do-multipart-post api-client "/processes/create" query-params body-params))]
                  (io-util/ppd [:span
                                "Process " (-> res :data :process/name name)
                                " successfully created."]))
                (catch js/Error e
                  (throw
                   (api.client/retype-ex e :process.util/new-process-api-call-failed))))
              (throw pushE)))))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process create-or-push -m bike-soil --process preauth-with-nightly-booking --path test-process/process.edn"))
  
