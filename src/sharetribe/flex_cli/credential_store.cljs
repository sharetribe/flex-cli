(ns sharetribe.flex-cli.credential-store
  (:require [keytar]))

;; TODO We may want to scope this per environment (production,
;; staging, local etc.)
(def ^:const service "flex-cli")

(defn get-api-key [marketplace-ident {:keys [on-success on-error]}]
  (-> (.getPassword keytar service (name marketplace-ident))
      (.then on-success)
      (.catch on-error)))

(defn set-api-key [marketplace-ident password {:keys [on-success on-error]}]
  (-> (.setPassword keytar service (name marketplace-ident) password)
      (.then on-success)
      (.catch on-error)))

(defn delete-api-key [marketplace-ident {:keys [on-success on-error]}]
  (-> (.deletePassword keytar service (name marketplace-ident))
      (.then on-success)
      (.catch on-error)))

(comment
  (def callbacks {:on-success println
                  :on-error println})

  (get-api-key :bike-soil callbacks)
  (set-api-key :bike-soil "123123123asdf" callbacks)
  (delete-api-key :bike-soil)

  )

