(ns sharetribe.flex-cli.commands.search-schema
  (:require [sharetribe.flex-cli.api.client :refer [do-get]]
            [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [com.cognitect.transit :as transit]
            [clojure.string :as str]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.commands.search-schema.set :as search-schema.set]
            [sharetribe.flex-cli.commands.search-schema.unset :as search-schema.unset]))

(declare list-search-schemas)

(def cmd {:name "search"
          :handler #'list-search-schemas
          :desc "list all search schemas"
          :opts []
          :sub-cmds [search-schema.set/cmd
                     search-schema.unset/cmd]})

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
     (let [res (<? (do-get api-client "/search-schemas/query" {:marketplace marketplace
                                                               :of "dataSchema.of/userProfile,dataSchema.of/listing"}))]
       (io-util/print-table
        [:of :key :scope :type :default-value :doc]
        (->> (:data res)
             (map (fn [{:dataSchema/keys [key doc scope valueType cardinality defaultValue of]}]
                    {:key (name key)
                     :doc doc
                     :scope (name scope)
                     :type (type-label valueType cardinality)
                     :default-value (default-value-label defaultValue)
                     :of (name of)}))
             (sort-by (juxt :of :scope :key))))))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "search -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "search unset --key testing --scope metadata -m bike-soil")
  )
