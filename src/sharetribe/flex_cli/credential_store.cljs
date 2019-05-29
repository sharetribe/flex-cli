(ns sharetribe.flex-cli.credential-store
  (:require [keytar]))

;; TODO We may want to scope this per environment (production,
;; staging, local etc.)
(def ^:const service "flex-cli")
(def ^:const username "api-key")

(defn get-api-key [{:keys [on-success on-error]}]
  (-> (.getPassword keytar service username)
      (.then on-success)
      (.catch on-error)))

(defn set-api-key [password {:keys [on-success on-error]}]
  (-> (.setPassword keytar service username password)
      (.then on-success)
      (.catch on-error)))

(defn delete-api-key [{:keys [on-success on-error]}]
  (-> (.deletePassword keytar service username)
      (.then on-success)
      (.catch on-error)))

(comment
  (def callbacks {:on-success println
                  :on-error println})

  (get-api-key callbacks)
  (set-api-key "123123123asdf" callbacks)
  (delete-api-key)

  )

