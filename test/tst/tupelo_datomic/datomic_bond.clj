(ns tst.tupelo.datomic-bond
  (:require [tupelo.datomic   :as td]
            [tupelo.schema    :as ts]
            [tupelo.core      :refer [spy spyx spyxx it-> safe-> matches? grab wild-match? forv submap? only ]]
            [datomic.api      :as d]
            [schema.core      :as s]
  )
  (:use clojure.test)
  (:gen-class))

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
  (let [eids  (td/query-attr  :let    [$ db-val]
                              :find   [?e]  ; <- could also use Datomic Pull API
                              :where  [ [?e :person/name] ] ) ]
    (set  (for [eid eids]
            (td/entity-map db-val eid)))))

;---------------------------------------------------------------------------------------------------
(deftest t-james-bond
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

  ; Verify the antagonists were added to the DB
  (is (= (get-people (live-db))
         #{ {:person/name "James Bond"    :location "London"      :weapon/type #{:weapon/wit    :weapon/gun} }
            {:person/name "M"             :location "London"      :weapon/type #{:weapon/guile  :weapon/gun} }
            {:person/name "Dr No"         :location "Caribbean"   :weapon/type #{:weapon/gun               } } } ))

  ; Using James' name, lookup his EntityId (EID). It is a java.lang.Long that is a unique ID across the whole DB.
  (let [james-eid   (td/query-value  :let    [$ (live-db)]     ; like Clojure let
                                     :find   [?eid]
                                     :where  [ [?eid :person/name "James Bond"] ] )
        _ (s/validate ts/Eid james-eid)  ; verify the expected type
        ; Retrieve James' attr-val pairs as a map. An entity can be referenced either by EID or by a
        ; LookupRef, which is a unique attribute-value pair expressed as a vector.
        james-map   (td/entity-map (live-db) james-eid)                       ; lookup by EID  
        james-map2  (td/entity-map (live-db) [:person/name "James Bond"] )    ; lookup by LookupRef
  ]
    (is (= james-map {:person/name "James Bond" :location "London" :weapon/type #{:weapon/wit :weapon/gun} } ))
    (is (= james-map james-map2 ))

    ; Adding nil values is not allowed, and will generate an Exception.
    (is (thrown? Exception   @(td/transact *conn* 
                                (td/update [:person/name "James Bond"] ; update using a LookupRef
                                  { :weapon/type nil } ))))  ; nil value for :weapon/type causes exception

    ; Update the database with more weapons.  If we overwrite some items that are already present
    ; (e.g. :weapon/gun) it is idempotent (no duplicates are allowed).  The first arg to td/update
    ; is an EntitySpec (either EntityId or LookupRef) and determines the Entity that is updated.
    (td/transact *conn* 
      (td/update james-eid   ; update using EID
          { :weapon/type #{ :weapon/gun :weapon/knife }
            :person/secret-id 007 } )   ; Note that James has a secret-id but no one else does

      (td/update [:person/name "Dr No"] ; update using LookupRef
        { :weapon/type #{ :weapon/gun :weapon/knife :weapon/guile } } )))

  ; Verify current status. Notice there are no duplicate weapons.
  (is (= (get-people (live-db))
    #{ {:person/name "James Bond" :location "London"    :weapon/type #{              :weapon/wit :weapon/knife :weapon/gun} :person/secret-id 7 }
       {:person/name "M"          :location "London"    :weapon/type #{:weapon/guile                           :weapon/gun} }
       {:person/name "Dr No"      :location "Caribbean" :weapon/type #{:weapon/guile             :weapon/knife :weapon/gun} } } ))

  ; Try to add non-existent weapon. This throws since the bogus kw does not match up with an entity.
  (is (thrown? Exception   @(td/transact *conn* 
                              (td/update [:person/name "James Bond"] ; update using a LookupRef
                                { :weapon/type #{ :there.is/no-such-kw } } ))))  ; bogus value for :weapon/type causes exception

  ; For general queries, use td/query.  It returns a set of tuples (a TupleSet).  Duplicate
  ; tuples in the result will be discarded.
  (let [tuple-set   (td/query  :let    [$ (live-db)]
                               :find   [?name ?loc] ; <- shape of output tuples
                               :where  [ [?eid :person/name ?name]      ; pattern-matching rules specify how the variables
                                         [?eid :location    ?loc ] ] )  ;   must be related (implicit join)
  ]
    (s/validate  ts/TupleSet  tuple-set)       ; verify expected type using Prismatic Schema
    (s/validate #{ [s/Any] }  tuple-set)       ; literal definition of TupleSet
    (is (= tuple-set #{ ["Dr No"       "Caribbean"]      ; Even though London is repeated, each tuple is
                        ["James Bond"  "London"]         ; still unique. Otherwise, any duplicate tuples
                        ["M"           "London"] } )))   ; will be discarded since output is a clojure set.

  ; If you want just a single attribute as output, you can get a set of attributes (rather than a set of
  ; tuples) using td/query-attr.  As usual, any duplicate values will be discarded. It is an error if
  ; more than one attribute is present in the :find clause.
  (let [names     (td/query-attr :let    [$ (live-db)]
                                 :find   [?name] ; <- a single attr-val output allows use of td/query-attr
                                 :where  [ [?eid :person/name ?name] ] )
        cities    (td/query-attr :let    [$ (live-db)]
                                 :find   [?loc]  ; <- a single attr-val output allows use of td/query-attr
                                 :where  [ [?eid :location ?loc] ] )
  ]
    (is (= names    #{"Dr No" "James Bond" "M"} ))  ; all names are present, since unique
    (is (= cities   #{"Caribbean" "London"} )))     ; duplicate "London" discarded

  ; If you want just a single tuple as output, you can get it (rather than a set of
  ; tuples) using td/query-entity.  It is an error if more than one tuple is found.
  (let [beachy    (td/query-entity :let    [$    (live-db)     ; assign multiple query variables
                                            ?loc "Caribbean"]  ;   just like clojure 'let' special form
                                   :find   [?eid ?name] ; <- output tuple shape
                                   :where  [ [?eid :person/name ?name      ]
                                             [?eid :location    ?loc] ] )
        busy      (try ; error - both James & M are in London
                    (td/query-entity :let    [$ (live-db)
                                              ?loc "London"]
                                     :find   [?eid ?name] ; <- output tuple shape
                                     :where  [ [?eid :person/name ?name]
                                               [?eid :location    ?loc ] ] )
                    (catch Exception ex (.toString ex)))
  ]
    (is (matches? beachy [_ "Dr No"] ))           ; found 1 match as expected
    (is (re-find #"IllegalStateException" busy)))  ; Exception thrown/caught since 2 people in London


  ; If you know there is (or should be) only a single scalar answer, you can get the scalar value as
  ; output using td/query-value. It is an error if more than one tuple or value is present.
  (let [beachy    (td/query-value  :let    [$    (live-db)     ; assign multiple query variables 
                                            ?loc "Caribbean"]  ; just like clojure 'let' special form
                                   :find   [?name]
                                   :where  [ [?eid :person/name ?name]
                                             [?eid :location    ?loc ] ] )
        busy      (try ; error - multiple results for London
                    (td/query-value  :let    [$    (live-db)
                                              ?loc "London"]
                                     :find   [?eid]
                                     :where  [ [?eid :person/name  ?name]
                                               [?eid :location     ?loc ] ] )
                    (catch Exception ex (.toString ex)))
        multi     (try ; error - tuple [?eid ?name] is not scalar
                    (td/query-value  :let    [$    (live-db)
                                              ?loc "Caribbean"]
                                     :find   [?eid ?name]
                                     :where  [ [?eid :person/name  ?name]
                                               [?eid :location     ?loc ] ] )
                    (catch Exception ex (.toString ex)))
  ]
    (is (= beachy "Dr No"))                       ; found 1 match as expected
    (is (re-find #"IllegalStateException" busy))   ; Exception thrown/caught since 2 people in London
    (is (re-find #"IllegalStateException" multi))) ; Exception thrown/caught since 2-vector is not scalar

  ; If you wish to retain duplicate results on output, you must use td/query-pull and the Datomic
  ; Pull API to return a list of results (instead of a set).
  (let [result-pull     (td/query-pull  :let    [$ (live-db)]                 ; $ is the implicit db name
                                        :find   [ (pull ?eid [:location]) ]   ; output :location for each ?eid found
                                        :where  [ [?eid :location] ] )        ; find any ?eid with a :location attr
        result-sort     (sort-by #(-> % only :location) result-pull)
  ]
    (s/validate [ts/TupleMap]   result-pull)  ; a list of tuples of maps
    (s/validate  ts/TupleMaps   result-pull)  ; a list of tuples of maps
    (is (= result-sort  [ [ {:location "Caribbean"} ] 
                          [ {:location "London"   } ]
                          [ {:location "London"   } ] ] )))
; #todo show Exception if non-pull

  ; Create a partition named :people (we could namespace it like :db.part/people if we wished)
  (td/transact *conn* 
    (td/new-partition :people ))

  ; Create Honey Rider and add her to the :people partition
  (let [tx-result   @(td/transact *conn* 
                        (td/new-entity :people ; <- partition is first arg (optional) to td/new-entity 
                          { :person/name "Honey Rider" :location "Caribbean" :weapon/type #{:weapon/knife} } ))
        [honey-eid]  (td/eids tx-result)          ; retrieve Honey Rider's EID from the seq (destructuring)
         honey-eid2  (only (td/eids tx-result))   ;   or use 'only' to unwrap it
  ]
    (s/validate ts/Eid honey-eid)  ; verify the expected type
    (is (= honey-eid honey-eid2))
    (is (= :people ; verify the partition name for Honey's EID
           (td/partition-name (live-db) honey-eid)))

    ; Show that only Honey is in the people partition
    (let [people-eids           (td/partition-eids (live-db) :people)
          people-entity-maps    (mapv  #(td/entity-map (live-db) %)  people-eids) ]
      (is (= (only people-entity-maps)
             {:person/name "Honey Rider", :weapon/type #{:weapon/knife}, :location "Caribbean"} ))))

; #todo verify that datomic/q returns TupleSets (i.e. no duplicate tuples in result)
)
