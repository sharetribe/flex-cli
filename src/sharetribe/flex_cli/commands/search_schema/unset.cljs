(ns sharetribe.flex-cli.commands.search-schema.unset
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.string :as str]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
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
                 {:id :scope
                  :long-opt "--scope"
                  :desc "extended data scope (either public or metadata)"
                  :required "SCOPE"
                  :missing "--scope is required"}]})

(def scopes #{"public" "metadata"})

(defn- ensure-valid-params! [params]
  (let [{:keys [scope]} params
        errors (when-not (contains? scopes scope)
                 [(str "--scope must be one of: " (str/join ", " scopes))])]

    (when errors
      (exception/throw! :command/invalid-args {:command :unset
                                               :errors errors}))

    params))

(defn- body-params [params]
  (let [{:keys [key scope]} params]
    {:key (keyword key)
     :scope (keyword "dataSchema.scope" scope)}))

(defn unset-search-schema [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         query {:marketplace marketplace}
         body (-> params
                  ensure-valid-params!
                  body-params)

         res (<? (do-post api-client "/search-schemas/unset" query body))]

     (println
      (str
       (if (= "public" (:scope params))
         "Public data"
         "Metadata")
       " schema for " (:key params) " successfully unset.")))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "help")
  (sharetribe.flex-cli.core/main-dev-str "search list -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "search set --key category --scope metadata --type long -m bike-soil --default 1.12")
  (sharetribe.flex-cli.core/main-dev-str "search unset --key category --scope metadata -m bike-soil")
  )
