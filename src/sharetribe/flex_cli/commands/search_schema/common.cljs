(ns sharetribe.flex-cli.commands.search-schema.common
  (:require
   [chalk]
   [clojure.string :as str]))

(defn bold [str]
  (.bold chalk str))

(def schema-for->scopes
  {"userProfile" #{"metadata" "private" "protected" "public"}
   "listing" #{"metadata" "public"}})

(defn default-schema-for-param [{:keys [schema-for] :as params}]
  (if (str/blank? schema-for)
    (assoc params :schema-for "listing")
    params))
