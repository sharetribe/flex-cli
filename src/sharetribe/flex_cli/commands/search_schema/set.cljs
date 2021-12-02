(ns sharetribe.flex-cli.commands.search-schema.set
  (:refer-clojure :exclude [type])
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.string :as str]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.commands.search-schema.common :as common]
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
                  :desc (str "extended data scope (either metadata or public for listing schema,"
                             " metadata, private, protected or public for userProfile schema)")
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
                  :required "DEFAULT"}
                 {:id :schema-for
                  :long-opt "--schema-for"
                  :desc "Subject of the schema (either listing or userProfile, defaults to listing)"
                  :required "SCHEMA FOR"}]})

(def types #{"enum" "multi-enum" "boolean" "long" "text"})

(defn- ensure-valid-params! [params]
  (let [{:keys [key scope type default schema-for]} params
        scopes-for-schema-for (common/schema-for->scopes schema-for)
        errors (cond-> []
                 (str/includes? key ".")
                 (conj (str "--key cannot include dots (.). Only top-level keys can be indexed."))

                 (not (contains? types type))
                 (conj (str "--type must be one of: " (str/join ", " (map common/bold types))))

                 (not scopes-for-schema-for)
                 (conj (str "--schema-for must be one of: "
                            (str/join ", " (map common/bold (keys common/schema-for->scopes)))))

                 (not (contains? scopes-for-schema-for scope))
                 (conj (str "--scope must be one of: "
                            (str/join ", " (map common/bold scopes-for-schema-for)) " for " schema-for))

                 (and (some? default)
                      (= "boolean" type)
                      (not (boolean? default)))
                 (conj (str "--default must be either " (common/bold "true") " or " (common/bold false) " when --type is boolean"))

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
  (let [{:keys [doc key scope type default schema-for]} params]
    (merge {:key (keyword key)
            :scope (keyword "dataSchema.scope" scope)
            :valueType (keyword "dataSchema.type"
                                (if (= "multi-enum" type) "enum" type))
            :cardinality (if (= "multi-enum" type)
                           :dataSchema.cardinality/many
                           :dataSchema.cardinality/one)
            :of (keyword "dataSchema.of" schema-for)}
           (when (some? default)
             {:defaultValue default})
           (when (some? doc)
             {:doc doc}))))

(defn set-search-schema [{:keys [scope] :as params} ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         query {:marketplace marketplace}
         body (-> params
                  common/default-schema-for-param
                  coerce-default-value
                  ensure-valid-params!
                  body-params)]
     (<? (do-post api-client "/search-schemas/set" query body))

     (println
      (str
       (case scope
         "metadata" "Metadata"
         "private" "Private data"
         "protected" "Protected data"
         "public" "Public data")
       " schema, "
       (:key params)
       " is successfully set for "
       (name (:of body))
       ".")))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "help")
  (sharetribe.flex-cli.core/main-dev-str "search set --key complexValue.innerValue --scope metadata --type long -m bike-soil --default 1.0")
  ; => Invalid arguments for command set:
  ; --key cannot include dots (.). Only top-level keys can be indexed.
  (sharetribe.flex-cli.core/main-dev-str "search set --key age --scope protected --type long -m bike-soil --schema-for userProfile --doc \"bye\"")
  ; Protected data schema, age is successfully set for userProfile.
  (sharetribe.flex-cli.core/main-dev-str "search set --key age --scope metadata --type long -m bike-soil")
  ; Metadata schema, age is successfully set for listing.
  (sharetribe.flex-cli.core/main-dev-str "search -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "help search set")
  
  )
