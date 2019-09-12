(ns sharetribe.flex-cli.commands.process.exception-util
  (:require [chalk]
            [sharetribe.flex-cli.api.client :as api.client]))

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
