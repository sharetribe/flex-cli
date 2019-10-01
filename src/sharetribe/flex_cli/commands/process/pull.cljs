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
                  :desc "name of the process that is fetched"
                  :required "PROCESS_NAME"
                  :missing "--process is required"}
                 {:id :version
                  :desc "version of the process that is fetched"
                  :long-opt "--version"
                  :required "VERSION"}
                 {:id :alias
                  :desc "alias to a specific process version, e.g. release-1"
                  :long-opt "--alias"
                  :required "ALIAS"}
                 {:id :path
                  :long-opt "--path"
                  :desc "path to the directory where the process should be saved"
                  :required "LOCAL_PROCESS_DIR"
                  :missing "--path is required"}
                 {:id :force
                  :desc "force overwriting an existing process directory"
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

          res (<? (do-get api-client "/processes/show" query-params))]

      (io-util/write-process path (:data res))
      (io-util/log "Saved process to" path))))
