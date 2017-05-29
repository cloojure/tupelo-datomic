(ns tst.tupelo-datomic.core
  (:use clojure.test tupelo.test)
  (:require [datomic.api          :as d]
            [tupelo-datomic.core  :as td]
            [tupelo.schema        :as ts]
            [tupelo.core          :as t]
            [schema.core          :as s]))
(t/refer-tupelo)

(def datomic-uri "datomic:mem://tupelo")          ; the URI for our test db
(def ^:dynamic *conn*)

;---------------------------------------------------------------------------------------------------
; clojure.test fixture: setup & teardown for each test
(use-fixtures :each
  (fn setup-execute-teardown            ; perform setup, execution, & teardown for each test
    [tst-fn]
; setup ----------------------------------------------------------
    (d/create-database datomic-uri)             ; create the DB
    (binding [*conn* (d/connect datomic-uri) ]  ; create & save a connection to the db
; execute --------------------------------------------------------
      (try
        (tst-fn)
; teardown -------------------------------------------------------
        (finally
          (d/delete-database datomic-uri))))))

;---------------------------------------------------------------------------------------------------

(deftest t-new-partition
  (is (wild-match? {:db/id                 :*
                    :db.install/_partition :db.part/db
                    :db/ident              :people}
        (td/new-partition :people)))
  (is (wild-match? {:db/id                 :*
                    :db.install/_partition :db.part/db
                    :db/ident              :part.with.ns}
        (td/new-partition :part.with.ns)))
  (is (wild-match? {:db/id                 :*
                    :db.install/_partition :db.part/db
                    :db/ident              :some-ns/some-part}
        (td/new-partition :some-ns/some-part))))

; #todo add more testing; verify each option alone pos/neg
(dotest
  (let [result (td/new-attribute
                 :weapon/type :db.type/keyword
                 :db.unique/value :db.unique/identity :db.cardinality/one :db.cardinality/many
                 :db/index :db/fulltext :db/isComponent :db/noHistory)]
    (is (s/validate datomic.db.DbId (:db/id result)))
    (is (wild-match? {:db/id          :* :db/ident :weapon/type
                      :db/index       true :db/unique :db.unique/identity
                      :db/noHistory   true :db/cardinality :db.cardinality/many
                      :db/isComponent true :db.install/_attribute :db.part/db
                      :db/fulltext    true :db/valueType :db.type/keyword}
          result)))
  (is (wild-match? {:db/id          :* :db.install/_attribute :db.part/db
                    :db/ident       :name
                    :db/valueType   :db.type/string
                    :db/index       true
                    :db/cardinality :db.cardinality/one}
        (td/new-attribute :name :db.type/string)))

  (is (wild-match? {:db/id          :* :db.install/_attribute :db.part/db
                    :db/ident       :name
                    :db/valueType   :db.type/string
                    :db/index       false
                    :db/cardinality :db.cardinality/one}
        (td/new-attribute :name :db.type/string :db/noindex)))

  (let [result (td/new-attribute :weapon/type :db.type/keyword
                 :db.unique/identity :db.unique/value
                 :db.cardinality/many :db.cardinality/one
                 :db/index :db/fulltext :db/isComponent :db/noHistory)]
    (is (wild-match? {:db/id          :* :db/ident :weapon/type
                      :db/index       true :db/unique :db.unique/value
                      :db/noHistory   true :db/cardinality :db.cardinality/one
                      :db/isComponent true :db.install/_attribute :db.part/db
                      :db/fulltext    true :db/valueType :db.type/keyword}
          result))))

(dotest
  (is (thrown? Exception (td/new-attribute :some-attr :db.type/bogus)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/string
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/string)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/keyword
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/keyword)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/boolean
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/boolean)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/long
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/long)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/bigint
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/bigint)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/float
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/float)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/double
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/double)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/bigdec
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/bigdec)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/bytes
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/bytes)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/instant
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/instant)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/uuid
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/uuid)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/uri
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/uri)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/ref
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/ref))))

(dotest
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/string
                  :db/cardinality :db.cardinality/one}
        (td/new-attribute :location :db.type/string)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/string
                  :db/cardinality :db.cardinality/many}
        (td/new-attribute :location :db.type/string :db.cardinality/many)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/string
                  :db/cardinality :db.cardinality/one
                  :db/unique      :db.unique/value}
        (td/new-attribute :location :db.type/string :db.unique/value)))
  (is (submatch? {:db/ident       :location
                  :db/valueType   :db.type/string
                  :db/cardinality :db.cardinality/one
                  :db/unique      :db.unique/identity}
        (td/new-attribute :location :db.type/string :db.unique/identity))))

(dotest
  ; "new-entity"
  (is (submatch? {:person/name "dilbert" :job/type :job.type/sucky}
        (td/new-entity {:person/name "dilbert" :job/type :job.type/sucky})))

  ; "new-entity with partition"
  (let [result (td/new-entity :dummy.part/name {:person/name "dilbert" :job/type :job.type/sucky})
        dbid   (grab :db/id result)
        part1  (first dbid)
        part2  (second dbid)]
    ; result: {:db/id #db/id[:dummy.part/name -1000003] :person/name "dilbert" :job/type :job.type/sucky}
    (is (wild-match? {:db/id       {:part :dummy.part/name, :idx :*}
                      :person/name "dilbert" :job/type :job.type/sucky} result))
    (is (wild-match? #db/id[:dummy.part/name :*] dbid)) ; #db/id[:dummy.part/name -1000003]
    (is (= [:part :dummy.part/name] part1))
    (is (wild-match? [:idx :*] part2))
    (is (s/validate ts/Eid (second part2)))))

(deftest t-new-enum
  (is (submatch? {:db/ident :weapon.type/gun} (td/new-enum :weapon.type/gun)))
  (is (submatch? {:db/ident :gun} (td/new-enum :gun)))
  (is (thrown? Exception (td/new-enum "gun"))))

; #todo: need more tests for query-*, etc

(deftest t-update
  (is= {:db/id 999 :person/name "joe" :car :car.type/bmw}
        (td/update 999 {:person/name "joe" :car :car.type/bmw}))
  (is= {:db/id [:person/name "joe"] :car :car.type/bmw}
        (td/update [:person/name "joe"] {:car :car.type/bmw})))

(deftest t-retract-value
  (is= [:db/retract 999 :car :car.type/bmw]
        (td/retract-value 999 :car :car.type/bmw))
  (is= [:db/retract [:person/name "joe"] :car :car.type/bmw]
        (td/retract-value [:person/name "joe"] :car :car.type/bmw)))

(deftest t-retract-entity
  (is= [:db.fn/retractEntity 999] (td/retract-entity 999))
  (is= [:db.fn/retractEntity [:person/name "joe"]]
        (td/retract-entity [:person/name "joe"])))

; The macro test must be in the same source file as the macro definition or it won't expand properly
(deftest t-macro
  (is (td/t-query)))

(deftest t-contains-pull?
  (let [proxy-contains-pull? #'td/contains-pull? ] ; trick to get around private var
    (is       (proxy-contains-pull? [:find '[xx (pull [*]) ?y ]] ))
    (is (not  (proxy-contains-pull? [:find '[xx            ?y ]] )))))

