(ns tst.tupelo-datomic.functionality
  (:require [tupelo-datomic.core  :as td]
            [tupelo.schema        :as ts]
            [datomic.api          :as d]
            [schema.core          :as s]
            [tupelo.core :as t]
  )
  (:use clojure.test tupelo.core)
  )

(spyx *clojure-version*)
(set! *warn-on-reflection* false)
(set! *print-length* nil)
(s/set-fn-validation! true)             ; enable Prismatic Schema type definitions (#todo add to Schema docs)

(def datomic-uri "datomic:mem://tst.bond")      ; the URI for our test db
(def ^:dynamic *conn*)                  ; dynamic var to hold the db connection

;---------------------------------------------------------------------------------------------------
; clojure.test fixture: setup & teardown for each test
(use-fixtures :each
  (fn setup-execute-teardown            ; perform setup, execution, & teardown for each test
    [tst-fn]
; setup ----------------------------------------------------------
    (d/create-database datomic-uri)             ; create the DB
    (try
      (binding [*conn* (d/connect datomic-uri) ]  ; create & save a connection to the db
; execute --------------------------------------------------------
        (tst-fn))
; teardown -------------------------------------------------------
      (finally
        (d/delete-database datomic-uri)))))

;---------------------------------------------------------------------------------------------------
; Convenience function to keep syntax a bit more concise
(defn live-db [] (d/db *conn*))

; helper function
(s/defn get-people :- ts/Set
  "Returns a set of entity maps for all entities with the :person/name attribute"
  [db-val :- s/Any]
  (let [eids  (td/find-attr   :let    [$ db-val]
                              :find   [?eid]  ; <- could also use Datomic Pull API
                              :where  {:db/id ?eid :person/name _} ) ]
    (set  (for [eid eids]
            (td/entity-map db-val eid)))))

;---------------------------------------------------------------------------------------------------
; #todo
; demo to show changing an attribute (string -> integer, cardinality/one -> many, deleting an attribute
; example of retracting 1 item from a normal (cardinality/one) attribute & a cardinality/many attribute

(deftest t-james-bond

  ; Attributes can be "namespaced" in a variety of ways
  (td/transact *conn* 
    (td/new-attribute   :person/name :db.type/string)
    (td/new-attribute   :person.name :db.type/string)
    (td/new-attribute   :person-name :db.type/string))

  ; Re-creating attr is OK
  (td/transact *conn* 
    (td/new-attribute   :person/name :db.type/string))

  ; Cannot change attr type (e.g. string -> long)
  (let [result    (td/transact *conn* 
                    (td/new-attribute   :person.name :db.type/long)) ]
    ; (newline) (spyx (class result))
    ; (newline) (spyx result)
    (is (re-find #":db.error/invalid-install-attribute" (.toString result))))

  (td/transact *conn* 
    (td/new-entity { :person/name "James Bond" :person.name "Jimmy" :person-name "Jack" } ))

  (newline)
  (is (= (spyx (get-people (live-db)))
          #{ { :person/name "James Bond" :person.name "Jimmy" :person-name "Jack" } } ))

; #todo verify that datomic/q returns TupleSets (i.e. no duplicate tuples in result)
)
