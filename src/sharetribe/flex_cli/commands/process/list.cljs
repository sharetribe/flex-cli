(ns sharetribe.flex-cli.commands.process.list
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.api.client :refer [do-get]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]))

(declare list-processes)

(def cmd {:name "list"
          :handler #'list-processes
          :desc "list all transaction processes"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "PROCESS_NAME"}]})

(defn list-all-processes [api-client marketplace]
  (go-try
   (let [res (<? (do-get api-client "/processes/query" {:marketplace marketplace}))
         process-names (map (fn [{:process/keys [name version]}]
                              {:name (io-util/namespaced-str name)
                               :latest-version version})
                            (:data res))]

     (io-util/print-table process-names))))

(defn list-process-versions [api-client marketplace process-name]
  (go-try
   (let [res (<? (do-get api-client "/processes/query-versions" {:marketplace marketplace
                                                                 :name process-name}))
         versions (map (fn [{:process/keys [version createdAt aliases transactionCount]}]
                         {:created (io-util/format-date-and-time createdAt)
                          :version version
                          :aliases (str/join ", " (map io-util/namespaced-str aliases))
                          :transactions transactionCount})
                       (:data res))
         version-placeholder {:version "..."}]
     (io-util/print-table (->> res
                               :data
                               (map (fn [{:process/keys [version createdAt aliases transactionCount]}]
                                      {:created (io-util/format-date-and-time createdAt)
                                       :version version
                                       :aliases (str/join ", " (map io-util/namespaced-str aliases))
                                       :transactions transactionCount})
                                    (:data res))
                               (reduce (fn [versions v]
                                         (let [prev (last versions)]
                                           (if (and prev (not= (dec (:version prev))
                                                               (:version v)))
                                             ;; Add a placeholder line if versions are not continuous.
                                             (conj versions version-placeholder v)
                                             (conj versions v))
                                           )
                                         )
                                       []))))))

(defn list-processes [params ctx]
  (let [{:keys [api-client marketplace]} ctx
        {:keys [process-name]} params]
    (if process-name
      (list-process-versions api-client marketplace process-name)
      (list-all-processes api-client marketplace))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process list -m bike-soil --process preauth-with-nightly-booking")
  )
