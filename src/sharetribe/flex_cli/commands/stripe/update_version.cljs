(ns sharetribe.flex-cli.commands.stripe.update-version
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [<!]]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.exception :as exception]))

(declare update-version)

;; Order matters, keep the newest version first in the vector
;; Prompt shows options in order
(def ^:const supported-versions ["2019-12-03" "2019-09-09" "2019-02-19"])

(def cmd {:name "update-version"
          :handler #'update-version
          :desc "update Stripe API version in use"
          :opts [{:id :version
                  :long-opt "--version"
                  :required "VERSION"
                  :desc (str "Stripe API version to update to. One of: " (str/join ", " supported-versions))}
                 {:id :force
                  :long-opt "--force"
                  :short-opt "-f"
                  :desc "force Stripe API version update without confirmation"}]})

(defn ensure-valid-version! [version]
  (when-not ((set supported-versions) version)
    (exception/throw! :command/invalid-args
                      {:command :update-version
                       :errors [(str "--version should be one of: " (str/join ", " supported-versions) ". Was " version ".")]})))

(defn ensure-confirmed! [confirm]
  (when-not confirm
    (exception/throw! :command/not-confirmed "Stripe API version update not confirmed. Exiting.")))

(defn update-version [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [version force]} params

         version (or version
                     (:version (<! (io-util/prompt [{:name :version
                                                     :choices supported-versions
                                                     :type :list
                                                     :message "Stripe API version"}]))))
         _ (ensure-valid-version! version)

         confirm (or force
                     (:confirm (<! (io-util/prompt [{:name :confirm
                                                     :type :confirm
                                                     :default false
                                                     :message (str "Updating Stripe API version to " version ".\n"
                                                                   "Make sure you are handling Capabilities (https://stripe.com/docs/connect/capabilities-overview)\n"
                                                                   "and identity verification (https://stripe.com/docs/connect/identity-verification)\n"
                                                                   "in your frontend as specified by your new API version.\n\n"
                                                                   "Confirm?")}]))))
         _ (ensure-confirmed! confirm)

         query-params {:marketplace marketplace}
         body-params {:version version}
         res (try
               (<? (do-post api-client "/stripe/update-version" query-params body-params))
               (catch js/Error e
                 (throw
                  (api.client/retype-ex e :stripe/update-api-version-failed))))]

     (io-util/ppd (str "Stripe API version successfully changed to " (-> res :data))))))
