(ns sharetribe.flex-cli.credential-store
  (:require [cljs.reader :refer [read-string]]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.xdg :as xdg]
            [sharetribe.flex-cli.io-util :as io-util]
            [clojure.string :as str]))

(def ^:const auth-file "auth.edn")

(defmethod exception/format-exception :credentials/api-key-not-found [_ _ _]
  "API key not found. Please log in first.")

(defn get-api-key []
  (if-let [api-key (-> auth-file
                       xdg/read-config-file!
                       read-string
                       :api-key)]
    api-key
    (exception/throw! :credentials/api-key-not-found)))

(defn set-api-key [api-key]
  (xdg/write-config-file! auth-file (pr-str {:api-key api-key})))

(defn delete-api-key []
  (when-let [auth-info (-> auth-file
                           xdg/read-config-file!
                           read-string)]
    (xdg/write-config-file! auth-file (pr-str (dissoc auth-info :api-key)))))

(comment
  (get-api-key)
  (set-api-key "123123123asdf!!!")
  (delete-api-key)

  )

