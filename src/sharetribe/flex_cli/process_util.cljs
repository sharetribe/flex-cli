(ns sharetribe.flex-cli.process-util
  (:require [clojure.set :as set]
            [chalk]
            [form-data :as FormData]
            [sharetribe.flex-cli.io-util :as io-util]
            [sharetribe.flex-cli.api.client :as api.client]
            [sharetribe.flex-cli.exception :as exception]))

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

(defn- template-error-report [total index error]
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

(defn- format-invalid-templates-error [data]
  (let [errors (-> (api.client/api-error data) :details :render-errors)
        total-errors (count errors)]
    (concat
     [:span
      "The process contains invalid email templates. Found "
      (str total-errors) " invalid " (if (= 1 total-errors) "template" "templates") "."
      :line]
     (map-indexed (partial template-error-report total-errors) errors))))

(defn format-invalid-template-error [data]
  (let [error (-> (api.client/api-error data) :details :render-error)]
    (template-error-report 1 0 error)))

(defn- format-process-exists-error [data]
  [:span error-arrow
   " Process already exists: "
   (-> data api.client/api-error :details :process-name name)])

(defmethod exception/format-exception :process.util/new-process-api-call-failed [_ _ data]
  (case (:code (api.client/api-error data))
    :invalid-templates (format-invalid-templates-error data)
    :tx-process-already-exists (format-process-exists-error data)
    (api.client/default-error-format data)))

(defmethod exception/format-exception :process.util/missing-templates [_ _ {:keys [notifications]}]
  [:span
   (map (fn [{:keys [name template]}]
          [:span error-arrow
           " Template " (.bold chalk (clojure.core/name template))
           " not found for notification " (.bold chalk (clojure.core/name name))
           :line])
        notifications)])

(defn ensure-process-dir! [path]
  (when-not (io-util/process-dir? path)
    (exception/throw! :command/invalid-args
                      {:command :push
                       :errors ["--path should be a process directory"]})))

(defn ensure-templates! [tx-process templates]
  (let [process-tmpl-names (->> tx-process :notifications (map :template) set)
        template-names (set (map :name templates))
        extra-tmpls (set/difference template-names process-tmpl-names)
        missing-templates (remove (fn [n]
                                    (contains? template-names (:template n)))
                                  (:notifications tx-process))]
    (doseq [t extra-tmpls]
      (io-util/ppd-err [:span
                        (.bold.yellow chalk "Warning: ")
                        "template exists but is not used in the process: "
                        (.bold chalk (name t))]))
    (when (seq missing-templates)
      (exception/throw! :process.util/missing-templates {:notifications missing-templates}))))

(defn to-multipart-form-data [{:keys [name definition templates]}]
  (reduce
   (fn [form-data tmpl]
     (doto form-data
       (.append (str "template-html-" (clojure.core/name (:name tmpl))) (:html tmpl))
       (.append (str "template-subject-" (clojure.core/name (:name tmpl))) (:subject tmpl))))
   (doto (FormData.)
     (.append "name" name)
     (.append "definition" definition))
   templates))
