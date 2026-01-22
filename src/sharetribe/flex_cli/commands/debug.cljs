(ns sharetribe.flex-cli.commands.debug 
  (:require
   [sharetribe.flex-cli.config :as config]
   [sharetribe.flex-cli.credential-store :refer [get-api-key]]))

(defn mask-last-4 [s]
  (str "..." (subs s (- (count s) 4))))

(defn debug [_opts _ctx]
  (let [api-key (if-let [k (get-api-key)]
                  (mask-last-4 k)
                  "No API key set")]
    (println {:api-key api-key
              :conf-map (config/conf-map)})))

(def cmd
  {:name "debug"
   :desc "display debug info"
   :no-api-key? true
   :no-marketplace? true
   :hidden? true
   :handler debug})

(comment

  (sharetribe.flex-cli.core/main-dev-str "debug")

  )
