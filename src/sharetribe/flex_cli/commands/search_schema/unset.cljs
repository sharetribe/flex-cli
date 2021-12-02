(ns sharetribe.flex-cli.commands.search-schema.unset
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.string :as str]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.commands.search-schema.common :as common]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]))

(declare unset-search-schema)

(def cmd {:name "unset"
          :handler #'unset-search-schema
          :desc "unset search schema"
          :opts [{:id :key
                  :long-opt "--key"
                  :desc "key name"
                  :required "KEY"
                  :missing "--key is required"}
                 {:id :schema-for
                  :long-opt "--schema-for"
                  :desc "Subject of the schema (either listing or userProfile, defaults to listing)"
                  :required "SCHEMA FOR"}
                 {:id :scope
                  :long-opt "--scope"
                  :desc "extended data scope (either public or metadata)"
                  :required "SCOPE"
                  :missing "--scope is required"}]})

(defn- ensure-valid-params! [params]
  (let [{:keys [key schema-for scope]} params
        scopes-for-schema-for (common/schema-for->scopes schema-for)
        errors (cond-> []
                       (str/includes? key ".")
                       (conj "--key cannot include dots (.). Only top-level keys can be indexed.")

                       (not scopes-for-schema-for)
                       (conj (str "--schema-for must be one of: "
                                  (str/join ", " (map common/bold (keys common/schema-for->scopes)))))

                       (not (contains? scopes-for-schema-for scope))
                       (conj (str "--scope must be one of: "
                                  (str/join ", " (map common/bold scopes-for-schema-for)) " for " schema-for)))]

    (when (seq errors)
      (exception/throw! :command/invalid-args {:command :unset
                                               :errors errors}))

    params))

(defn- body-params [params]
  (let [{:keys [key schema-for scope]} params]
    {:key (keyword key)
     :scope (keyword "dataSchema.scope" scope)
     :of (keyword "dataSchema.of" schema-for)}))

(defn unset-search-schema [{:keys [scope] :as params} ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         query {:marketplace marketplace}
         body (-> params
                  common/default-schema-for-param
                  ensure-valid-params!
                  body-params)]
     (<? (do-post api-client "/search-schemas/unset" query body))

     (println
      (str
       (case scope
         "metadata" "Metadata"
         "private" "Private data"
         "protected" "Protected data"
         "public" "Public data")
       " schema, "
       (:key params)
       " is successfully unset for "
       (name (:of body))
       ".")))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "help")
  (sharetribe.flex-cli.core/main-dev-str "search list -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "search set --key category --scope metadata --type long -m bike-soil --default 1.12")
  (sharetribe.flex-cli.core/main-dev-str "search unset --key category --scope metadata -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "search -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "search unset --schema-for userProfile --key age --scope protected -m bike-soil")
  )
