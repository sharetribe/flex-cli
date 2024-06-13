(ns sharetribe.flex-cli.commands.listing-approval
  (:require [sharetribe.flex-cli.api.client :refer [do-get do-post]]
            [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]))

(declare query-listing-approval)
(declare enable-listing-approval)
(declare disable-listing-approval)

(def enable-cmd {:name "enable"
                 :handler #'enable-listing-approval
                 :desc "enable listing approvals"})

(def disable-cmd {:name "disable"
                  :handler #'disable-listing-approval
                  :desc "disable listing approvals"})

(def cmd {:name "listing-approval"
          :handler #'query-listing-approval
          :desc "check if listing approvals are enabled or disabled"
          :sub-cmds [enable-cmd
                     disable-cmd]})

(defn enable-listing-approval [_ ctx]
  (let [{:keys [api-client marketplace]} ctx]
    (go-try
     (let [_res (<? (do-post api-client "/listing-approval/enable" {:marketplace marketplace} {}))]
       (println "Successfully enabled listing approvals in" marketplace)))))

(defn disable-listing-approval [_ ctx]
  (let [{:keys [api-client marketplace]} ctx]
    (go-try
     (let [_res (<? (do-post api-client "/listing-approval/disable" {:marketplace marketplace} {}))]
       (println "Successfully disabled listing approvals in" marketplace)))))

(defn query-listing-approval [_ ctx]
  (let [{:keys [api-client marketplace]} ctx]
    (go-try
     (let [res (<? (do-get api-client "/marketplace/show" {:marketplace marketplace}))]
       (if (-> res :data :marketplace/requireListingApproval)
         (println "Listing approvals are enabled in" marketplace)
         (println "Listing approvals are disabled in" marketplace))))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "listing-approval -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "listing-approval enable -m bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "listing-approval disable -m bike-soil")
  )
