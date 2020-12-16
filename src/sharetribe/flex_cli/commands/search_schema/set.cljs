(ns sharetribe.flex-cli.commands.search-schema.set
  (:refer-clojure :exclude [type])
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.string :as str]
            [chalk]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]))

(declare set-search-schema)

(def cmd {:name "set"
          :handler #'set-search-schema
          :desc "set search schema"
          :opts [{:id :key
                  :long-opt "--key"
                  :desc "key name"
                  :required "KEY"
                  :missing "--key is required"}
                 {:id :scope
                  :long-opt "--scope"
                  :desc "extended data scope (either public or metadata)"
                  :required "SCOPE"
                  :missing "--scope is required"}
                 {:id :type
                  :long-opt "--type"
                  :desc "value type (either enum, multi-enum, boolean, long or text)"
                  :required "TYPE"
                  :missing "--type is required"}
                 {:id :doc
                  :long-opt "--doc"
                  :desc "description of the schema"
                  :required "DOC"}
                 {:id :default
                  :long-opt "--default"
                  :desc "default value for search if value is not set"
                  :required "DEFAULT"}]})

(defn bold [str]
  (.bold chalk str))

(def types #{"enum" "multi-enum" "boolean" "long" "text"})
(def scopes #{"public" "metadata"})

(defn- ensure-valid-params! [params]
  (let [{:keys [key scope type default]} params

        errors (cond-> []
                 (str/includes? key ".")
                 (conj (str "--key can not include dots (.). Only top-level keys can be indexed."))

                 (not (contains? types type))
                 (conj (str "--type must be one of: " (str/join ", " (map bold types))))

                 (not (contains? scopes scope))
                 (conj (str "--scope must be one of: " (str/join ", " (map bold scopes))))

                 (and (some? default)
                      (= "boolean" type)
                      (not (boolean? default)))
                 (conj (str "--default must be either " (bold "true") " or " (bold false) " when --type is boolean"))

                 (and (some? default)
                      (= "long" type)
                      (not (int? default)))
                 (conj (str "--default must be an integer value when --type is long")))]

    (when (seq errors)
      (exception/throw! :command/invalid-args {:command :set
                                               :errors errors}))

    params))

(defn ->boolean [s]
  (case s
    "true" true
    "false" false
    s))

(defn ->number [s]
  (let [parsed (js/Number.parseFloat s)]
    (if-not (js/Number.isNaN parsed)
      parsed
      s)))

(defn ->list [s]
  (map str/trim (str/split s ",")))

(defn coerce-default-value [params]
  (update params :default (fn [s]
                            (case (:type params)
                              "boolean" (->boolean s)
                              "long" (->number s)
                              "multi-enum" (->list s)
                              s))))

(defn body-params [params]
  (let [{:keys [key scope type default]} params]
    (cond-> {:key (keyword key)
             :scope (keyword "dataSchema.scope" scope)
             :valueType (keyword "dataSchema.type"
                                 (if (= "multi-enum" type) "enum" type))
             :cardinality (if (= "multi-enum" type)
                            :dataSchema.cardinality/many
                            :dataSchema.cardinality/one)}
      (some? default) (assoc :defaultValue default))))

(defn set-search-schema [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         query {:marketplace marketplace}
         body (-> params
                  coerce-default-value
                  ensure-valid-params!
                  body-params)

         res (<? (do-post api-client "/search-schemas/set" query body))]

     (println
      (str
       (if (= "public" (:scope params))
         "Public data"
         "Metadata")
       " schema for " (:key params) " successfully set.")))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "help")
  (sharetribe.flex-cli.core/main-dev-str "search set --key complexValue.innerValue --scope metadata --type long -m bike-soil --default 1.0")
  )
