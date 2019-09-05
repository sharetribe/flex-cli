(ns sharetribe.flex-cli.commands.process.pull
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.api.client :refer [do-get]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.exception :as exception]))

(declare pull-process)

(def cmd {:name "pull"
          :handler #'pull-process
          :desc "fetch a process file"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "[WIP] PROCESS_NAME"
                  :missing "--process is required"}
                 {:id :version
                  :long-opt "--version"
                  :required "[WIP] VERSION"}
                 {:id :alias
                  :long-opt "--alias"
                  :required "[WIP] ALIAS"}
                 {:id :path
                  :long-opt "--path"
                  :required "[WIP] LOCAL_PROCESS_DIR"
                  :missing "--path is required"}
                 {:id :force
                  :long-opt "--force"}]})

(defn- ensure-process-dir! [path force]
  (cond
    (io-util/file? path) (exception/throw! :command/invalid-args
                                           {:command :pull
                                            :errors ["--path should be a directory"]})

    (not (io-util/dir? path)) (do (io-util/log "Creating a new directory:" path)
                                  (io-util/mkdirp path))

    (and (io-util/process-dir? path) (not force)) (exception/throw! :command/invalid-args
                                                            {:command :pull
                                                             :errors ["--path is already a process directory, use --force to overwrite"]})))

(defn pull-process [params ctx]
  (go-try
    (let [{:keys [api-client marketplace]} ctx
          {:keys [process-name version alias path force]} params
          _ (when-not (or version alias)
              (exception/throw! :command/invalid-args {:command :pull
                                                       :errors ["--version or --alias is required"]}))
          _ (ensure-process-dir! path force)

          query-params (cond-> {:marketplace marketplace
                                :name process-name}
                         alias (assoc :alias alias)
                         version (assoc :version version))

          res (<? (do-get api-client "/processes/show-dev" query-params))

          process-data (-> res :data :process/process)
          templates-data (-> res :data :process/emailTemplates)

          process-file-path (io-util/join path "process.edn")]

      (io-util/save-file process-file-path process-data)

      (doseq [tmpl templates-data]
        (let [{:emailTemplate/keys [name subject html]} tmpl
              name-str (clojure.core/name name)]
          (io-util/mkdirp (io-util/template-path path name-str))
          (io-util/save-file (io-util/html-file-path path name-str) html)
          (io-util/save-file (io-util/subject-file-path path name-str) subject)))

      (io-util/log "Saved process to" path))))
