(ns sharetribe.flex-cli.io-util-test
  (:require
   [chalk]
   [cljs.test :refer-macros [deftest is testing]]
   [clojure.string :as str]
   [sharetribe.flex-cli.io-util :as io-util]))

(defn- get-nth-row-output-values [table n]
  (-> table
      str/trim
      str/split-lines
      (nth (inc n))
      (str/split #" \s+")))

(deftest table-output-string-tests
  (let [table (io-util/construct-table
               [:schema-for :scope :key :type :default-value :doc]
               [{:key "accountType",
                 :doc "Listing's account type.",
                 :scope "metadata",
                 :type "enum",
                 :default-value "",
                 :schema-for "listing"}
                {:key "promoted",
                 :doc "Listing is promoted?",
                 :scope "metadata",
                 :type "boolean",
                 :default-value "",
                 :schema-for "listing"}
                {:key "category", :doc "Listing category.", :scope "public", :type "enum", :default-value "", :schema-for "listing"}
                {:key "gears",
                 :doc "How many gears the bike has.",
                 :scope "public",
                 :type "long",
                 :default-value "",
                 :schema-for "listing"}
                {:key "geometry", :doc "Bike frame geometry.", :scope "public", :type "text", :default-value "", :schema-for "listing"}
                {:key "subscription",
                 :doc "User subscription.",
                 :scope "metadata",
                 :type "enum",
                 :default-value "",
                 :schema-for "userProfile"}
                {:key "hobbies",
                 :doc "User hobbies.",
                 :scope "public",
                 :type "multi-enum",
                 :default-value "",
                 :schema-for "userProfile"}])]
    (testing "Includes all headers"
      (is (and (clojure.string/includes? table "Schema for")
               (clojure.string/includes? table "Key")
               (clojure.string/includes? table "Scope")
               (clojure.string/includes? table "Type")
               (clojure.string/includes? table "Default value")
               (clojure.string/includes? table "Doc"))))

    (testing "Includes rows"
      (is (= ["listing" "metadata" "accountType" "enum" "Listing's account type."]
             (get-nth-row-output-values table 0)))

      (is (= ["listing" "metadata" "promoted" "boolean" "Listing is promoted?"]
             (get-nth-row-output-values table 1)))

      (is (= ["listing" "public" "category" "enum" "Listing category."]
             (get-nth-row-output-values table 2)))

      (is (= ["listing" "public" "gears" "long" "How many gears the bike has. "]
             (get-nth-row-output-values table 3)))

      (is (= ["listing" "public" "geometry" "text" "Bike frame geometry."]
             (get-nth-row-output-values table 4)))

      (is (= ["userProfile" "metadata" "subscription" "enum" "User subscription."]
             (get-nth-row-output-values table 5))))))
