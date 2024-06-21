(ns sharetribe.flex-cli.commands.process.create-or-push-and-create-or-update-alias
  (:require [clojure.set :as set]
            [clojure.core.async :as async :refer [go <!]]
            [chalk]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-multipart-post do-post]]
            [sharetribe.tempelhof.tx-process :as tx-process]
            [sharetribe.flex-cli.process-util :as process-util]))

(declare create-or-push-and-create-or-update-alias-process)

(def cmd {:name "create-or-push-and-create-or-update-alias"
          :handler #'create-or-push-and-create-or-update-alias-process
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
                  :missing "--path is required"}
                 {:id :alias
                  :long-opt "--alias"
                  :required "ALIAS"
                  :missing "--alias is required"
                  :desc "alias name, e.g. release-1"}]})

(defn create-alias [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name version alias]} params
         query-params {:marketplace marketplace}
         body-params {:name (keyword process-name)

                      ;; TODO: Use spec or tools-cli for parameter validation and coercion
                      :version (js/parseInt version)

                      :alias (keyword alias)}
         res (<? (do-post api-client "/aliases/create-alias" query-params body-params))]
     (io-util/ppd [:span
                   "Alias "
                   (-> res :data :processAlias/alias io-util/namespaced-str)
                   " successfully created to point to version "
                   (-> res :data :processAlias/version str)
                   "."]))))

(defn create-or-update-alias [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name version alias]} params
         query-params {:marketplace marketplace}
         body-params {:name (keyword process-name)

                      ;; TODO: Use spec or tools-cli for parameter validation and coercion
                      :version (js/parseInt version)

                      :alias (keyword alias)}]
     (try (let [res (<? (do-post api-client "/aliases/update-alias" query-params body-params))]
            (io-util/ppd [:span
                          "Alias "
                          (-> res :data :processAlias/alias io-util/namespaced-str)
                          " successfully updated to point to version "
                          (-> res :data :processAlias/version str)
                          "."]))
          (catch js/Error e
            (if (= :alias-not-found (-> e ex-data :data :res :response :errors first :code))
              (create-alias params ctx)
              (throw e)))))))

(defn create-or-push-and-create-or-update-alias-process [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name path alias]} params

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
                            " successfully saved for process " (-> res :data :process/name name)]))
            (create-or-update-alias {:process-name process-name
                                     :version (-> res :data :process/version str)
                                     :alias alias}
                                    ctx))
          (catch js/Error pushE
            (if (= :tx-process-not-found (-> pushE ex-data :data :res :response :errors first :code))
              (try
                (let [res (<? (do-multipart-post api-client "/processes/create" query-params body-params))]
                  (io-util/ppd [:span
                                "Process " (-> res :data :process/name name)
                                " successfully created."])
                  (create-or-update-alias {:process-name process-name
                                           :version (-> res :data :process/version str)
                                           :alias alias}
                                          ctx))
                (catch js/Error e
                  (throw
                   (api.client/retype-ex e :process.util/new-process-api-call-failed))))
              (throw pushE)))))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process create-or-push-and-create-or-update-alias -m bike-soil --process preauth-with-nightly-booking --path test-process/process.edn --alias release-1"))
  
