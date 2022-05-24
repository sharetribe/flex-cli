(ns sharetribe.flex-cli.commands.assets
  "Commands for managing assets."
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.set :as set]
            [clojure.string :as str]
            [form-data :as FormData]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-multipart-post do-get]]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.flex-cli.io-util :as io-util]))

(declare pull-assets)
(declare push-assets)

(def cmd {:name "assets"
          :sub-cmds [{:name "pull"
                      :handler #'pull-assets
                      :desc "Pull assets"
                      ;; Command only for internal use
                      :hidden? true
                      :opts [{:id :path
                              :long-opt "--path"
                              :desc "Path to directory where the assets will be stored"
                              :required "PATH"
                              :missing "--path is required"}
                             {:id :prune
                              :long-opt "--prune"
                              :desc "Delete local files that are no longer present as assets on Flex"}]}
                     {:name "push"
                      :handler #'push-assets
                      :desc "Push assets"
                      ;; Command only for internal use
                      :hidden? true
                      :opts [{:id :path
                              :long-opt "--path"
                              :desc "Path to directory with assets"
                              :required "PATH"
                              :missing "--path is required"}
                             {:id :prune
                              :long-opt "--prune"
                              :desc "Delete assets in Flex that are no longer present locally"}]}]})

(defn- ensure-asset-dir! [path]
  (when-not (io-util/dir? path)
    (exception/throw! :command/invalid-args
                      {:command :upsert
                       :errors ["--path should be an asset directory"]})))

(defn- to-multipart-form-data
  [{:keys [current-version assets]}]
  (:form-data
   (reduce
    (fn [{:keys [form-data i]} {:keys [path op data-raw filename file-stream]}]
      {:form-data (case op
                    :delete
                    (doto form-data
                      (.append (str "path-" i) path)
                      (.append (str "op-" i) "delete"))

                    (:upsert nil)
                    (doto form-data
                      (.append (str "path-" i) path)
                      (.append (str "op-" i) "upsert")
                      (.append (str "data-raw-" i) data-raw filename)))
       :i (inc i)})
    {:form-data (doto (FormData.)
                  (.append "current-version" current-version))
     :i 0}
    assets)))

(defn pull-assets [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [path prune]} params

         _ (ensure-asset-dir! path)

         {old-version :version :as asset-meta} (io-util/read-asset-meta path)

         query-params {:marketplace marketplace
                       :version-alias "latest"}

         res (try
               (<? (do-get api-client "/assets/pull" query-params))
               (catch js/Error e
                 (throw e)))

         new-version (-> res :meta :aliased-version)
         local-assets (when prune (io-util/list-assets path))
         deleted-paths (when prune
                         (set/difference (into #{} (map :path local-assets))
                                         (into #{} (map :path (:data res)))))
         updated? (not= old-version new-version)]

     (if (or updated? (seq deleted-paths))
       (do
         (when updated?
           (io-util/write-assets path (:data res)))
         (when (seq deleted-paths)
           (io-util/remove-assets path deleted-paths))
         (io-util/write-asset-meta path (assoc asset-meta
                                               :version new-version
                                               :assets (into []
                                                             (map #(dissoc % :data-raw))
                                                             (:data res))))
         (io-util/ppd [:span
                       "Version " new-version
                       " successfully pulled."]))
       (io-util/ppd [:span
                     "Assets are up to date."])))))

(defn- validate-assets!
  [assets]
  (doseq [asset assets]
    (let [path-lower (-> asset
                         :path
                         str/lower-case)]
      (when
        (str/ends-with? path-lower ".json")
        (try
          (.parse js/JSON (:data-raw asset))
          (catch js/Error e
            (throw (ex-info (str "File '"
                                 (:full-path asset)
                                 "' does not contain valid JSON:\n"
                                 e)
                            {}))))))))

(defn push-assets [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [path prune]} params

         _ (ensure-asset-dir! path)

         {:keys [version assets] :as asset-meta} (io-util/read-asset-meta path)
         local-assets (io-util/read-assets path)

         _ (validate-assets! local-assets)

         delete-assets (when prune
                         (->> (set/difference (into #{} (map :path assets))
                                              (into #{} (map :path local-assets)))
                              (map (fn [path]
                                     {:path path
                                      :op :delete}))))

         query-params {:marketplace marketplace}
         body-params (to-multipart-form-data
                      {:current-version (if version version "nil") ;; stringify nil as initial version
                       :assets (concat local-assets delete-assets)})


         res (try
               (<? (do-multipart-post api-client "/assets/push" query-params body-params))
               (catch js/Error e
                 (throw e)))

         new-version (-> res :data :version)]

     (if new-version
       (do
         (io-util/write-asset-meta path (assoc asset-meta
                                               :version new-version
                                               :assets (-> res :data :asset-meta)))
         (io-util/ppd [:span
                       "New version " new-version
                       " successfully created."]))
       (io-util/ppd [:span
                     "Assets are up to date."])))))

(comment

  (sharetribe.flex-cli.core/main-dev-str "help assets pull")
  (sharetribe.flex-cli.core/main-dev-str "help assets push")

  ;; set own path to asset dir or add test asset data to repo
  (sharetribe.flex-cli.core/main-dev-str "assets pull -m bike-soil --path tmp/assets")
  (sharetribe.flex-cli.core/main-dev-str "assets pull -m bike-soil --path tmp/assets --prune")

  (sharetribe.flex-cli.core/main-dev-str "assets push -m bike-soil --path tmp/assets")
  (sharetribe.flex-cli.core/main-dev-str "assets push -m bike-soil --path tmp/assets --prune")

  )
