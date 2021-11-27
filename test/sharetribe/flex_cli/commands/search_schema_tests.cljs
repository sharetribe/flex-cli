(ns sharetribe.flex-cli.commands.search-schema-tests
  (:require
   [clojure.test :refer [deftest is testing]]
   [sharetribe.flex-cli.api.client :refer [do-get]]
   [sharetribe.flex-cli.commands.search-schema :as search-schema]
   [sharetribe.flex-cli.io-util :as io-util]
   [spy.assert :as assert]       ;; the core library with functions returning booleans
   [spy.core :as spy]  ;; assertions wrapping clojure.test/is
   [spy.test]               ;; assert-expr definitions for clojure.test
   ))

(deftest failing
  (is (= "bye!" "hello!")))

#_(with-redefs
 [do-get (spy/mock (fn [_client _path _query]
                     ; mock API response
                     {:data [{:dataSchema/key :hobbies,
                              :dataSchema/doc "User hobbies.",
                              :dataSchema/of :dataSchema.of/userProfile,
                              :dataSchema/scope :dataSchema.scope/public,
                              :dataSchema/valueType :dataSchema.type/enum,
                              :dataSchema/cardinality :dataSchema.cardinality/many}
                             {:dataSchema/key :promoted,
                              :dataSchema/doc "Listing is promoted?",
                              :dataSchema/of :dataSchema.of/listing,
                              :dataSchema/scope :dataSchema.scope/metadata,
                              :dataSchema/valueType :dataSchema.type/boolean,
                              :dataSchema/cardinality :dataSchema.cardinality/one}
                             {:dataSchema/key :amenities,
                              :dataSchema/doc "The amenities that this bike comes with, such as a pool, a shower or a refridgerator. Makes total sense.",
                              :dataSchema/of :dataSchema.of/listing,
                              :dataSchema/scope :dataSchema.scope/public,
                              :dataSchema/valueType :dataSchema.type/enum,
                              :dataSchema/cardinality :dataSchema.cardinality/many}
                             {:dataSchema/key :category,
                              :dataSchema/doc "Listing category.",
                              :dataSchema/of :dataSchema.of/listing,
                              :dataSchema/scope :dataSchema.scope/public,
                              :dataSchema/valueType :dataSchema.type/enum,
                              :dataSchema/cardinality :dataSchema.cardinality/one}
                             {:dataSchema/key :gears,
                              :dataSchema/doc "How many gears the bike has.",
                              :dataSchema/of :dataSchema.of/listing,
                              :dataSchema/scope :dataSchema.scope/public,
                              :dataSchema/valueType :dataSchema.type/long,
                              :dataSchema/cardinality :dataSchema.cardinality/one}
                             {:dataSchema/key :breaks,
                              :dataSchema/defaultValue true,
                              :dataSchema/doc "Listed bike has breaks or not.",
                              :dataSchema/of :dataSchema.of/listing,
                              :dataSchema/scope :dataSchema.scope/public,
                              :dataSchema/valueType :dataSchema.type/boolean,
                              :dataSchema/cardinality :dataSchema.cardinality/one}
                             {:dataSchema/key :accountType,
                              :dataSchema/doc "Listing's account type.",
                              :dataSchema/of :dataSchema.of/listing,
                              :dataSchema/scope :dataSchema.scope/metadata,
                              :dataSchema/valueType :dataSchema.type/enum,
                              :dataSchema/cardinality :dataSchema.cardinality/one}
                             {:dataSchema/key :subscription,
                              :dataSchema/doc "User subscription.",
                              :dataSchema/of :dataSchema.of/userProfile,
                              :dataSchema/scope :dataSchema.scope/metadata,
                              :dataSchema/valueType :dataSchema.type/enum,
                              :dataSchema/cardinality :dataSchema.cardinality/one}
                             {:dataSchema/key :geometry,
                              :dataSchema/doc "Bike frame geometry.",
                              :dataSchema/of :dataSchema.of/listing,
                              :dataSchema/scope :dataSchema.scope/public,
                              :dataSchema/valueType :dataSchema.type/text,
                              :dataSchema/cardinality :dataSchema.cardinality/one}]}
                     ))]
 (search-schema/list-search-schemas :foo {})
 (assert/called-once? do-get)
 (is (= 3 1))
 )