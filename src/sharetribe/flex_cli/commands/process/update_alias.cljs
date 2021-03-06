(ns sharetribe.flex-cli.commands.process.update-alias
  (:require [clojure.core.async :as async :refer [go <!]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.api.client :refer [do-post]]
            [sharetribe.flex-cli.io-util :as io-util]))

(declare update-alias)

(def cmd {:name "update-alias"
          :handler #'update-alias
          :desc "update an existing alias"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "PROCESS_NAME"
                  :missing "--process is required"
                  :desc "process name, see process list for available names"}
                 {:id :version
                  :long-opt "--version"
                  :required "VERSION"
                  :missing "--version is required"
                  :desc "process version the alias should point to"}
                 {:id :alias
                  :long-opt "--alias"
                  :required "ALIAS"
                  :missing "--alias is required"
                  :desc "alias name, e.g. release-1"}]})

(defn update-alias [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name version alias]} params
         query-params {:marketplace marketplace}
         body-params {:name (keyword process-name)

                      ;; TODO: Use spec or tools-cli for parameter validation and coercion
                      :version (js/parseInt version)

                      :alias (keyword alias)}
         res (<? (do-post api-client "/aliases/update-alias" query-params body-params))]
     (io-util/ppd [:span
                   "Alias "
                   (-> res :data :processAlias/alias io-util/namespaced-str)
                   " successfully updated to point to version "
                   (-> res :data :processAlias/version str)
                   "."]))))
