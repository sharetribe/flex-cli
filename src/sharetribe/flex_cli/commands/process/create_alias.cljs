(ns sharetribe.flex-cli.commands.process.create-alias
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.api.client :refer [do-post]]
            [sharetribe.flex-cli.io-util :as io-util]))

(declare create-alias)

(def cmd {:name "create-alias"
          :handler #'create-alias
          :desc "create a new alias"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "PROCESS_NAME"
                  :missing "--process is required"}
                 {:id :version
                  :long-opt "--version"
                  :required "VERSION"
                  :missing "--version is required"}
                 {:id :alias
                  :long-opt "--alias"
                  :required "ALIAS"
                  :missing "--alias is required"}]})

(defn create-alias [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name version alias]} params
         query-params {:marketplace marketplace}
         body-params {:name (keyword process-name)
                      :version (js/parseInt version)
                      :alias (keyword alias)}
         res (<? (do-post api-client "/aliases/create-alias" query-params body-params))]
     (io-util/ppd [:span
                   "Alias "
                   (-> res :data :processAlias/alias str)
                   " successfully created."]))))
