(ns tst.tupelo-datomic.quick-start
  (:use tupelo.core tupelo.test)
  (:require
    [clojure.tools.logging :as log]
   ;[datomic.client.api :as d]
    [datomic.api        :as d]
    [tupelo-datomic.core  :as td]
  ))

(def use-datomic-cloud false)

(def datomic-uri "datomic:mem://tst.bond")      ; the URI for our test db

(def cfg {:server-type :cloud
          :region      "us-west-2" ;; e.g. us-east-1
          :system      "aws-datomic-1"
          :query-group "aws-datomic-1"
          :endpoint    "http://entry.aws-datomic-1.us-west-2.datomic.net:8182/"
          :proxy-port  8182})

(def movie-schema [{:db/ident       :movie/title
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "The title of the movie"}

                   {:db/ident       :movie/genre
                    :db/valueType   :db.type/string
                    :db/cardinality :db.cardinality/one
                    :db/doc         "The genre of the movie"}

                   {:db/ident       :movie/release-year
                    :db/valueType   :db.type/long
                    :db/cardinality :db.cardinality/one
                    :db/doc         "The year the movie was released in theaters"}])

(def first-movies [{:movie/title        "The Goonies"
                    :movie/genre        "action/adventure"
                    :movie/release-year 1985}
                   {:movie/title        "Commando"
                    :movie/genre        "action/adventure"
                    :movie/release-year 1985}
                   {:movie/title        "Repo Man"
                    :movie/genre        "punk dystopia"
                    :movie/release-year 1984}])

(def all-titles-q '[:find ?movie-title
                    :where [_ :movie/title ?movie-title]])

;(defmacro comment-if
;  [flag & forms]
;  (if-not flag
;    `(do ~@forms)))
;
;(defn exercise-db [conn]
;  (nl) (println "exercise-db - enter")
;  (nl) (spyx conn)
;  (nl) (spyx (d/transact conn {:tx-data movie-schema}))
;  (nl) (spyx (d/transact conn {:tx-data first-movies}))
;  (nl) (spyx (d/q all-titles-q (spyxx (d/db conn))))
;  (nl) (println "exercise-db - exit") )

(dotest
  ; Attemp to use Datomic Cloud Client API
  ;(comment
  ;  (println :using-cloud)
  ;  (let [client (d/client cfg)
  ;        >>     (spyx client)
  ;        >>     (spyx (d/delete-database client {:db-name "movies"}))
  ;        >>     (spyx (d/create-database client {:db-name "movies"}))
  ;        conn   (d/connect client {:db-name "movies"})]
  ;    (exercise-db conn)
  ;    (spyx (d/delete-database client {:db-name "movies"})))
  ;    (nl) (println :exit-jvm) (java.lang.System/exit 0)
  ;)

  (do
    (log/info :using-local)
    (is (contains? #{true false} (d/delete-database datomic-uri))) ; true on success
    (is= true (d/create-database datomic-uri)) ; true on success
    (let [conn (d/connect datomic-uri)]
      (td/transact conn
        (td/new-attribute :movie/title        :db.type/string :db.cardinality/one) ; "The title of the movie"
        (td/new-attribute :movie/genre        :db.type/string :db.cardinality/one) ; "The genre of the movie"}
        (td/new-attribute :movie/release-year :db.type/long   :db.cardinality/one)) ; "The year the movie was released in theaters"
      (td/transact conn
        (td/new-entity {:movie/title "The Goonies" :movie/genre "action/adventure" :movie/release-year 1985})
        (td/new-entity {:movie/title "Commando" :movie/genre "action/adventure" :movie/release-year 1985})
        (td/new-entity {:movie/title "Repo Man" :movie/genre "punk dystopia" :movie/release-year 1984}))
      (is= #{"Repo Man" "Commando" "The Goonies"}
        (td/query-attr :let    [$ (d/db conn)]
                       :find   [?name]   ; <- could also use Datomic Pull API
                       :where  [[_ :movie/title ?name]])))
    (is= true (d/delete-database datomic-uri)))) ; true on success
