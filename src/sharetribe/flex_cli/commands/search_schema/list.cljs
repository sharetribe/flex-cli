(ns sharetribe.flex-cli.commands.search-schema.list
  (:require [sharetribe.flex-cli.api.client :refer [do-get]]
            [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [com.cognitect.transit :as transit]
            [clojure.string :as str]))

(declare list-search-schemas)

(def cmd {:name "list"
          :handler #'list-search-schemas
          :desc "list all search schemas"
          :opts []})

(defn- type-label [value-type cardinality]
  (if (= [:dataSchema.type/enum :dataSchema.cardinality/many]
         [value-type cardinality])
    "multi-enum"
    (name value-type)))

(defn- unwrap-tagged [v]
  (if (transit/isTaggedValue v)
    (goog.object/get v "rep")
    v))

(defn- default-value-label
  "Takes value `v` and returns stringified version of it.

  In theory, the value can be a Transit Tagged value, because the type
  of the default value can be anything in the database level since we
  use stringified EDN. Thus, we'll do the best effort to show what
  ever value is passed."
  [v]
  (let [v (unwrap-tagged v)]
    (if (coll? v)
      (str/join ", " v)
      (str v))))

(defn list-search-schemas [_ ctx]
  (let [{:keys [api-client marketplace]} ctx]
    (go-try
     (let [res (<? (do-get api-client "/search-schemas/query" {:marketplace marketplace}))]
       (io-util/print-table
        [:key :scope :type :default-value :doc]
        (->> (:data res)
             (map (fn [{:dataSchema/keys [key doc scope valueType cardinality defaultValue]}]
                    {:key (name key)
                     :doc doc
                     :scope (name scope)
                     :type (type-label valueType cardinality)
                     :default-value (default-value-label defaultValue)}))
             (sort-by (juxt :scope :key))))))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "search list -m bike-soil")
  )
