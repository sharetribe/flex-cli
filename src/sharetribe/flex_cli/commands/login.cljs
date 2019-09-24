(ns sharetribe.flex-cli.commands.login
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.api.client :refer [new-client do-get]]
            [sharetribe.flex-cli.credential-store :as credential-store]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.exception :as exception]))

(defn login [params ctx]
  (go-try
   (let [{:keys [marketplace]} ctx
         {:keys [api-key]} (<! (io-util/prompt [{:name :api-key
                                                 :type :password
                                                 :message "API key"}]))
         api-client (new-client api-key)
         {:keys [data]} (<? (do-get api-client "/current_admin/show" nil))]

     (credential-store/set-api-key api-key)
     (io-util/ppd [:span "Hello " (:admin/email data) "!"]))))
