(ns sharetribe.flex-cli.commands.stripe.update-version
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [<!]]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.exception :as exception]))

(declare update-version)

(def cmd {:name "update-version"
          :handler #'update-version
          :desc "Update Stripe API version in use."
          :opts [{:id :version
                  :long-opt "--version"
                  :required "VERSION"
                  :desc "Stripe API version to update to. One of 2019-02-19 or 2019-09-09."}
                 {:id :force
                  :long-opt "--force"
                  :short-opt "-f"
                  :desc "Force Stripe API version update without confirmation."}]})

(defn ensure-valid-version! [version]
  (when-not (#{"2019-02-19" "2019-09-09"} version)
    (exception/throw! :command/invalid-args
                      {:command :update-version
                       :errors ["--version should be one of 2019-02-19 or 2019-09-09. Was: " version]})))

(defn confirm! [force version]
  (let [confirm (or force
                    (:confirm (<! (io-util/prompt [{:name :confirm
                                                    :type :confirm
                                                    :default false
                                                    :message (str "Updating Stripe API version to " version ".\n"
                                                                  "Make sure you are handling Capabilities (https://stripe.com/docs/connect/capabilities-overview)\n"
                                                                  "and identity verification (https://stripe.com/docs/connect/identity-verification)\n"
                                                                  "in your front end as specified by your new API version.\n\n"
                                                                  "Confirm?")}]))))]
    (when-not confirm
      (exception/throw! :command/not-confirmed "Stripe API version update not confirmed. Exiting.")) ))

(defn update-version [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [version force]} params

         version (or version
                     (:version (<! (io-util/prompt [{:name :version
                                                     :choices ["2019-09-09" "2019-02-19"]
                                                     :type :list
                                                     :message "Stripe API version"}]))))

         _ (ensure-valid-version! version)
         _ (confirm! force version)

         query-params {:marketplace marketplace}
         body-params {:version version}

         res (try
               (<? (do-post api-client "/stripe/update-version" query-params body-params))
               (catch js/Error e
                 (throw
                  (api.client/retype-ex e :stripe/update-api-version-failed))))]

     (io-util/ppd (str "Stripe API version successfully changed to " (-> res :data))))))

