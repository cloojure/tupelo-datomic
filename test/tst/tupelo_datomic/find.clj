(ns tst.tupelo-datomic.find
  (:use tupelo.test)
 ;(:refer-clojure :exclude [clojure.core find] )
  (:require [datomic.api          :as d]
            [tupelo-datomic.core  :as td]
            [tupelo.schema        :as ts]
            [tupelo.core :as t]
            [schema.core          :as s]))
(t/refer-tupelo)

(def datomic-uri "datomic:mem://tupelo")          ; the URI for our test db
(def ^:dynamic *conn*)

; helper function
(s/defn get-people :- ts/Set
  "Returns a set of entity maps for all entities with the :person/name attribute"
  []
  (let [eids  (td/query-attr  :let    [$ (d/db *conn*)]
                              :find   [?eid]  ; <- could also use Datomic Pull API
                              :where  [ [?eid :person/name] ] ) ]
    (set  (for [eid eids]
            (td/entity-map (d/db *conn*) eid)))))

(defn add-james-data []
  ; Create some new attributes. Required args are the attribute name (an optionally namespaced
  ; keyword) and the attribute type (full listing at http://docs.datomic.com/schema.html). We wrap
  ; the new attribute definitions in a transaction and immediately commit them into the DB.
  (td/transact *conn* ;   required              required              zero-or-more
               ;  <attr name>         <attr value type>       <optional specs ...>
               (td/new-attribute   :person/name         :db.type/string         :db.unique/value)      ; each name      is unique
               (td/new-attribute   :person/secret-id    :db.type/long           :db.unique/value)      ; each secret-id is unique
               (td/new-attribute   :weapon/type         :db.type/ref            :db.cardinality/many)  ; one may have many weapons
               (td/new-attribute   :location            :db.type/string)     ; all default values
               (td/new-attribute   :favorite-weapon     :db.type/keyword ))  ; all default values

  ; Create some "enum" values. These are degenerate entities that serve the same purpose as an
  ; enumerated value in Java (these entities will never have any attributes). Again, we
  ; wrap our new enum values in a transaction and commit them into the DB.
  (td/transact *conn*
               (td/new-enum :weapon/gun)
               (td/new-enum :weapon/knife)
               (td/new-enum :weapon/guile)
               (td/new-enum :weapon/wit))

  ; Create some antagonists and load them into the db.  We can specify some of the attribute-value
  ; pairs at the time of creation, and add others later. Note that whenever we are adding multiple
  ; values for an attribute in a single step (e.g. :weapon/type), we must wrap all of the values
  ; in a set. Note that the set implies there can never be duplicate weapons for any one person.
  ; As before, we immediately commit the new entities into the DB.
  (td/transact *conn*
               (td/new-entity { :person/name "James Bond" :location "London"     :weapon/type #{ :weapon/gun :weapon/wit   } } )
               (td/new-entity { :person/name "M"          :location "London"     :weapon/type #{ :weapon/gun :weapon/guile } } )
               (td/new-entity { :person/name "Dr No"      :location "Caribbean"  :weapon/type    :weapon/gun                 } ))

  ; Update the database with more weapons.  If we overwrite some items that are already present
  ; (e.g. :weapon/gun) it is idempotent (no duplicates are allowed).  The first arg to td/update
  ; is an EntitySpec (either EntityId or LookupRef) and determines the Entity that is updated.
  (td/transact *conn*
               (td/update [:person/name "James Bond"]
                          { :weapon/type #{ :weapon/gun :weapon/knife }
                           :person/secret-id 007 } )   ; Note that James has a secret-id but no one else does

               (td/update [:person/name "Dr No"] ; update using LookupRef
                          { :weapon/type #{ :weapon/gun :weapon/knife :weapon/guile }} )))

(defn verify-james-data []
  ; Verify current status. Notice there are no duplicate weapons.
  (is (= (get-people)
         #{ {:person/name "James Bond" :location "London"    :weapon/type #{              :weapon/wit :weapon/knife :weapon/gun} :person/secret-id 7 }
            {:person/name "M"          :location "London"    :weapon/type #{:weapon/guile                           :weapon/gun}}
            {:person/name "Dr No"      :location "Caribbean" :weapon/type #{:weapon/guile             :weapon/knife :weapon/gun}}} )))

;---------------------------------------------------------------------------------------------------
; clojure.test fixture: setup & teardown for each test
(use-fixtures :each
  (fn setup-execute-teardown            ; perform setup, execution, & teardown for each test
    [tst-fn]
; setup ----------------------------------------------------------
    (d/create-database datomic-uri)             ; create the DB
    (binding [*conn* (d/connect datomic-uri) ]  ; create & save a connection to the db
      (add-james-data)
      (verify-james-data)
; execute --------------------------------------------------------
      (try
        (tst-fn)
; teardown -------------------------------------------------------
        (finally
          (d/delete-database datomic-uri))))))

;---------------------------------------------------------------------------------------------------
; The macro test must be in the same source file as the macro definition or it won't expand properly
;(deftest t-macro
;  (is (td/t-query)))

(deftest t-find
  (let [james-eid  (td/query-value  :let    [$ (d/db *conn*)]
                                    :find   [?eid]
                                    :where  [[?eid :person/name "James Bond"]]) ]
    (let [eids  (td/find  :let    [$ (d/db *conn*)]
                          :find   [?eid]
                          :where  {:db/id ?eid :person/name "James Bond"  :weapon/type :weapon/wit}
                          {:db/id ?eid :location "London"} )
          ]
      (is (= james-eid (only (only eids)))))
    (let [eids  (td/find  :let    [$ (d/db *conn*)]
                          :find   [?eid]
                          :where  {:db/id ?eid :person/name "James Bond"  :weapon/type :weapon/wit}
                                  {:db/id ?eid :location "Caribbean"} )
          ]
      (is (= #{} eids)))
  )
)
