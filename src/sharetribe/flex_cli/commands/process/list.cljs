(ns sharetribe.flex-cli.commands.process.list
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.api.client :refer [do-get <?]]
            [sharetribe.flex-cli.io-util :as io-util]))

(declare list-processes)

(def cmd {:name "list"
          :handler #'list-processes})

(defn list-processes [_ ctx]
  (go
    (let [{:keys [api-client marketplace]} ctx
          res (<? (do-get api-client "/processes/query" {:marketplace marketplace}))
          process-names (->> res
                             :data
                             (map #(-> %
                                       (update :process/name io-util/namespaced-str)
                                       (dissoc :process/id))))]

      (io-util/print-table process-names))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process list --marketplace=bike-soil")
  )
