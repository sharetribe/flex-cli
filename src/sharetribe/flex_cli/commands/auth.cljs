(ns sharetribe.flex-cli.commands.auth
  (:require [sharetribe.flex-cli.credential-store :as credential-store])
  )

(defn login [opts]
  ;; TODO Build generic parameter validation
  (assert (:api-key opts) "Missing mandatory option: api-key")
  (assert (:marketplace opts) "Missing mandatory option: marketplace")

  ;; TODO Validate API key (by doing a request to API)?

  (credential-store/set-api-key
   ;; TODO Coerse parameters
   (keyword (:marketplace opts))
   (:api-key opts)
   {:on-success #(println "API stored.")
    :on-error #(println "Failed to store the API key. This may happen
    if you are using an environment where a credential manager is not
    available, e.g. a headless environment without desktop
    capabilities.") ;; TODO Add note about possiblity to use API_KEY environment variable, once it's implemented.
    }))

(defn logout [opts]
  (credential-store/delete-api-key
   ;; TODO Coerse parameters
   (keyword (:marketplace opts))
   {:on-success #(println "API removed.")
    :on-error #(println "Failed to store the API key. This may happen
    if you are using an environment where a credential manager is not
    available, e.g. a headless environment without desktop
    capabilities.") ;; TODO Add note about possiblity to use API_KEY environment variable, once it's implemented.
    })
  )

(comment
  (sharetribe.flex-cli.core/main-dev-str "login --api-key=asdfasdfasdf --marketplace=bike-soil")
  (sharetribe.flex-cli.core/main-dev-str "logout --marketplace=bike-soil"))
