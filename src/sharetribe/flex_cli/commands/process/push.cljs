(ns sharetribe.flex-cli.commands.process.push
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.api.client :refer [do-post]]
            [sharetribe.tempelhof.tx-process :as tx-process]))

(declare push-process)

(def cmd {:name "push"
          :handler #'push-process
          :desc "push a process file to the remote"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "[WIP] PROCESS_NAME"
                  :missing "--process is required"}
                 {:id :path
                  :long-opt "--path"
                  :required "[WIP] LOCAL_PROCESS_DIR"
                  :missing "--path is required"}]})

(defn push-process [params ctx]
  (go-try
    (let [{:keys [api-client marketplace]} ctx
          {:keys [process-name path]} params

          process-str (io-util/load-file path)

          ;; NOTE: this is used for validation, ignoring the parsed process
          _ (tx-process/parse-tx-process-string process-str)

          query-params {:marketplace marketplace}
          body-params {:name (keyword process-name)
                       :definition process-str}

          res (<? (do-post api-client "/processes/create-version" query-params body-params))]

      (io-util/ppd [:span
                    "Version " (-> res :data :process/version str)
                    " successfully saved for process " (-> res :data :process/name name)]))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process push -m bike-soil --process preauth-with-nightly-booking --path test-process/process.edn")
  )
