(ns sharetribe.flex-cli.commands.process.list
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.api.client :refer [do-get]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]))

(declare list-processes)

(def cmd {:name "list"
          :handler #'list-processes
          :desc "list all transaction processes"})

(defn list-processes [_ ctx]
  (go-try
    (let [{:keys [api-client marketplace]} ctx
          res (<? (do-get api-client "/processes/query" {:marketplace marketplace}))
          process-names (map (fn [{:process/keys [name version]}]
                               {:name (io-util/namespaced-str name)
                                :latest-version version})
                             (:data res))]

      (io-util/print-table process-names))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process list --marketplace=bike-soil")
  )
