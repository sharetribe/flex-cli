(ns sharetribe.flex-cli.commands.process.delete-alias
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.api.client :refer [do-post]]
            [sharetribe.flex-cli.io-util :as io-util]))

(declare delete-alias)

(def cmd {:name "delete-alias"
          :handler #'delete-alias
          :desc "delete an existing alias"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "PROCESS_NAME"
                  :missing "--process is required"
                  :desc "process name, see process list for available names"}
                 {:id :alias
                  :long-opt "--alias"
                  :required "ALIAS"
                  :missing "--alias is required"
                  :desc "alias name, e.g. release-1"}]})

(defn delete-alias [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name version alias]} params
         query-params {:marketplace marketplace}
         body-params {:name (keyword process-name)
                      :alias (keyword alias)}
         res (<? (do-post api-client "/aliases/delete-alias" query-params body-params))]
     (io-util/ppd [:span
                   "Alias "
                   (-> res :data :processAlias/alias io-util/namespaced-str)
                   " successfully deleted."]))))
