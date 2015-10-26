(ns karmalack.db
  (:require [clojure.java.jdbc :as sql]
            [yesql.core :refer [defqueries]]
            [clj-time.core :as t]
            [clj-time.local :as l]
            [clj-time.coerce :as c]
            [datomic.api :as d]))

(def uri "datomic:free://localhost:4334/karmalack")
(def conn (d/connect uri))

(defn find-user-by-id [id]
  (let [db (d/db conn)]
    (:db/id (d/entity db [:user/userid id]))))

(defn add-user [id]
  (let [r @(d/transact
             conn
             [{:db/id (d/tempid :db.part/user)
               :user/userid id
               :user/skill ""
               :user/banner ""}])]
    (-> r :tempids vals first)))

(defn- user-entity [userid]
  (if-let [u (find-user-by-id userid)]
    u
    (add-user userid)))

(defn- karma-delta [delta userid source]
  (let [id (user-entity userid)]
    @(d/transact
       conn
       [{:db/id        (d/tempid :db.part/user)
         :karma/user   id
         :karma/source source
         :karma/delta  delta
         :karma/ts     (java.util.Date.)}])))

(def karma-inc! (partial karma-delta 1))
(def karma-dec! (partial karma-delta -1))

(defn settings-save-skill! [userid skill]
  @(d/transact
     conn
     [{:db/id       (d/tempid :db.part/user)
       :user/userid userid
       :user/skill  skill}]))

(defn settings-save-banner! [userid banner]
  @(d/transact
     conn
     [{:db/id       (d/tempid :db.part/user)
       :user/userid  userid
       :user/banner  banner}]))

(defn karma-stats []
  (let [totals (into {}
                     (d/q '[:find ?userid (sum ?delta)
                            :with ?k
                            :where [?u :user/userid ?userid]
                            [?k :karma/user ?u]
                            [?k :karma/delta ?delta]]
                          (d/db conn)))]
    totals))

(defn all-user-settings []
  (->>
    (d/q
      '[:find ?userid ?skill ?banner
        :where
        [?u :user/userid ?userid]
        [?u :user/skill ?skill]
        [?u :user/banner ?banner]]
      (d/db conn))
    (map (fn [[id skill banner]]
           [id {:skill skill :banner banner}]))
    (into {})))


(defn setup-database! []
  (d/create-database uri)
  @(d/transact
     (d/connect uri)
     '[
       ;; User
       {:db/id                 #db/id[:db.part/db]
        :db/ident              :user/userid
        :db/valueType          :db.type/string
        :db/cardinality        :db.cardinality/one
        :db/unique             :db.unique/identity
        :db/doc                "The user's id"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :user/skill
        :db/valueType          :db.type/string
        :db/cardinality        :db.cardinality/one
        :db/doc                "The user's skill string"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :user/banner
        :db/valueType          :db.type/string
        :db/cardinality        :db.cardinality/one
        :db/doc                "The banner image URL for the user"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :user/karma
        :db/valueType          :db.type/ref
        :db/cardinality        :db.cardinality/many
        :db/doc                "User's karma"
        :db.install/_attribute :db.part/db}


       ;; Karma
       ;;
       {:db/id                 #db/id[:db.part/db]
        :db/ident              :karma/user
        :db/valueType          :db.type/ref
        :db/cardinality        :db.cardinality/one
        :db/doc                "The user's id"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :karma/source
        :db/valueType          :db.type/string
        :db/cardinality        :db.cardinality/one
        :db/doc                "The channel where this karma was awarded"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :karma/delta
        :db/valueType          :db.type/long
        :db/cardinality        :db.cardinality/one
        :db/doc                "The amount of karma awarded, positive or negative"
        :db.install/_attribute :db.part/db}

       {:db/id                 #db/id[:db.part/db]
        :db/ident              :karma/ts
        :db/valueType          :db.type/instant
        :db/cardinality        :db.cardinality/one
        :db/doc                "The timestamp when this karma was awarded"
        :db.install/_attribute :db.part/db}
       ]))

(setup-database!)

(defn cleanup! []
  (d/delete-database uri))

(defn reset-database! []
  (cleanup!)
  (setup-database!))
