(ns sharetribe.flex-cli.commands.assets
  "Commands for managing assets."
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.set :as set]
            [clojure.string :as str]
            [chalk]
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
                             {:id :version
                              :desc "Version of the assets that are pulled"
                              :long-opt "--version"
                              :required "VERSION"}
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

(defn- ensure-asset-dir-and-throw! [path]
  (when-not (io-util/dir? path)
    (exception/throw! :command/invalid-args
                      {:command :upsert
                       :errors ["--path should be an asset directory"]})))

(defn- ensure-asset-dir-and-make! [path]
  (when-not (io-util/dir? path)
    (io-util/mkdirp path)))

(defn- stage-asset
  [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [filename path data-raw] :as asset} params
         file-name (or filename path)]
     (when-not data-raw
       (exception/throw! :assets/stage-api-call-failed
                         {:asset-path path}))

     (let [form-data (doto (js/FormData.)
                       (.append "file" data-raw file-name))]
       (try
         (let [res (<? (do-multipart-post api-client "/assets/stage"
                                          {:marketplace marketplace}
                                          form-data))
               staging-id (-> res :data :staging-id)]
           (assoc asset :staging-id staging-id))
         (catch js/Error e
           (if (= :api/error (exception/type e))
             (throw (exception/exception :assets/stage-api-call-failed
                                         {:asset-path path
                                          :api-error-data (exception/data e)}))
             (throw e))))))))

(defn- to-multipart-form-data
  [{:keys [current-version assets]}]
  (:form-data
   (reduce
    (fn [{:keys [form-data i]} {:keys [path op data-raw filename staging-id]}]
      {:form-data (case op
                    :delete
                    (doto form-data
                      (.append (str "path-" i) path)
                      (.append (str "op-" i) "delete"))

                    (:upsert nil)
                    (let [form-data (doto form-data
                                      (.append (str "path-" i) path)
                                      (.append (str "op-" i) "upsert"))]
                      (when staging-id
                        (.append form-data (str "staging-id-" i) (str staging-id)))
                      (when (and (not staging-id) data-raw)
                        (let [fname (or filename path)]
                          (.append form-data (str "data-raw-" i) (js/Blob. (array data-raw)) fname)))
                      form-data))
       :i (inc i)})
    {:form-data (doto (js/FormData.)
                  (.append "current-version" current-version))
     :i 0}
    assets)))

(defn pull-assets [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [path prune version]} params

         _ (ensure-asset-dir-and-make! path)

         {old-version :version :as asset-meta} (io-util/read-asset-meta path)


         version-params (if version
                          {:version version}
                          {:version-alias "latest"})

         query-params (merge
                       version-params
                       {:marketplace marketplace})

         res (try
               (<? (do-get api-client "/assets/pull" query-params))
               (catch js/Error e
                 (throw e)))

         new-version (or
                      (-> res :meta :version)
                      (-> res :meta :aliased-version))
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

(defn filter-assets-to-upload
  [existing-meta local-assets]
  (let [existing-meta (or existing-meta [])
        hash-by-path (into {} (map (juxt :path :content-hash)) existing-meta)
        changed? (fn [{:keys [path content-hash]}]
                   (let [stored-hash (get hash-by-path path)]
                     ;; Assets without stored metadata are treated as changed.
                     (or (nil? stored-hash)
                         (not= stored-hash content-hash))))]
    (filter changed? local-assets)))

(defn push-assets [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [path prune]} params

         _ (ensure-asset-dir-and-throw! path)

         {:keys [version assets] :as asset-meta} (io-util/read-asset-meta path)
         local-assets (io-util/read-assets path)
         changed-assets (into [] (filter-assets-to-upload assets local-assets))
         json-asset? (fn [{:keys [path]}]
                       (let [path-lower (str/lower-case path)]
                         (str/ends-with? path-lower ".json")))
         stageable-assets (into [] (remove json-asset? changed-assets))

         _ (validate-assets! local-assets)

         delete-assets (when prune
                         (->> (set/difference (into #{} (map :path (or assets [])))
                                              (into #{} (map :path local-assets)))
                              (map (fn [path]
                                     {:path path
                                      :op :delete}))))

         _ (when (seq changed-assets)
             (let [paths (str/join ", " (map :path changed-assets))]
               (io-util/log (.green chalk (str "Uploading changed assets: " paths)))))
         no-ops? (and (empty? changed-assets)
                      (empty? delete-assets))]

     (if no-ops?
       (io-util/ppd [:span "Assets are up to date."])
       (let [query-params {:marketplace marketplace}
             _ (when (seq stageable-assets)
                 (io-util/log (.green chalk (str "Staging assets: "
                                                 (str/join ", " (map :path stageable-assets))))))
             staged-assets (when (seq stageable-assets)
                             (loop [acc []
                                    remaining stageable-assets]
                               (if-let [asset (first remaining)]
                                 (let [staged (<? (stage-asset asset ctx))]
                                   (recur (conj acc staged) (rest remaining)))
                                 acc)))
             staged-by-path (into {} (map (juxt :path :staging-id)) (or staged-assets []))
             upsert-ops (into []
                              (map (fn [asset]
                                     (let [path (:path asset)
                                           staging-id (get staged-by-path path)]
                                       (cond-> {:path path
                                                :op :upsert}
                                         staging-id (assoc :staging-id staging-id)
                                         (and (not staging-id) (:data-raw asset))
                                         (assoc :data-raw (:data-raw asset)
                                                :filename (:filename asset)
                                                :full-path (:full-path asset))))))
                              changed-assets)
             body-params (to-multipart-form-data
                          {:current-version (if version version "nil") ;; stringify nil as initial version
                           :assets (concat upsert-ops (or delete-assets []))})

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
                         "Assets are up to date."])))))))

(defmethod exception/format-exception :assets/stage-api-call-failed [_ _ {:keys [asset-path api-error-data]}]
  (let [error (when api-error-data (api.client/api-error api-error-data))
        detail (or (:detail error) (:message error))
        suggestion "Fix the file and rerun assets push to retry staging."
        bold-path (.bold chalk (or asset-path "<unknown>"))]
    (if (= :asset-invalid-content (:code error))
      [:span
       (.bold.red chalk "Failed to stage image ") bold-path ": "
       (or detail "The file is missing or uses an unsupported format.")
       :line suggestion]
      (let [fallback (cond
                       api-error-data (api.client/default-error-format api-error-data)
                       :else [:span "Staging failed due to an unexpected error."])
            fallback-span (if (and (vector? fallback) (= :span (first fallback)))
                            fallback
                            [:span fallback])]
        (into (vec fallback-span)
              [:line
               "While staging image " bold-path "."
               :line suggestion])))))

(comment

  (sharetribe.flex-cli.core/main-dev-str "help assets pull")
  (sharetribe.flex-cli.core/main-dev-str "help assets push")

  ;; set own path to asset dir or add test asset data to repo
  (sharetribe.flex-cli.core/main-dev-str "assets pull -m bike-soil --path tmp/assets")
  (sharetribe.flex-cli.core/main-dev-str "assets pull -m bike-soil --path tmp/assets --prune")

  (sharetribe.flex-cli.core/main-dev-str "assets push -m bike-soil --path tmp/assets")
  (sharetribe.flex-cli.core/main-dev-str "assets push -m bike-soil --path tmp/assets --prune")

  )
