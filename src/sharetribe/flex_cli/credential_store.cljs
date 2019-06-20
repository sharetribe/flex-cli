(ns sharetribe.flex-cli.credential-store
  (:require [keytar]
            [clojure.core.async :as async :refer [go <! chan put! take!]]
            [sharetribe.flex-cli.exception :as exception]
            [clojure.string :as str]))

;; TODO We may want to scope this per environment (production,
;; staging, local etc.)
(def ^:const service "flex-cli")
(def ^:const username "api-key")

(defmethod exception/format-exception :credentials/api-key-not-found [_ _ _]
  "API key not found. Please log in first.")

(def ^:const failed-details
  (str/join
   " "
   ["This may happen if you are using an environment where a credential"
    "manager is not available, e.g. a headless environment without"
    "desktop capabilities."]))

(defmethod exception/format-exception :credentials/get-api-key-failed [_ _ _]
  (str "Failed to get the API key. " failed-details))

(defmethod exception/format-exception :credentials/set-api-key-failed [_ _ _]
  (str "Failed to store the API key. " failed-details))

(defmethod exception/format-exception :credentials/deleted-api-key-failed [_ _ _]
  (str "Failed to delete the API key. " failed-details))


(defn get-api-key []
  (let [c (chan)]
    (-> (.getPassword keytar service username)
        (.catch #(exception/throw! :credentials/get-api-key-failed %))
        (.then #(if (seq %)
                  (put! c %)
                  (exception/throw! :credentials/api-key-not-found))))
    c))

(defn set-api-key [password]
  (let [c (chan)]
    (-> (.setPassword keytar service username password)
        (.catch #(exception/throw! :credentials/set-api-key-failed %))
        (.then #(put! c ::success)))
    c))

(defn delete-api-key []
  (let [c (chan)]
    (-> (.deletePassword keytar service username)
        (.catch #(exception/throw! :credentials/delete-api-key-failed %))
        (.then #(put! c ::success)))
    c))

(comment
  (go
    (println (<! (get-api-key))))

  (go
    (println (<! (set-api-key "123123123asdf!!!"))))

  (go
    (println (<! (delete-api-key))))

  )

