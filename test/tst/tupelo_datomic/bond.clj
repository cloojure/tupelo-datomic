(ns tst.tupelo-datomic.bond
  (:use tupelo.core tupelo.test)
  (:require
    [clojure.pprint       :as pp]
    [datomic.api          :as d]
    [schema.core          :as s]
    [tupelo-datomic.core  :as td]
    [tupelo.schema        :as ts]
  ))

(def datomic-uri "datomic:mem://tst.bond") ; the URI for our test db
(def ^:dynamic *conn*) ; dynamic var to hold the db connection

;---------------------------------------------------------------------------------------------------
; clojure.test fixture: setup & teardown for each test
(use-fixtures :each
  (fn setup-execute-teardown ; perform setup, execution, & teardown for each test
    [tst-fn]
    ; setup ----------------------------------------------------------
    (d/create-database datomic-uri) ; create the DB
    (try
      (binding [*conn* (d/connect datomic-uri)] ; create & save a connection to the db
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
  (let [eids (onlies (td/find 
                       :let [$ db-val]
                       :find [?eid] ; <- could also use Datomic Pull API
                       :where {:db/id ?eid :person/name _}))]
    (set (for [eid eids]
           (td/entity-map db-val eid)))))

;---------------------------------------------------------------------------------------------------
; #todo
; demo to show changing an attribute (string -> integer, cardinality/one -> many, deleting an attribute
; example of retracting 1 item from a normal (cardinality/one) attribute & a cardinality/many attribute

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
  (is= (get-people (live-db))
         #{ {:person/name "James Bond"    :location "London"      :weapon/type #{:weapon/wit    :weapon/gun} }
            {:person/name "M"             :location "London"      :weapon/type #{:weapon/guile  :weapon/gun} }
            {:person/name "Dr No"         :location "Caribbean"   :weapon/type #{:weapon/gun               } } } )

  ; Using James' name, lookup his EntityId (EID). It is a java.lang.Long that is a unique ID across the whole DB.
  (let [james-eid   (only2 (td/find  :let [$ (live-db)] ; like Clojure let
                                     :find [?eid]
                                     :where {:db/id ?eid :person/name "James Bond"}))
        _ (s/validate ts/Eid james-eid)  ; verify the expected type
        ; Retrieve James' attr-val pairs as a map. An entity can be referenced either by EID or by a
        ; LookupRef, which is a unique attribute-value pair expressed as a vector.
        james-map   (td/entity-map (live-db) james-eid)                       ; lookup by EID
        james-map2  (td/entity-map (live-db) [:person/name "James Bond"] )    ; lookup by LookupRef
  ]
    (is= james-map james-map2
           {:person/name "James Bond" :location "London" :weapon/type #{:weapon/wit :weapon/gun} } )

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
        { :weapon/type #{ :weapon/gun :weapon/knife :weapon/guile } } ))

    ; Verify current status. Notice there are no duplicate weapons.
    (is= (get-people (live-db))
      #{ {:person/name "James Bond" :location "London"    :weapon/type #{              :weapon/wit :weapon/knife :weapon/gun} :person/secret-id 7 }
         {:person/name "M"          :location "London"    :weapon/type #{:weapon/guile                           :weapon/gun} }
         {:person/name "Dr No"      :location "Caribbean" :weapon/type #{:weapon/guile             :weapon/knife :weapon/gun} } } )

    ;-----------------------------------------------------------------------------
    ; Search for people that match both {:weapon/type :weapon/guile} and {:weapon/type :weapon/gun}
    (let [tuple-set   (td/find :let    [$ (live-db)]
                               :find   [?name]
                               :where  {:person/name ?name :weapon/type :weapon/guile }
                                       {:person/name ?name :weapon/type :weapon/gun } ) ]
      (is= #{["Dr No"] ["M"]} tuple-set ))

    ;-----------------------------------------------------------------------------
    ; Try to add non-existent weapon. This throws since the bogus kw does not match up with an entity.
    (is (thrown? Exception   @(td/transact *conn*
                                (td/update [:person/name "James Bond"] ; update using a LookupRef
                                  { :weapon/type #{ :there.is/no-such-kw } } ))))  ; bogus value for :weapon/type causes exception

    ; What if James throws his knife at a villan.  We must remove it from the db.
    (td/transact *conn*
      (td/retract-value [:person/name "James Bond"] :weapon/type :weapon/knife))
    (is= (td/entity-map (live-db) [:person/name "James Bond"]) ; LookupRef
          {:person/name "James Bond" :location "London" :weapon/type #{:weapon/wit :weapon/gun} :person/secret-id 7 } )

    ; Dr No is no match for James. He gives up trying to use guile...  Remove it using native Datomic.
    (td/entity-map (live-db) [:person/name "Dr No"])
    (d/transact *conn* [
       [:db/retract [:person/name "Dr No"] :weapon/type :weapon/guile]
    ] )
    (is= (td/entity-map (live-db) [:person/name "Dr No"]) ; LookupRef
          {:person/name "Dr No" :location "Caribbean" :weapon/type #{:weapon/knife :weapon/gun} } )

    ; James is on a secret mission, & no one knows where...
    (td/transact *conn*
      (td/retract-value james-eid :location "London")) ; We must know the current value to retract it
    (is= (td/entity-map (live-db) james-eid)  ; lookup by EID
           {:person/name "James Bond" :weapon/type #{:weapon/wit :weapon/gun} :person/secret-id 7 } )
    ; James turns up in the Caribbean
    (td/transact *conn*
      (td/update james-eid {:location "Caribbean"} ))  ; add a value where none exists
    (is= (td/entity-map (live-db) james-eid)  ; lookup by EID
           {:person/name "James Bond" :location "Caribbean" :weapon/type #{:weapon/wit :weapon/gun} :person/secret-id 7 } )
    ; James then returns to London
    (td/transact *conn*
      (td/update james-eid {:location "London"} ))  ; overwrite an existing value (implicitly retracts old value, then adds new value)
    (is= (td/entity-map (live-db) james-eid)  ; lookup by EID
           {:person/name "James Bond" :location "London" :weapon/type #{:weapon/wit :weapon/gun} :person/secret-id 7 } )

    ; (td/entity-map-full ...) includes the EID in the result under the native Datomic keyword :db/id
    (is= (td/entity-map-full (live-db) james-eid)  ; lookup by EID
           {:person/name "James Bond" :location "London" :weapon/type #{:weapon/wit :weapon/gun} :person/secret-id 7
            :db/id james-eid } )
  )

  ; For general queries, use td/find.  It returns a set of tuples (a TupleSet).  Duplicate
  ; tuples in the result will be discarded.
  (let [tuple-set   (td/find   :let    [$ (live-db)]
                               :find   [?name ?loc] ; <- shape of output tuples
                               :where  {:person/name ?name :location ?loc} )
  ]
    (s/validate  ts/TupleSet  tuple-set)       ; verify expected type using Prismatic Schema
    (s/validate #{ [s/Any] }  tuple-set)       ; literal definition of TupleSet
    (is= tuple-set #{ ["Dr No"       "Caribbean"]      ; Even though London is repeated, each tuple is
                        ["James Bond"  "London"]         ; still unique. Otherwise, any duplicate tuples
                        ["M"           "London"] } ))   ; will be discarded since output is a clojure set.

  ; If you want just a single attribute as output, you can get a set of attributes (rather than a set of
  ; tuples) use (onlies ...).  As usual, any duplicate values will be discarded. It is an error if
  ; more than one attribute is present in the :find clause.
  (let [names     (onlies (td/find :let [$ (live-db)]
                            :find [?name] ; <- a single attr-val output
                            :where {:person/name ?name}))
        cities    (onlies (td/find :let [$ (live-db)]
                            :find [?loc] ; <- a single attr-val output
                            :where {:location ?loc}))
        ]
    (is= names    #{"Dr No" "James Bond" "M"} )  ; all names are present, since unique
    (is= cities   #{"Caribbean" "London"} ))     ; duplicate "London" discarded

  ; If you want just a single tuple as output, use (only ...)
  (let [beachy    (only (td/find :let [$ (live-db) ; assign multiple find variables
                                       ?loc "Caribbean"] ;   just like clojure 'let' special form
                          :find [?eid ?name] ; <- output tuple shape
                          :where {:db/id ?eid :person/name ?name :location ?loc}))
        busy      (try ; error - both James & M are in London
                    (only (td/find :let [$ (live-db)
                                         ?loc "London"]
                            :find [?eid ?name] ; <- output tuple shape
                            :where {:db/id ?eid :person/name ?name :location ?loc}))
                    (catch Exception ex (.toString ex)))
  ]
    (is (wild-match? [:* "Dr No"] beachy ))           ; found 1 match as expected
    (is (re-find #"Exception" busy)))  ; Exception thrown/caught since 2 people in London

  ; If you know there is (or should be) only a single scalar answer, use (only2 ...)
  (let [beachy    (only2 (td/find :let [$ (live-db) ; assign multiple find variables
                                        ?loc "Caribbean"] ; just like clojure 'let' special form
                           :find [?name]
                           :where {:person/name ?name :location ?loc}))
        busy      (try ; error - multiple results for London
                    (only2 (td/find :let [$ (live-db)
                                          ?loc "London"]
                             :find [?eid]
                             :where {:db/id ?eid :person/name ?name :location ?loc}))
                    (catch Exception ex (.toString ex)))
        multi     (try ; error - tuple [?eid ?name] is not scalar
                    (only2 (td/find :let [$ (live-db)
                                          ?loc "Caribbean"]
                             :find [?eid ?name]
                             :where {:db/id ?eid :person/name ?name :location ?loc}))
                    (catch Exception ex (.toString ex)))
  ]
    (is= beachy "Dr No")               ; found 1 match as expected
    (is (re-find #"Exception" busy))   ; Exception thrown/caught since 2 people in London
    (is (re-find #"Exception" multi))) ; Exception thrown/caught since 2-vector is not scalar

;-----------------------------------------------------------------------------
; #todo make ?*   a synonym for _ wildcard (?)
; #todo make :eid a synonym for #db/id key (?)

  ; If you wish to retain duplicate results on output, you must use td/find-pull and the Datomic
  ; Pull API to return a list of results (instead of a set).
  (let [result-pull     (td/find-pull   :let    [$ (live-db)]                 ; $ is the implicit db name
                                        :find   [ (pull ?eid [:location]) ]   ; output :location for each ?eid found
                                        :where  { :db/id ?eid :location _ } )        ; find any ?eid with a :location attr
        result-sort     (sort-by #(-> % only :location) result-pull)
  ]
    (s/validate [ts/TupleMap]   result-pull)  ; a list of tuples of maps
    (s/validate  ts/TupleMaps   result-pull)  ; a list of tuples of maps
    (is= result-sort [[{:location "Caribbean"}]
                      [{:location "London"}]
                      [{:location "London"}]]))
; #todo show Exception if non-pull

; #todo Add example linking entities (& README)
; #todo Add example showing history (eg find EIDs of last tx)

; #todo
;;  ; If you wish to retain duplicate results on output, you must use td/query-pull and the Datomic
;;  ; Pull API to return a list of results (instead of a set).
;;  (let [result-pull     (td/query-pull  :let    [$ (live-db)]                 ; $ is the implicit db name
;;                                        :find   [ (pull ?eid [:location]) ]   ; output :location for each ?eid found
;;                                        :where  [ [?eid :location] ] )        ; find any ?eid with a :location attr
;;        result-sort     (sort-by #(-> % only :location) result-pull)
;;  ]
;;    (s/validate [ts/TupleMap]   result-pull)  ; a list of tuples of maps
;;    (s/validate  ts/TupleMaps   result-pull)  ; a list of tuples of maps
;;    (is (= result-sort  [ [ {:location "Caribbean"} ]
;;                          [ {:location "London"   } ]
;;                          [ {:location "London"   } ] ] )))
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
        tx-datoms   (td/tx-datoms (live-db) tx-result)
  ]
    (s/validate ts/Eid honey-eid)  ; verify the expected type
    (is= honey-eid honey-eid2)
    (is= :people ; verify the partition name for Honey's EID
           (td/partition-name (live-db) honey-eid))

    ; Show that only Honey is in the people partition
    (let [people-eids           (td/partition-eids (live-db) :people)
          people-entity-maps    (mapv  #(td/entity-map (live-db) %)  people-eids) ]
      (is= (only people-entity-maps)
             {:person/name "Honey Rider", :weapon/type #{:weapon/knife}, :location "Caribbean"} ))
    ; tx-datoms looks like:   ;;; (print "Honey tx-datoms =") (pp/pprint tx-datoms)
    ;    [ {:e 13194139534328,
    ;       :a :db/txInstant,
    ;       :v #inst "2016-10-02T21:45:44.689-00:00",
    ;       :tx 13194139534328,
    ;       :added true}
    ;      {:e 299067162756089,
    ;       :a :person/name,
    ;       :v "Honey Rider",
    ;       :tx 13194139534328,
    ;       :added true}
    ;      {:e 299067162756089,
    ;       :a :location,
    ;       :v "Caribbean",
    ;       :tx 13194139534328,
    ;       :added true}
    ;      {:e 299067162756089,
    ;       :a :weapon/type,
    ;       :v 17592186045419,
    ;       :tx 13194139534328,
    ;       :added true} ]
    (is= "Honey Rider" (:v (only (keep-if #(= :person/name  (:a %)) tx-datoms))))
    (is= "Caribbean"   (:v (only (keep-if #(= :location     (:a %)) tx-datoms))))
    (is= 1                (count (keep-if #(= :weapon/type  (:a %)) tx-datoms)))
    (is= 1                (count (keep-if #(= :db/txInstant (:a %)) tx-datoms)))
    (is (apply = (map :tx tx-datoms)))  ; All datoms have the same :tx value
  )

  ; Once James has defeated Dr No, we need to remove him (& everything he possesses) from the database.
  ; We see that Dr No is in the DB...
  (let [tuple-set   (td/find  :let    [$ (live-db)]
                              :find   [?name ?loc] ; <- shape of output tuples
                              :where  {:person/name ?name :location ?loc} ) ]
    (is= tuple-set #{["James Bond" "London"]
                     ["M" "London"]
                     ["Dr No" "Caribbean"]
                     ["Honey Rider" "Caribbean"]}))
  ; we do the retraction...
  (td/transact *conn*
    (td/retract-entity [:person/name "Dr No"] ))
  ; ...and now he's gone!
  (let [tuple-set   (td/find  :let    [$ (live-db)]
                              :find   [?name ?loc]
                              :where  {:person/name ?name :location ?loc} ) ]
    (is= tuple-set #{["James Bond" "London"]
                     ["M" "London"]
                     ["Honey Rider" "Caribbean"]}))

  (defn get-bond-girl-names []
    (let [result-pull     (d/pull (live-db) [:bond-girl] [:person/name "James Bond"])
          bond-girl-names (forv [girl-entity (grab :bond-girl result-pull) ]
                               (grab :person/name (td/entity-map (live-db) (grab :db/id girl-entity))))
          ]

      bond-girl-names))

  (td/transact *conn*
    (td/new-attribute :bond-girl :db.type/ref :db.cardinality/many)  ; there are many Bond girls
    (td/new-attribute :best-friend :db.type/ref)  ; one can have many friends

)
  ; #todo modify to use tempIds (string or negative int)
  (let [tx-result          @(td/transact *conn*
                              (td/new-entity {:db/id "user" :person/name "Sylvia Trench" :best-friend "tr"})
                              (td/new-entity {:db/id "tr" :person/name "Tatiana Romanova" :best-friend "user"})
                              (td/new-entity {:db/id "pg" :person/name "Pussy Galore" :best-friend "bd"})
                              (td/new-entity {:db/id "bd" :person/name "Bibi Dahl" :best-friend "op"})
                              (td/new-entity {:db/id "op" :person/name "Octopussy" :best-friend "user"})
                              (td/new-entity {:db/id "pc" :person/name "Paris Carver" :best-friend "cj"})
                              (td/new-entity {:db/id "cj" :person/name "Christmas Jones" :best-friend "pc"}))
        tx-datoms          (td/tx-datoms (live-db) tx-result)
        girl-datoms        (vec (remove #(= :db/txInstant (grab :a %)) tx-datoms))
        girl-eids          (mapv :e girl-datoms)
        txr-2              (td/transact *conn*
                             (td/update [:person/name "James Bond"] ; update using a LookupRef
                               {:bond-girl girl-eids})
                             (td/update [:person/name "James Bond"] ; don't forget to add Honey Rider!
                               {:bond-girl #{[:person/name "Honey Rider"]}}))
  ]
    (is= (get-bond-girl-names)
          ["Sylvia Trench" "Tatiana Romanova" "Pussy Galore" "Bibi Dahl"
           "Octopussy" "Paris Carver" "Christmas Jones" "Honey Rider"])

    ; Suppose Bibi Dahl is just not refined enough for James. Give her a demotion.
    (td/transact *conn*
      (td/retract-value [:person/name "James Bond"] :bond-girl [:person/name "Bibi Dahl"]))
    (is (= (get-bond-girl-names) ; Note that Bibi Dahl is no longer listed
          ["Sylvia Trench" "Tatiana Romanova" "Pussy Galore"
           "Octopussy" "Paris Carver" "Christmas Jones" "Honey Rider"] ))

    (let [bibi-eid (only (only (td/find :let [$ (live-db)]
                                 :find [?eid]
                                 :where {:db/id ?eid :person/name "Bibi Dahl"})))

          bibi     (td/entity-map (live-db) bibi-eid)
          bf-eid   (fetch-in bibi [:best-friend :db/id])
          bf       (td/entity-map (live-db) bf-eid)]
      (is= "Bibi Dahl" (grab :person/name bibi))
      (is= "Octopussy" (grab :person/name bf))))

  ; #todo verify that datomic/q returns TupleSets (i.e. no duplicate tuples in result)
  )

