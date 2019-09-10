(ns sharetribe.flex-cli.commands.process.push
  (:require [clojure.set :as set]
            [clojure.core.async :as async :refer [go <!]]
            [chalk]
            [sharetribe.flex-cli.async-util :refer [<? go-try]]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.api.client :as api.client :refer [do-post]]
            [sharetribe.flex-cli.exception :as exception]
            [sharetribe.tempelhof.tx-process :as tx-process]))

(def error-arrow (.bold.red chalk "\u203A"))

(defn- error-report
  "Given an error description map, format it as an error report string
  (a multi line string)."
  [total index error]
  (let [{:keys [loc msg]} error
        {:keys [row col]} loc
        header (if loc
                 (str (inc index) "/" total
                      " [at line " row ", column " col "]"
                      ":\n")
                 (str (inc index) "/" total
                      ":\n"))]
    (str "\n" error-arrow " " header msg "\n")))

(defn template-error-report [total index error]
  (let [{:keys [template-name reason evidence line column template-part]} error]
    (error-report
     total
     index
     {:msg (str "Error in " (.bold chalk (name template-name))
                " template " (name template-part)
                ". Reason: " reason
                "\n\n" evidence)
      :loc {:row line
            :col column}})))

(defn format-invalid-templates-error [data]
  (let [errors (-> (api.client/api-error data) :details :render-errors)
        total-errors (count errors)]
    (concat
     [:span
      "The process contains invalid email templates. Found "
      (str total-errors) " invalid " (if (= 1 total-errors) "template" "templates") "."
      :line]
     (map-indexed (partial template-error-report total-errors) errors))))

(defmethod exception/format-exception :process.push/api-call-failed [_ _ data]
  (case (:code (api.client/api-error data))
    :invalid-templates (format-invalid-templates-error data)
    (api.client/default-error-format data)))

(defmethod exception/format-exception :process.push/missing-templates [_ _ {:keys [notifications]}]
  [:span
   (map (fn [{:keys [name template]}]
          [:span (.bold.red chalk "Error: ")
           "template " (.bold chalk (clojure.core/name template))
           " not found for notification " (.bold chalk (clojure.core/name name))
           :line])
        notifications)])

(declare push-process)

(def cmd {:name "push"
          :handler #'push-process
          :desc "push a process file to the remote"
          :opts [{:id :process-name
                  :long-opt "--process"
                  :required "PROCESS_NAME"
                  :missing "--process is required"}
                 {:id :path
                  :long-opt "--path"
                  :required "LOCAL_PROCESS_DIR"
                  :missing "--path is required"}]})

(defn- ensure-process-dir! [path]
  (when-not (io-util/process-dir? path)
    (exception/throw! :command/invalid-args
                      {:command :push
                       :errors ["--path should be a process directory"]})))

(defn- ensure-templates! [tx-process templates]
  (let [process-tmpl-names (->> tx-process :notifications (map :template) set)
        template-names (set (map :name templates))
        extra-tmpls (set/difference template-names process-tmpl-names)
        missing-templates (remove (fn [n]
                                    (template-names (:template n)))
                                  (:notifications tx-process))]
    (doseq [t extra-tmpls]
      (io-util/ppd-err [:span
                        (.bold.yellow chalk "Warning: ")
                        "template exists but is not used in the process: "
                        (.bold chalk (name t))]))
    (when (seq missing-templates)
      (exception/throw! :process.push/missing-templates {:notifications missing-templates}))))

(defn push-process [params ctx]
  (go-try
   (let [{:keys [api-client marketplace]} ctx
         {:keys [process-name path]} params

         _ (ensure-process-dir! path)

         process-str (io-util/load-file (io-util/process-file-path path))
         templates (io-util/read-templates path)

         tx-process (tx-process/parse-tx-process-string process-str)
         _ (ensure-templates! tx-process templates)

         query-params {:marketplace marketplace}
         body-params {:name (keyword process-name)
                      :definition process-str
                      :templates templates}

         res (try
               (<? (do-post api-client "/processes/create-version" query-params body-params))
               (catch js/Error e
                 (throw
                  (api.client/retype-ex e :process.push/api-call-failed))))]

     (if (= :no-changes (-> res :meta :result))
       (io-util/ppd [:span "No changes"])
       (io-util/ppd [:span
                     "Version " (-> res :data :process/version str)
                     " successfully saved for process " (-> res :data :process/name name)])))))

(comment
  (sharetribe.flex-cli.core/main-dev-str "process push -m bike-soil --process preauth-with-nightly-booking --path test-process/process.edn")
  )
