(ns sharetribe.flex-cli.commands.process.push
  (:require [clojure.set :as set]
            [clojure.core.async :as async :refer [go <!]]
            [chalk]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]
            [sharetribe.tempelhof.tx-process :as tx-process]
            [sharetribe.flex-cli.process-util :as process-util]))

(declare push-process)

(def cmd {:name "push"
          :handler #'push-process
          :desc "push a process file to the remote"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "PROCESS_NAME"
                  :missing "--process is required"}
                 {:id :path
                  :long-opt "--path"
                  :required "LOCAL_PROCESS_DIR"
                  :missing "--path is required"}]})

(defn push-process [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name path]} params

         _ (process-util/ensure-process-dir! path)

         process-str (io-util/load-file (io-util/process-file-path path))
         templates (io-util/read-templates path)

         tx-process (tx-process/parse-tx-process-string process-str)
         _ (process-util/ensure-templates! tx-process templates)

         query-params {:marketplace marketplace}
         body-params {:name (keyword process-name)
                      :definition process-str
                      :templates templates}

         res (try
               (<? (do-post api-client "/processes/create-version" query-params body-params))
               (catch js/Error e
                 (throw
                  (api.client/retype-ex e :process.util/new-process-api-call-failed))))]

     (if (= :no-changes (-> res :meta :result))
       (io-util/ppd [:span "No changes"])
       (io-util/ppd [:span
                     "Version " (-> res :data :process/version str)
                     " successfully saved for process " (-> res :data :process/name name)])))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process push -m bike-soil --process preauth-with-nightly-booking --path test-process/process.edn")
  )
